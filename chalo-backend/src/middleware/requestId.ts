// ============================================================
// Chalo Backend — Request ID Middleware
// Adds unique request ID for distributed tracing and log correlation
// ============================================================

import { Request, Response, NextFunction } from 'express';
import { v4 as uuid } from 'uuid';

declare global {
  // eslint-disable-next-line @typescript-eslint/no-namespace
  namespace Express {
    interface Request {
      id: string;
    }
  }
}

/**
 * Request ID Middleware
 * Attaches a unique ID to each request for tracing through logs
 * Accepts incoming X-Request-Id header or generates new UUID
 */
export function requestIdMiddleware(
  req: Request,
  res: Response,
  next: NextFunction
): void {
  // Use existing request ID from upstream proxy or generate new one
  const upstreamId =
    (req.headers['x-request-id'] as string) ||
    (req.headers['x-correlation-id'] as string);

  // Sanitize: allow only alphanumeric, hyphens, and underscores (max 128 chars)
  const requestId = upstreamId && /^[\w-]{1,128}$/.test(upstreamId)
    ? upstreamId
    : uuid();

  req.id = requestId;

  // Set response header for client correlation
  res.setHeader('X-Request-Id', requestId);

  next();
}

/**
 * Get request ID from request object
 * Safe to call even if middleware wasn't applied
 */
export function getRequestId(req: Request): string {
  return req.id || 'unknown';
}
