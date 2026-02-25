// ============================================================
// Chalo Backend — Global Error Handler Middleware
// Catches all errors and returns consistent API responses
// ============================================================

import { Request, Response, NextFunction } from 'express';
import { ApiError } from '../utils/apiError';
import logger from '../config/logger';
import config from '../config';

/**
 * 404 Not Found handler — catches all unmatched routes
 */
export const notFoundHandler = (
  req: Request,
  _res: Response,
  next: NextFunction
): void => {
  next(ApiError.notFound(`Route not found: ${req.method} ${req.originalUrl}`));
};

/**
 * Global error handler — MUST have 4 parameters for Express to recognize it
 * Converts all errors into a consistent JSON response format
 */
export const globalErrorHandler = (
  err: Error | ApiError,
  req: Request,
  res: Response,
  _next: NextFunction
): void => {

  // Default to 500 Internal Server Error
  let statusCode = 500;
  let message = 'Internal Server Error';
  let errors: unknown[] = [];
  let stack: string | undefined;

  if (err instanceof ApiError) {
    // Our custom API error — trusted, use directly
    statusCode = err.statusCode;
    message = err.message;
    errors = err.errors;
  } else if (err.name === 'ValidationError') {
    // Zod or similar validation error
    statusCode = 400;
    message = err.message;
  } else if (err.name === 'PrismaClientKnownRequestError') {
    // Prisma constraint violations, etc.
    statusCode = 409;
    message = 'Database operation failed — possible duplicate or constraint violation';
  } else if (err.name === 'PrismaClientValidationError') {
    statusCode = 400;
    message = 'Invalid data sent to database';
  } else {
    // Unexpected error — log full details, return generic message
    message = config.isDev ? err.message : 'Something went wrong. Please try again.';
  }

  // Stack trace — only in development
  if (config.isDev) {
    stack = err.stack;
  }

  // Log the error
  const logPayload = {
    statusCode,
    message: err.message,
    url: req.originalUrl,
    method: req.method,
    ip: req.ip,
    userAgent: req.get('user-agent'),
    ...(statusCode >= 500 ? { stack: err.stack } : {}),
  };

  if (statusCode >= 500) {
    logger.error('Server error', logPayload);
  } else if (statusCode >= 400) {
    logger.warn('Client error', logPayload);
  }

  // Send response
  res.status(statusCode).json({
    success: false,
    statusCode,
    message,
    ...(errors.length > 0 ? { errors } : {}),
    ...(stack ? { stack } : {}),
    timestamp: new Date().toISOString(),
  });
};
