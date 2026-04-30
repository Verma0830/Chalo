// ============================================================
// Chalo Backend — Standardized API Response Builder
// Every response uses this format for consistency
// ============================================================

import { Response } from 'express';
import { ApiResponseData, PaginationMeta } from '../types';

export class ApiResponse {
  /**
   * Success response with data
   */
  static success<T>(
    res: Response,
    data: T,
    message = 'Success',
    statusCode = 200
  ): Response {
    const response: ApiResponseData<T> = {
      success: true,
      statusCode,
      message,
      data,
      timestamp: new Date().toISOString(),
    };

    return res.status(statusCode).json(response);
  }

  /**
   * Success response with pagination metadata
   */
  static paginated<T>(
    res: Response,
    data: T[],
    meta: PaginationMeta,
    message = 'Success'
  ): Response {
    const response: ApiResponseData<T[]> = {
      success: true,
      statusCode: 200,
      message,
      data,
      meta,
      timestamp: new Date().toISOString(),
    };

    return res.status(200).json(response);
  }

  /**
   * Created response (201)
   */
  static created<T>(res: Response, data: T, message = 'Created successfully'): Response {
    return ApiResponse.success(res, data, message, 201);
  }

  /**
   * No content response (204)
   */
  static noContent(res: Response): Response {
    return res.status(204).send();
  }

  /**
   * Error response (used by error handler middleware)
   */
  static error(
    res: Response,
    statusCode: number,
    message: string,
    errors: unknown[] = []
  ): Response {
    const response: ApiResponseData = {
      success: false,
      statusCode,
      message,
      ...(errors.length > 0 ? { data: errors } : {}),
      timestamp: new Date().toISOString(),
    };

    return res.status(statusCode).json(response);
  }
}

/**
 * Build pagination metadata from query + total count
 */
export function buildPaginationMeta(
  page: number,
  limit: number,
  total: number
): PaginationMeta {
  const totalPages = Math.ceil(total / limit);

  return {
    page,
    limit,
    total,
    totalPages,
    hasNext: page < totalPages,
    hasPrev: page > 1,
  };
}
