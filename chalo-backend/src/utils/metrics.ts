// ============================================================
// Chalo Backend — Prometheus Metrics
// Observability with custom metrics for ride-hailing operations
// ============================================================

import { Registry, Counter, Histogram, Gauge, collectDefaultMetrics } from 'prom-client';

// Create a dedicated registry for Chalo metrics
export const metricsRegistry = new Registry();

// Set default labels
metricsRegistry.setDefaultLabels({
  app: 'chalo-backend',
});

// Collect default Node.js metrics (CPU, memory, event loop, etc.)
collectDefaultMetrics({ register: metricsRegistry });

// -------------------------------------------------------
// Custom Business Metrics
// -------------------------------------------------------

/**
 * HTTP Request Duration Histogram
 * Tracks response time for all API endpoints
 */
export const httpRequestDuration = new Histogram({
  name: 'http_request_duration_seconds',
  help: 'Duration of HTTP requests in seconds',
  labelNames: ['method', 'path', 'status_code'],
  buckets: [0.01, 0.05, 0.1, 0.25, 0.5, 1, 2.5, 5, 10],
  registers: [metricsRegistry],
});

/**
 * HTTP Request Counter
 * Total number of requests by method, path, status
 */
export const httpRequestTotal = new Counter({
  name: 'http_requests_total',
  help: 'Total number of HTTP requests',
  labelNames: ['method', 'path', 'status_code'],
  registers: [metricsRegistry],
});

/**
 * Rides Created Counter
 */
export const ridesCreatedTotal = new Counter({
  name: 'chalo_rides_created_total',
  help: 'Total number of rides created',
  labelNames: ['type'], // 'on_demand' | 'scheduled'
  registers: [metricsRegistry],
});

/**
 * Rides Completed Counter
 */
export const ridesCompletedTotal = new Counter({
  name: 'chalo_rides_completed_total',
  help: 'Total number of rides completed successfully',
  registers: [metricsRegistry],
});

/**
 * Rides Cancelled Counter
 */
export const ridesCancelledTotal = new Counter({
  name: 'chalo_rides_cancelled_total',
  help: 'Total number of rides cancelled',
  labelNames: ['cancelled_by'], // 'customer' | 'driver' | 'system'
  registers: [metricsRegistry],
});

/**
 * Payment Failures Counter
 */
export const paymentFailuresTotal = new Counter({
  name: 'chalo_payment_failures_total',
  help: 'Total number of payment failures',
  labelNames: ['method'], // 'upi' | 'cash'
  registers: [metricsRegistry],
});

/**
 * Driver Search Duration Histogram
 */
export const driverSearchDuration = new Histogram({
  name: 'chalo_driver_search_duration_seconds',
  help: 'Time taken to find and match a driver',
  buckets: [1, 5, 10, 30, 60, 120],
  registers: [metricsRegistry],
});

/**
 * Active Rides Gauge
 */
export const activeRidesGauge = new Gauge({
  name: 'chalo_active_rides',
  help: 'Current number of active rides',
  labelNames: ['status'],
  registers: [metricsRegistry],
});

/**
 * Online Drivers Gauge
 */
export const onlineDriversGauge = new Gauge({
  name: 'chalo_online_drivers',
  help: 'Current number of online drivers',
  registers: [metricsRegistry],
});

/**
 * SOS Alerts Counter
 */
export const sosAlertsTotal = new Counter({
  name: 'chalo_sos_alerts_total',
  help: 'Total number of SOS alerts triggered',
  registers: [metricsRegistry],
});

/**
 * External API Call Duration (Razorpay, Google Maps, etc.)
 */
export const externalApiDuration = new Histogram({
  name: 'chalo_external_api_duration_seconds',
  help: 'Duration of external API calls',
  labelNames: ['service', 'operation', 'success'],
  buckets: [0.1, 0.25, 0.5, 1, 2, 5, 10],
  registers: [metricsRegistry],
});

/**
 * Circuit Breaker State Gauge
 */
export const circuitBreakerState = new Gauge({
  name: 'chalo_circuit_breaker_state',
  help: 'Circuit breaker state (0=closed, 1=open, 0.5=half-open)',
  labelNames: ['service'],
  registers: [metricsRegistry],
});

// -------------------------------------------------------
// Metrics Endpoint Handler
// -------------------------------------------------------

import { Request, Response } from 'express';

/**
 * Express handler for /metrics endpoint
 * Returns Prometheus-formatted metrics
 */
export async function metricsHandler(_req: Request, res: Response): Promise<void> {
  try {
    res.set('Content-Type', metricsRegistry.contentType);
    res.end(await metricsRegistry.metrics());
  } catch (error) {
    res.status(500).end('Error generating metrics');
  }
}
