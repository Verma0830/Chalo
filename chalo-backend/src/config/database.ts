// ============================================================
// Chalo Backend — Prisma Database Client
// Singleton pattern — prevents multiple instances in dev
// ============================================================

import { PrismaClient } from '@prisma/client';
import logger from './logger';

declare global {
  // eslint-disable-next-line no-var
  var __prisma: PrismaClient | undefined;
}

function createPrismaClient(): PrismaClient {
  const prisma = new PrismaClient({
    log: [
      { emit: 'event', level: 'query' },
      { emit: 'event', level: 'error' },
      { emit: 'event', level: 'warn' },
    ],
  });

  // Log slow queries in development
  prisma.$on('query', (e) => {
    if (e.duration > 200) {
      logger.warn(`Slow query (${e.duration}ms): ${e.query}`);
    }
  });

  prisma.$on('error', (e) => {
    logger.error('Prisma error', { message: e.message });
  });

  prisma.$on('warn', (e) => {
    logger.warn('Prisma warning', { message: e.message });
  });

  return prisma;
}

// Singleton: reuse client in development (hot reloading creates duplicates otherwise)
export const prisma = global.__prisma || createPrismaClient();

if (process.env.NODE_ENV !== 'production') {
  global.__prisma = prisma;
}

// Graceful disconnect
export async function disconnectDatabase(): Promise<void> {
  await prisma.$disconnect();
  logger.info('Database disconnected');
}

export default prisma;
