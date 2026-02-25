// ============================================================
// Chalo Backend — Server Entry Point
// Handles startup, graceful shutdown, and uncaught errors
// ============================================================

import config from './config';
import logger from './config/logger';
import { initializeFirebase } from './config/firebase';
import { prisma, disconnectDatabase } from './config/database';
import { createApp } from './app';

async function startServer(): Promise<void> {
  try {
    // 1. Initialize Firebase Admin
    initializeFirebase();
    logger.info('Firebase Admin SDK initialized');

    // 2. Verify database connection
    await prisma.$connect();
    logger.info('Database connected successfully');

    // 3. Create Express app
    const app = createApp();

    // 4. Start HTTP server
    const server = app.listen(config.port, () => {
      logger.info(`
  ╔═══════════════════════════════════════════════╗
  ║   Chalo Backend API Server                    ║
  ║   Environment: ${config.env.padEnd(30)}║
  ║   Port:        ${String(config.port).padEnd(30)}║
  ║   API:         /api/${config.apiVersion.padEnd(26)}║
  ║   Health:      /health                        ║
  ╚═══════════════════════════════════════════════╝
      `);
    });

    // 5. Graceful shutdown handlers
    const shutdown = async (signal: string) => {
      logger.info(`${signal} received — shutting down gracefully...`);

      // Stop accepting new connections
      server.close(async () => {
        logger.info('HTTP server closed');

        // Disconnect database
        await disconnectDatabase();

        logger.info('Graceful shutdown complete');
        process.exit(0);
      });

      // Force kill after 10 seconds
      setTimeout(() => {
        logger.error('Forceful shutdown — could not close connections in time');
        process.exit(1);
      }, 10000);
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
  logger.error('UNHANDLED REJECTION — shutting down', { reason });
  process.exit(1);
});

// Start!
startServer();
