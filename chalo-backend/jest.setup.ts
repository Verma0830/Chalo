// ============================================================
// Jest Global Setup — Mocks for ESM-only modules and test app
// Provides uuid, metrics, and rate limiter mocks for all tests
// ============================================================

jest.mock('uuid', () => ({
  v4: () => 'test-uuid-0000-0000-0000-000000000000',
}));

jest.mock('./src/utils/metrics', () => ({
  metricsHandler: jest.fn((_req: unknown, res: { set: (key: string, value: string) => void; send: (body: string) => void; }) => {
    res.set('Content-Type', 'text/plain');
    res.send('# metrics');
  }),
  httpRequestDuration: { observe: jest.fn() },
  httpRequestTotal: { inc: jest.fn() },
  ridesCreatedTotal: { inc: jest.fn() },
  ridesCancelledTotal: { inc: jest.fn() },
  ridesCompletedTotal: { inc: jest.fn() },
  paymentFailuresTotal: { inc: jest.fn() },
  driverSearchDuration: { observe: jest.fn() },
  activeRidesGauge: { set: jest.fn(), inc: jest.fn(), dec: jest.fn() },
  onlineDriversGauge: { set: jest.fn(), inc: jest.fn(), dec: jest.fn() },
  sosAlertsTotal: { inc: jest.fn() },
  externalApiDuration: { observe: jest.fn() },
  circuitBreakerState: { set: jest.fn() },
  metricsRegistry: {
    metrics: jest.fn().mockResolvedValue(''),
    contentType: 'text/plain',
  },
}));

const noop = (_req: unknown, _res: unknown, next: () => void) => next();

jest.mock('./src/middleware/rateLimiter', () => ({
  createRateLimiter: () => noop,
  authRateLimiter: noop,
  apiRateLimiter: noop,
  rideRateLimiter: noop,
  webhookRateLimiter: noop,
  initRateLimitRedis: jest.fn().mockResolvedValue(undefined),
  disconnectRateLimitRedis: jest.fn().mockResolvedValue(undefined),
}));
