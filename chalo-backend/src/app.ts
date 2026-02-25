/* ============================================================
// Chalo Backend — Express App Setup
// All middleware, routes, and error handling wired here
// ============================================================ */

import express, { Application, Request, Response, NextFunction } from 'express';
import cors from 'cors';
import helmet from 'helmet';
import compression from 'compression';
import morgan from 'morgan';
import hpp from 'hpp';

import config from './config';
import { morganStream } from './config/logger';
import { globalErrorHandler, notFoundHandler } from './middleware/errorHandler';
import { createRateLimiter } from './middleware/rateLimiter';
import { requestIdMiddleware, getRequestId } from './middleware/requestId';
import { metricsHandler, httpRequestDuration, httpRequestTotal } from './utils/metrics';
import { prisma } from './config/database';
import routes from './routes';

export interface AppOptions {
  enableHttpsRedirect?: boolean;
  enableRateLimit?: boolean;
  enableMetrics?: boolean;
}

const defaultOptions: Required<AppOptions> = {
  enableHttpsRedirect: true,
  enableRateLimit: true,
  enableMetrics: true,
};

export function createApp(options: AppOptions = {}): Application {
  const resolvedOptions = { ...defaultOptions, ...options };
  const app = express();

  // -------------------------------------------------------
  // Trust Proxy (for correct req.ip behind reverse proxy)
  // -------------------------------------------------------
  if (config.isProd) {
    app.set('trust proxy', 1);
  }

  // -------------------------------------------------------
  // Request ID Middleware (first — for tracing all requests)
  // -------------------------------------------------------
  app.use(requestIdMiddleware);

  // -------------------------------------------------------
  // HTTPS Enforcement (production only)
  // -------------------------------------------------------
  if (resolvedOptions.enableHttpsRedirect && config.isProd) {
    app.use((req: Request, res: Response, next: NextFunction) => {
      // Trust proxy headers from load balancer
      if (req.headers['x-forwarded-proto'] !== 'https') {
        return res.redirect(301, `https://${req.headers.host}${req.url}`);
      }
      next();
    });
  }

  // -------------------------------------------------------
  // Security Middleware
  // -------------------------------------------------------

  // Helmet: sets various HTTP security headers
  app.use(helmet());

  // CORS: allow configured origins
  app.use(cors({
    origin: config.allowedOrigins,
    methods: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE'],
    allowedHeaders: ['Content-Type', 'Authorization', 'Idempotency-Key', 'X-Request-Id'],
    credentials: true,
    maxAge: 86400, // 24 hours preflight cache
  }));

  // HPP: protect against HTTP Parameter Pollution
  app.use(hpp());

  // -------------------------------------------------------
  // Body Parsing & Compression
  // Default limit is 100kb — critical endpoints override with smaller limits
  // -------------------------------------------------------

  app.use(express.json({ limit: '100kb' }));
  app.use(express.urlencoded({ extended: true, limit: '100kb' }));
  app.use(compression());

  // -------------------------------------------------------
  // Request Logging with Request ID
  // -------------------------------------------------------

  // Custom morgan token for request ID
  morgan.token('request-id', (req: Request) => getRequestId(req));

  if (config.isDev) {
    app.use(morgan(':method :url :status :response-time ms - :request-id'));
  } else {
    app.use(morgan(':remote-addr - :remote-user [:date[clf]] ":method :url HTTP/:http-version" :status :res[content-length] ":referrer" ":user-agent" :request-id :response-time ms', { stream: morganStream }));
  }

  // -------------------------------------------------------
  // Metrics Middleware (track request duration)
  // -------------------------------------------------------

  if (resolvedOptions.enableMetrics) {
    app.use((req: Request, res: Response, next: NextFunction) => {
      const start = process.hrtime.bigint();

      res.on('finish', () => {
        const duration = Number(process.hrtime.bigint() - start) / 1e9;
        const path = req.route?.path || req.path;
        const labels = {
          method: req.method,
          path: path.length > 50 ? path.substring(0, 50) : path,
          status_code: String(res.statusCode),
        };

        httpRequestDuration.observe(labels, duration);
        httpRequestTotal.inc(labels);
      });

      next();
    });
  }

  // -------------------------------------------------------
  // Rate Limiting
  // -------------------------------------------------------

  // Global rate limit: 100 requests per 15 minutes per IP
  if (resolvedOptions.enableRateLimit) {
    app.use(createRateLimiter({ windowMs: 15 * 60 * 1000, max: 100, prefix: 'rl:global' }));
  }

  // -------------------------------------------------------
  // Health Check (no auth required)
  // Checks database and returns detailed status
  // -------------------------------------------------------

  app.get('/health', async (_req: Request, res: Response) => {
    const healthCheck = {
      status: 'ok' as 'ok' | 'degraded' | 'unhealthy',
      service: 'chalo-backend',
      version: config.apiVersion,
      timestamp: new Date().toISOString(),
      uptime: process.uptime(),
      checks: {
        database: 'unknown' as 'ok' | 'down' | 'unknown',
      },
    };

    try {
      // Check database connectivity
      await prisma.$queryRaw`SELECT 1`;
      healthCheck.checks.database = 'ok';
    } catch {
      healthCheck.checks.database = 'down';
      healthCheck.status = 'degraded';
    }

    const statusCode = healthCheck.status === 'ok' ? 200 : 503;
    res.status(statusCode).json(healthCheck);
  });

  // -------------------------------------------------------
  // Prometheus Metrics Endpoint
  // -------------------------------------------------------

  if (resolvedOptions.enableMetrics) {
    app.get('/metrics', (req: Request, res: Response, next: NextFunction): void => {
      // Protect metrics in production with API key
      if (config.isProd && config.internalApiKey) {
        const apiKey = req.headers['x-api-key'] || req.query.key;
        if (apiKey !== config.internalApiKey) {
          res.status(403).json({ success: false, message: 'Forbidden' });
          return;
        }
      }
      next();
    }, metricsHandler);
  }

  // -------------------------------------------------------
  // API Routes
  // -------------------------------------------------------

  app.use(`/api/${config.apiVersion}`, routes);

  // -------------------------------------------------------
  // Error Handling (must be last)
  // -------------------------------------------------------

  // 404 handler — catch all unmatched routes
  app.use(notFoundHandler);

  // Global error handler — catches all thrown/next(err) errors
  app.use(globalErrorHandler);

  return app;
}

export function createTestApp(): Application {
  return createApp({
    enableHttpsRedirect: false,
    enableRateLimit: false,
    enableMetrics: false,
  });
}

export default createApp;
