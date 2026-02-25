// ============================================================
// Tests — ApiError custom error class
// ============================================================

import { ApiError, ErrorCode } from '../../utils/apiError';

describe('ApiError', () => {
  describe('constructor', () => {
    it('creates an error with the correct properties', () => {
      const err = new ApiError(400, 'Bad request', ErrorCode.VALIDATION_ERROR, ['field required']);
      expect(err.statusCode).toBe(400);
      expect(err.message).toBe('Bad request');
      expect(err.code).toBe(ErrorCode.VALIDATION_ERROR);
      expect(err.errors).toEqual(['field required']);
      expect(err.isOperational).toBe(true);
    });

    it('defaults to empty errors array', () => {
      const err = new ApiError(404, 'Not found');
      expect(err.errors).toEqual([]);
    });

    it('is an instance of Error', () => {
      const err = new ApiError(500, 'Server error');
      expect(err).toBeInstanceOf(Error);
      expect(err).toBeInstanceOf(ApiError);
    });

    it('captures a stack trace', () => {
      const err = new ApiError(400, 'test');
      expect(err.stack).toBeDefined();
    });
  });

  describe('factory methods', () => {
    it('badRequest() returns 400', () => {
      const err = ApiError.badRequest();
      expect(err.statusCode).toBe(400);
      expect(err.message).toBe('Bad Request');
      expect(err.code).toBe(ErrorCode.VALIDATION_ERROR);
    });

    it('badRequest() accepts custom message, code, and errors', () => {
      const err = ApiError.badRequest('Validation failed', ErrorCode.VALIDATION_ERROR, [{ field: 'phone' }]);
      expect(err.message).toBe('Validation failed');
      expect(err.code).toBe(ErrorCode.VALIDATION_ERROR);
      expect(err.errors).toHaveLength(1);
    });

    it('unauthorized() returns 401', () => {
      const err = ApiError.unauthorized();
      expect(err.statusCode).toBe(401);
      expect(err.code).toBe(ErrorCode.UNAUTHORIZED);
      expect(err.isOperational).toBe(true);
    });

    it('forbidden() returns 403', () => {
      expect(ApiError.forbidden().statusCode).toBe(403);
    });

    it('notFound() returns 404', () => {
      const err = ApiError.notFound('User not found');
      expect(err.statusCode).toBe(404);
      expect(err.message).toBe('User not found');
    });

    it('conflict() returns 409', () => {
      expect(ApiError.conflict().statusCode).toBe(409);
    });

    it('tooManyRequests() returns 429', () => {
      expect(ApiError.tooManyRequests().statusCode).toBe(429);
    });

    it('internal() returns 500 and isOperational=false', () => {
      const err = ApiError.internal();
      expect(err.statusCode).toBe(500);
      expect(err.isOperational).toBe(false);
    });

    it('serviceUnavailable() returns 503', () => {
      expect(ApiError.serviceUnavailable().statusCode).toBe(503);
    });
  });
});
