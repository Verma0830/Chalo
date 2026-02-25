// ============================================================
// Chalo Backend — Server Entry Point
// Handles startup, graceful shutdown, and uncaught errors
// ============================================================

import http from 'http';
import type { Socket } from 'net';
import config from './config';
import logger from './config/logger';
import { initializeFirebase } from './config/firebase';
import { prisma, disconnectDatabase } from './config/database';
import { createApp } from './app';
import { initRateLimitRedis, disconnectRateLimitRedis } from './middleware/rateLimiter';
import { initIdempotencyRedis, disconnectIdempotencyRedis } from './middleware/idempotency';
import { authService } from './services/auth.service';

// Track active connections for graceful shutdown
const connections: Set<Socket> = new Set();
let isShuttingDown = false;

async function startServer(): Promise<void> {
  try {
    // 1. Initialize Firebase Admin
    initializeFirebase();
    logger.info('Firebase Admin SDK initialized');

    // 1b. Production startup guards — fail fast on missing secrets
    if (config.isProd) {
      if (!config.razorpay.webhookSecret) {
        throw new Error('RAZORPAY_WEBHOOK_SECRET must be set in production');
      }
      if (config.razorpay.keySecret === 'placeholder_secret') {
        throw new Error('RAZORPAY_KEY_SECRET contains placeholder value — set real secret in production');
      }
      if (config.googleMaps.apiKey === 'placeholder_key') {
        throw new Error('GOOGLE_MAPS_API_KEY contains placeholder value — set real key in production');
      }
      if (!config.internalApiKey) {
        logger.warn('INTERNAL_API_KEY not set — /metrics endpoint will be unprotected');
      }
    }

    // 2. Verify database connection
    await prisma.$connect();
    logger.info('Database connected successfully');

    // 3. Initialize Redis connections
    await initRateLimitRedis();
    await initIdempotencyRedis();

    // 4. Create Express app
    const app = createApp();

    // 5. Create HTTP server (for connection tracking)
    const server = http.createServer(app);

    // Track connections for graceful shutdown
    server.on('connection', (conn) => {
      connections.add(conn);
      conn.on('close', () => connections.delete(conn));
    });

    // 6. Start HTTP server
    server.listen(config.port, () => {
      logger.info(`
  ╔═══════════════════════════════════════════════╗
  ║   Chalo Backend API Server                    ║
  ║   Environment: ${config.env.padEnd(30)}║
  ║   Port:        ${String(config.port).padEnd(30)}║
  ║   API:         /api/${config.apiVersion.padEnd(26)}║
  ║   Health:      /health                        ║
  ║   Metrics:     /metrics                       ║
  ╚═══════════════════════════════════════════════╝
      `);
    });

    // 7. Schedule background maintenance tasks
    //    Run OTP cleanup immediately on startup, then once every 24 hours.
    //    This removes expired/used OTP rows so the table stays lean.
    const OTP_CLEANUP_INTERVAL_MS = 24 * 60 * 60 * 1000; // 24 h
    authService.cleanupExpiredOTPs().catch((err) =>
      logger.error('Initial OTP cleanup failed', { err })
    );
    setInterval(() => {
      authService.cleanupExpiredOTPs().catch((err) =>
        logger.error('Scheduled OTP cleanup failed', { err })
      );
    }, OTP_CLEANUP_INTERVAL_MS);
    logger.info(`OTP cleanup scheduled every ${OTP_CLEANUP_INTERVAL_MS / 3600000}h`);

    // 8. Graceful shutdown handlers
    const shutdown = async (signal: string) => {
      if (isShuttingDown) {
        logger.warn('Shutdown already in progress');
        return;
      }
      isShuttingDown = true;

      logger.info(`${signal} received — shutting down gracefully...`);

      // Stop accepting new connections
      server.close(async () => {
        logger.info('HTTP server closed — no new connections');

        try {
          // Disconnect Redis
          await disconnectRateLimitRedis();
          await disconnectIdempotencyRedis();
          logger.info('Redis connections closed');

          // Disconnect database
          await disconnectDatabase();
          logger.info('Database disconnected');

          logger.info('Graceful shutdown complete');
          process.exit(0);
        } catch (error) {
          logger.error('Error during shutdown', { error });
          process.exit(1);
        }
      });

      // Give existing connections time to finish
      setTimeout(() => {
        logger.warn('Closing remaining connections forcefully');
        connections.forEach((conn) => conn.destroy());
      }, 15000);

      // Force kill after 30 seconds
      setTimeout(() => {
        logger.error('Forceful shutdown — could not close connections in time');
        process.exit(1);
      }, 30000);
    };

    process.on('SIGTERM', () => shutdown('SIGTERM'));
    process.on('SIGINT', () => shutdown('SIGINT'));

  } catch (error) {
    logger.error('Failed to start server', { error });
    process.exit(1);
  }
}

// -------------------------------------------------------
// Global Error Handlers — catch absolutely everything
// -------------------------------------------------------

process.on('uncaughtException', (error: Error) => {
  logger.error('UNCAUGHT EXCEPTION — shutting down', { error: error.message, stack: error.stack });
  process.exit(1);
});

process.on('unhandledRejection', (reason: unknown) => {
  logger.error('UNHANDLED REJECTION — triggering shutdown', { reason });
  // Throw to trigger uncaughtException handler which has the same error path
  throw reason;
});

// Start!
startServer();
