// ============================================================
// Chalo Backend — Sanitize Middleware
// Applies XSS sanitization to all request bodies
// ============================================================

import { Request, Response, NextFunction } from 'express';
import { sanitizeObject } from '../utils/sanitize';

/**
 * Middleware that sanitizes all string fields in request body
 * Must be placed AFTER body parsers (express.json)
 */
export function sanitizeBody(req: Request, _res: Response, next: NextFunction): void {
  if (req.body && typeof req.body === 'object' && !Buffer.isBuffer(req.body)) {
    req.body = sanitizeObject(req.body);
  }
  next();
}
