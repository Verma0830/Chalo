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
import { connectRedis, disconnectRedis } from './config/redis';
import { createApp } from './app';
import { initJobQueue, closeJobQueue } from './jobs/queue';
import { initTelemetry, shutdownTelemetry } from './telemetry';

// Track active connections for graceful shutdown
const connections: Set<Socket> = new Set();
let isShuttingDown = false;

async function startServer(): Promise<void> {
  try {
    // 0. Initialize OpenTelemetry if enabled
    await initTelemetry();

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
      if (config.redisUrl === 'redis://localhost:6379') {
        throw new Error('REDIS_URL must be explicitly configured in production');
      }
    }

    // 2. Verify database connection
    await prisma.$connect();
    logger.info('Database connected successfully');

    // 3. Initialize shared Redis connection
    await connectRedis();
    logger.info('Redis connected');

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

    // 7. Initialize BullMQ job queue (OTP cleanup, etc.)
    //    Replaces setInterval — safe for multi-instance deployments
    await initJobQueue();
    logger.info('Background job queue initialized');

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
          // Close job queue
          await closeJobQueue();
          logger.info('Job queue closed');

          // Disconnect Redis
          await disconnectRedis();
          logger.info('Redis disconnected');

          // Disconnect database
          await disconnectDatabase();
          logger.info('Database disconnected');

          // Shutdown telemetry exporter flush
          await shutdownTelemetry();
          logger.info('Telemetry shutdown complete');

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
