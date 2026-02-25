// ============================================================
// Chalo Backend — Custom API Error Class
// Extends Error with HTTP status codes, error codes, and structured errors
// ============================================================

/**
 * Standard error codes for programmatic client handling
 */
export enum ErrorCode {
  // General
  VALIDATION_ERROR = 'VALIDATION_ERROR',
  INTERNAL_ERROR = 'INTERNAL_ERROR',
  NOT_FOUND = 'NOT_FOUND',
  UNAUTHORIZED = 'UNAUTHORIZED',
  FORBIDDEN = 'FORBIDDEN',
  TOO_MANY_REQUESTS = 'TOO_MANY_REQUESTS',
  SERVICE_UNAVAILABLE = 'SERVICE_UNAVAILABLE',
  PAYLOAD_TOO_LARGE = 'PAYLOAD_TOO_LARGE',

  // Auth
  INVALID_TOKEN = 'INVALID_TOKEN',
  TOKEN_EXPIRED = 'TOKEN_EXPIRED',
  OTP_EXPIRED = 'OTP_EXPIRED',
  OTP_INVALID = 'OTP_INVALID',
  OTP_MAX_ATTEMPTS = 'OTP_MAX_ATTEMPTS',

  // Ride
  RIDE_NOT_FOUND = 'RIDE_NOT_FOUND',
  RIDE_ALREADY_ACTIVE = 'RIDE_ALREADY_ACTIVE',
  RIDE_ALREADY_RATED = 'RIDE_ALREADY_RATED',
  RIDE_CANNOT_CANCEL = 'RIDE_CANNOT_CANCEL',
  RIDE_NOT_COMPLETED = 'RIDE_NOT_COMPLETED',
  NO_DRIVERS_AVAILABLE = 'NO_DRIVERS_AVAILABLE',
  DRIVER_NOT_FOUND = 'DRIVER_NOT_FOUND',

  // Payment
  PAYMENT_FAILED = 'PAYMENT_FAILED',
  PAYMENT_INVALID_SIGNATURE = 'PAYMENT_INVALID_SIGNATURE',
  PAYMENT_ALREADY_COMPLETED = 'PAYMENT_ALREADY_COMPLETED',
  INVALID_PAYMENT_METHOD = 'INVALID_PAYMENT_METHOD',

  // Driver
  DRIVER_NOT_VERIFIED = 'DRIVER_NOT_VERIFIED',
  DRIVER_OFFLINE = 'DRIVER_OFFLINE',
  SUBSCRIPTION_EXPIRED = 'SUBSCRIPTION_EXPIRED',

  // Profile
  PROFILE_NOT_FOUND = 'PROFILE_NOT_FOUND',
  PROFILE_INCOMPLETE = 'PROFILE_INCOMPLETE',
}

export class ApiError extends Error {
  public readonly statusCode: number;
  public readonly code: ErrorCode | string;
  public readonly isOperational: boolean;
  public readonly errors: unknown[];

  constructor(
    statusCode: number,
    message: string,
    code: ErrorCode | string = ErrorCode.INTERNAL_ERROR,
    errors: unknown[] = [],
    isOperational = true
  ) {
    super(message);
    this.statusCode = statusCode;
    this.code = code;
    this.isOperational = isOperational;
    this.errors = errors;

    // Maintain proper prototype chain
    Object.setPrototypeOf(this, ApiError.prototype);

    // Capture stack trace
    Error.captureStackTrace(this, this.constructor);
  }

  // --- Factory methods for common HTTP errors ---

  static badRequest(message = 'Bad Request', code: ErrorCode | string = ErrorCode.VALIDATION_ERROR, errors: unknown[] = []): ApiError {
    return new ApiError(400, message, code, errors);
  }

  static unauthorized(message = 'Unauthorized', code: ErrorCode | string = ErrorCode.UNAUTHORIZED): ApiError {
    return new ApiError(401, message, code);
  }

  static forbidden(message = 'Forbidden', code: ErrorCode | string = ErrorCode.FORBIDDEN): ApiError {
    return new ApiError(403, message, code);
  }

  static notFound(message = 'Resource not found', code: ErrorCode | string = ErrorCode.NOT_FOUND): ApiError {
    return new ApiError(404, message, code);
  }

  static conflict(message = 'Conflict — resource already exists', code: ErrorCode | string = 'CONFLICT'): ApiError {
    return new ApiError(409, message, code);
  }

  static tooManyRequests(message = 'Too many requests', code: ErrorCode | string = ErrorCode.TOO_MANY_REQUESTS): ApiError {
    return new ApiError(429, message, code);
  }

  static internal(message = 'Internal Server Error', code: ErrorCode | string = ErrorCode.INTERNAL_ERROR): ApiError {
    return new ApiError(500, message, code, [], false);
  }

  static serviceUnavailable(message = 'Service temporarily unavailable', code: ErrorCode | string = ErrorCode.SERVICE_UNAVAILABLE): ApiError {
    return new ApiError(503, message, code);
  }
}
