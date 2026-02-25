// ============================================================
// Chalo Backend — Validation Middleware
// Uses Zod schemas to validate request body, params, query
// ============================================================

import { Request, Response, NextFunction } from 'express';
import { ZodSchema, ZodError } from 'zod';
import { ApiError } from '../utils/apiError';

type ValidationTarget = 'body' | 'params' | 'query';

/**
 * Generic validation middleware factory
 * Pass a Zod schema and which part of the request to validate
 * 
 * Usage:
 *   router.post('/rides', validate(createRideSchema, 'body'), controller.createRide);
 *   router.get('/rides/:id', validate(rideParamsSchema, 'params'), controller.getRide);
 */
export function validate(schema: ZodSchema, target: ValidationTarget = 'body') {
  return (req: Request, _res: Response, next: NextFunction): void => {
    try {
      const data = req[target];
      const parsed = schema.parse(data);

      // Replace with parsed (cleaned/transformed) data
      req[target] = parsed;

      next();
    } catch (error) {
      if (error instanceof ZodError) {
        const formattedErrors = error.errors.map((err) => ({
          field: err.path.join('.'),
          message: err.message,
          code: err.code,
        }));

        next(ApiError.badRequest('Validation failed', formattedErrors));
        return;
      }

      next(error);
    }
  };
}

/**
 * Convenience: validate request body
 */
export function validateBody(schema: ZodSchema) {
  return validate(schema, 'body');
}

/**
 * Convenience: validate URL params
 */
export function validateParams(schema: ZodSchema) {
  return validate(schema, 'params');
}

/**
 * Convenience: validate query string
 */
export function validateQuery(schema: ZodSchema) {
  return validate(schema, 'query');
}
