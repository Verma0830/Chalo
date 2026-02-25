// ============================================================
// Chalo Backend — Input Sanitization Utilities
// Protects against XSS and injection attacks
// ============================================================

import xss, { IFilterXSSOptions } from 'xss';

/**
 * XSS sanitization options — strict mode
 */
const xssOptions: IFilterXSSOptions = {
  whiteList: {}, // No HTML tags allowed
  stripIgnoreTag: true,
  stripIgnoreTagBody: ['script', 'style'],
};

/**
 * Sanitize a single string value
 * Removes all HTML tags and XSS attack vectors
 */
export function sanitizeString(input: string): string {
  if (typeof input !== 'string') return input;
  return xss(input.trim(), xssOptions);
}

/**
 * Sanitize an object's string properties (shallow)
 * Use for request body sanitization
 */
export function sanitizeObject<T extends Record<string, unknown>>(obj: T): T {
  const sanitized = { ...obj };

  for (const key of Object.keys(sanitized)) {
    const value = sanitized[key];

    if (typeof value === 'string') {
      (sanitized as Record<string, unknown>)[key] = sanitizeString(value);
    } else if (value && typeof value === 'object' && !Array.isArray(value)) {
      (sanitized as Record<string, unknown>)[key] = sanitizeObject(
        value as Record<string, unknown>
      );
    }
  }

  return sanitized;
}

/**
 * Sanitize a location object (address field)
 */
export function sanitizeLocation(location: {
  lat: number;
  lng: number;
  address: string;
}): { lat: number; lng: number; address: string } {
  return {
    lat: location.lat,
    lng: location.lng,
    address: sanitizeString(location.address),
  };
}
