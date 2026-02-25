// ============================================================
// Chalo Backend — Global Error Handler Middleware
// Catches all errors and returns consistent API responses
// ============================================================

import { Request, Response, NextFunction } from 'express';
import { ApiError, ErrorCode } from '../utils/apiError';
import { getRequestId } from './requestId';
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
  next(ApiError.notFound(`Route not found: ${req.method} ${req.originalUrl}`, ErrorCode.NOT_FOUND));
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
  const requestId = getRequestId(req);

  // Default to 500 Internal Server Error
  let statusCode = 500;
  let message = 'Internal Server Error';
  let code: string = ErrorCode.INTERNAL_ERROR;
  let errors: unknown[] = [];
  let stack: string | undefined;

  if (err instanceof ApiError) {
    // Our custom API error — trusted, use directly
    statusCode = err.statusCode;
    message = err.message;
    code = err.code;
    errors = err.errors;
  } else if (err.name === 'ValidationError') {
    // Zod or similar validation error
    statusCode = 400;
    message = err.message;
    code = ErrorCode.VALIDATION_ERROR;
  } else if (err.name === 'PrismaClientKnownRequestError') {
    // Prisma constraint violations, etc.
    statusCode = 409;
    message = 'Database operation failed — possible duplicate or constraint violation';
    code = 'DATABASE_CONSTRAINT_ERROR';
  } else if (err.name === 'PrismaClientValidationError') {
    statusCode = 400;
    message = 'Invalid data sent to database';
    code = ErrorCode.VALIDATION_ERROR;
  } else if (err.name === 'PayloadTooLargeError' || (err as { type?: string }).type === 'entity.too.large') {
    statusCode = 413;
    message = 'Request entity too large';
    code = ErrorCode.PAYLOAD_TOO_LARGE;
  } else {
    // Unexpected error — log full details, return generic message
    message = config.isDev ? err.message : 'Something went wrong. Please try again.';
  }

  // Stack trace — only in development
  if (config.isDev) {
    stack = err.stack;
  }

  // Log the error with request ID for correlation
  const logPayload = {
    requestId,
    statusCode,
    code,
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

  // Send response with error code
  res.status(statusCode).json({
    success: false,
    statusCode,
    code,
    message,
    requestId,
    ...(errors.length > 0 ? { errors } : {}),
    ...(stack ? { stack } : {}),
    timestamp: new Date().toISOString(),
  });
};
