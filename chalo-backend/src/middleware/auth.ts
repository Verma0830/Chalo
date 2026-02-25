// ============================================================
// Chalo Backend — Authentication Middleware
// Verifies Firebase Auth tokens on protected routes
// ============================================================

import { Request, Response, NextFunction } from 'express';
import { getAuth } from '../config/firebase';
import prisma from '../config/database';
import { ApiError } from '../utils/apiError';
import logger from '../config/logger';
import { AuthenticatedRequest } from '../types';

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

    // Find user in our database
    const user = await prisma.user.findUnique({
      where: { phone: decodedToken.phone_number },
      include: {
        customerProfile: true,
        driverProfile: true,
      },
    });

    if (!user) {
      throw ApiError.unauthorized('User not found. Please register first.');
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
      where: { phone: decodedToken.phone_number },
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
  } catch {
    // Silently continue — optional auth shouldn't block
    next();
  }
};
