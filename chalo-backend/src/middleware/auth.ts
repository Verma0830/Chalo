// ============================================================
// Chalo Backend — Authentication Middleware
// Verifies Firebase Auth tokens on protected routes
// ============================================================

import { Request, Response, NextFunction } from 'express';
import { UserRole } from '@prisma/client';
import { getAuth } from '../config/firebase';
import prisma from '../config/database';
import { getRedisClient, isRedisReady } from '../config/redis';
import { ApiError } from '../utils/apiError';
import logger from '../config/logger';
import { AuthenticatedRequest } from '../types';

/** TTL for cached user records in Redis (seconds) */
const AUTH_CACHE_TTL_SECS = 300; // 5 minutes

/** Shape of the user object stored in the auth cache */
interface CachedAuthUser {
  id: string;
  phone: string;
  name: string | null;
  role: UserRole;
  languagePref: string;
  isActive: boolean;
}

/**
 * Middleware: Verify Firebase ID token from Authorization header
 * Attaches user data to request object for downstream use
 */
export const authenticate = async (
  req: Request,
  _res: Response,
  next: NextFunction
): Promise<void> => {
  try {
    const authHeader = req.headers.authorization;

    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      throw ApiError.unauthorized('Missing or invalid Authorization header. Expected: Bearer <token>');
    }

    const token = authHeader.split('Bearer ')[1];

    if (!token) {
      throw ApiError.unauthorized('Token not provided');
    }

    // Verify with Firebase
    const decodedToken = await getAuth().verifyIdToken(token);

    // Check Redis cache first to avoid DB hit on every request
    const cacheKey = `auth:user:${decodedToken.uid}`;
    const redis = getRedisClient();
    let user: CachedAuthUser | null = null;

    if (redis && isRedisReady()) {
      try {
        const cached = await redis.get(cacheKey);
        if (cached) {
          user = JSON.parse(cached);
        }
      } catch (err) {
        logger.warn('Auth cache read failed — falling back to DB', { error: String(err) });
      }
    }

    // Cache miss — query database
    if (!user) {
      const dbUser = await prisma.user.findUnique({
        where: { id: decodedToken.uid },
        include: {
          customerProfile: true,
          driverProfile: true,
        },
      });

      if (!dbUser) {
        throw ApiError.unauthorized('User not found. Please register first.');
      }

      user = {
        id: dbUser.id,
        phone: dbUser.phone,
        name: dbUser.name,
        role: dbUser.role,
        languagePref: dbUser.languagePref,
        isActive: dbUser.isActive,
      };

      // Populate cache (fire-and-forget)
      if (redis && isRedisReady()) {
        redis
          .setEx(cacheKey, AUTH_CACHE_TTL_SECS, JSON.stringify(user))
          .catch((err) => {
            logger.warn('Auth cache write failed', { error: String(err) });
          });
      }
    }

    if (!user.isActive) {
      throw ApiError.forbidden('Account is deactivated. Contact support.');
    }

    // Attach user to request
    (req as AuthenticatedRequest).user = {
      id: user.id,
      phone: user.phone,
      name: user.name,
      role: user.role,
      languagePref: user.languagePref,
    };

    next();
  } catch (error) {
    if (error instanceof ApiError) {
      next(error);
      return;
    }
    logger.error('Auth middleware error', { error });
    next(ApiError.unauthorized('Invalid or expired token'));
  }
};

/**
 * Middleware: Restrict route to specific roles
 * Must be used AFTER authenticate middleware
 */
export const authorize = (...allowedRoles: string[]) => {
  return (req: Request, _res: Response, next: NextFunction): void => {
    const authReq = req as AuthenticatedRequest;

    if (!authReq.user) {
      next(ApiError.unauthorized('Authentication required'));
      return;
    }

    if (!allowedRoles.includes(authReq.user.role)) {
      next(ApiError.forbidden(`Access denied. Required role: ${allowedRoles.join(' or ')}`));
      return;
    }

    next();
  };
};

/**
 * Middleware: Optional auth — attaches user if token present, continues if not
 * Useful for public endpoints that behave differently for logged-in users
 */
export const optionalAuth = async (
  req: Request,
  _res: Response,
  next: NextFunction
): Promise<void> => {
  try {
    const authHeader = req.headers.authorization;

    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      next();
      return;
    }

    const token = authHeader.split('Bearer ')[1];
    if (!token) {
      next();
      return;
    }

    const decodedToken = await getAuth().verifyIdToken(token);
    const user = await prisma.user.findUnique({
      where: { id: decodedToken.uid },
    });

    if (user && user.isActive) {
      (req as AuthenticatedRequest).user = {
        id: user.id,
        phone: user.phone,
        name: user.name,
        role: user.role,
        languagePref: user.languagePref,
      };
    }

    next();
  } catch (error) {
    // Log unexpected failures (Firebase outages, network errors) at warn level.
    // Auth errors (expired token, malformed header) are expected — log at debug.
    const message = error instanceof Error ? error.message : String(error);
    if (message.includes('expired') || message.includes('Decoding')) {
      logger.debug('Optional auth token invalid', { error: message });
    } else {
      logger.warn('Optional auth unexpected error', { error: message });
    }
    // Continue — optional auth should never block the request
    next();
  }
};

