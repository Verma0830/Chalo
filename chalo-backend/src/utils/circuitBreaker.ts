// ============================================================
// Chalo Backend — Circuit Breaker
// Prevents cascade failures when external services are down
// ============================================================

import CircuitBreaker from 'opossum';
import logger from '../config/logger';
import { circuitBreakerState, externalApiDuration } from './metrics';

/**
 * Circuit breaker options — conservative defaults for production
 */
interface CircuitBreakerOptions {
  timeout?: number;          // Max time to wait for response (ms)
  errorThresholdPercentage?: number; // % of failures to open circuit
  resetTimeout?: number;     // Time before trying again (ms)
  volumeThreshold?: number;  // Min requests before calculating %
}

const DEFAULT_OPTIONS: CircuitBreakerOptions = {
  timeout: 10000,            // 10 seconds
  errorThresholdPercentage: 50,
  resetTimeout: 30000,       // 30 seconds
  volumeThreshold: 5,        // Need at least 5 requests
};

/**
 * Wrap a function with circuit breaker protection
 * @param name - Service name for logging/metrics
 * @param fn - The async function to protect
 * @param options - Circuit breaker options
 */
export function withCircuitBreaker<TArgs extends unknown[], TResult>(
  name: string,
  fn: (...args: TArgs) => Promise<TResult>,
  options: CircuitBreakerOptions = {}
): (...args: TArgs) => Promise<TResult> {
  const opts = { ...DEFAULT_OPTIONS, ...options };

  const breaker = new CircuitBreaker(fn, {
    timeout: opts.timeout,
    errorThresholdPercentage: opts.errorThresholdPercentage,
    resetTimeout: opts.resetTimeout,
    volumeThreshold: opts.volumeThreshold,
  });

  // Event handlers for observability
  breaker.on('open', () => {
    logger.warn(`Circuit breaker OPENED for ${name}`);
    circuitBreakerState.set({ service: name }, 1);
  });

  breaker.on('halfOpen', () => {
    logger.info(`Circuit breaker HALF-OPEN for ${name}`);
    circuitBreakerState.set({ service: name }, 0.5);
  });

  breaker.on('close', () => {
    logger.info(`Circuit breaker CLOSED for ${name}`);
    circuitBreakerState.set({ service: name }, 0);
  });

  breaker.on('timeout', () => {
    logger.warn(`Circuit breaker timeout for ${name}`);
  });

  breaker.on('reject', () => {
    logger.warn(`Circuit breaker rejected request for ${name} (circuit open)`);
  });

  breaker.on('success', (_result: unknown, latencyMs: number) => {
    externalApiDuration.observe(
      { service: name, operation: 'call', success: 'true' },
      latencyMs / 1000
    );
  });

  breaker.on('failure', (_err: unknown, latencyMs: number) => {
    externalApiDuration.observe(
      { service: name, operation: 'call', success: 'false' },
      (latencyMs || 0) / 1000
    );
  });

  // Initialize state metric
  circuitBreakerState.set({ service: name }, 0);

  // Return wrapped function
  return (...args: TArgs): Promise<TResult> => {
    return breaker.fire(...args) as Promise<TResult>;
  };
}

/**
 * Create a fallback-enabled circuit breaker
 * Returns fallback value when circuit is open
 */
export function withCircuitBreakerFallback<TArgs extends unknown[], TResult>(
  name: string,
  fn: (...args: TArgs) => Promise<TResult>,
  fallback: TResult | ((...args: TArgs) => TResult),
  options: CircuitBreakerOptions = {}
): (...args: TArgs) => Promise<TResult> {
  const opts = { ...DEFAULT_OPTIONS, ...options };

  const breaker = new CircuitBreaker(fn, {
    timeout: opts.timeout,
    errorThresholdPercentage: opts.errorThresholdPercentage,
    resetTimeout: opts.resetTimeout,
    volumeThreshold: opts.volumeThreshold,
  });

  // Add fallback
  breaker.fallback((...args: TArgs) => {
    logger.warn(`Circuit breaker fallback triggered for ${name}`);
    return typeof fallback === 'function'
      ? (fallback as (...args: TArgs) => TResult)(...args)
      : fallback;
  });

  // Event handlers
  breaker.on('open', () => {
    logger.warn(`Circuit breaker OPENED for ${name}`);
    circuitBreakerState.set({ service: name }, 1);
  });

  breaker.on('close', () => {
    logger.info(`Circuit breaker CLOSED for ${name}`);
    circuitBreakerState.set({ service: name }, 0);
  });

  circuitBreakerState.set({ service: name }, 0);

  return (...args: TArgs): Promise<TResult> => {
    return breaker.fire(...args) as Promise<TResult>;
  };
}
