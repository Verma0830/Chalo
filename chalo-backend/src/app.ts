/* ============================================================
// Chalo Backend — Express App Setup
// All middleware, routes, and error handling wired here
// ============================================================ */

import express, { Application } from 'express';
import cors from 'cors';
import helmet from 'helmet';
import compression from 'compression';
import morgan from 'morgan';
import hpp from 'hpp';

import config from './config';
import { morganStream } from './config/logger';
import { globalErrorHandler, notFoundHandler } from './middleware/errorHandler';
import { createRateLimiter } from './middleware/rateLimiter';
import routes from './routes';

export function createApp(): Application {
  const app = express();

  // -------------------------------------------------------
  // Security Middleware
  // -------------------------------------------------------

  // Helmet: sets various HTTP security headers
  app.use(helmet());

  // CORS: allow configured origins
  app.use(cors({
    origin: config.allowedOrigins,
    methods: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE'],
    allowedHeaders: ['Content-Type', 'Authorization'],
    credentials: true,
    maxAge: 86400, // 24 hours preflight cache
  }));

  // HPP: protect against HTTP Parameter Pollution
  app.use(hpp());

  // -------------------------------------------------------
  // Body Parsing & Compression
  // -------------------------------------------------------

  app.use(express.json({ limit: '10mb' }));
  app.use(express.urlencoded({ extended: true, limit: '10mb' }));
  app.use(compression());

  // -------------------------------------------------------
  // Request Logging
  // -------------------------------------------------------

  if (config.isDev) {
    app.use(morgan('dev'));
  } else {
    app.use(morgan('combined', { stream: morganStream }));
  }

  // -------------------------------------------------------
  // Rate Limiting
  // -------------------------------------------------------

  // Global rate limit: 100 requests per 15 minutes per IP
  app.use(createRateLimiter({ windowMs: 15 * 60 * 1000, max: 100 }));

  // -------------------------------------------------------
  // Health Check (no auth required)
  // -------------------------------------------------------

  app.get('/health', (_req, res) => {
    res.status(200).json({
      status: 'ok',
      service: 'chalo-backend',
      version: config.apiVersion,
      timestamp: new Date().toISOString(),
      uptime: process.uptime(),
    });
  });

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

export default createApp;
