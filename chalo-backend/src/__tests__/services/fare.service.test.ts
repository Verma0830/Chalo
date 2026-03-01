// ============================================================
// Tests — Fare Service (Google Maps Directions API + Haversine fallback)
// ============================================================

// ── Mocks — must come before imports ──────────────────────────
jest.mock('../../config/logger', () => ({
  __esModule: true,
  default: { info: jest.fn(), warn: jest.fn(), error: jest.fn(), debug: jest.fn() },
}));

// Bypass circuit breaker in unit tests — execute the underlying fn directly
// so we can mock fetch independently per test.
jest.mock('../../utils/circuitBreaker', () => ({
  withCircuitBreaker: (_name: string, fn: (...args: unknown[]) => unknown) => fn,
  withCircuitBreakerFallback: (
    _name: string,
    fn: (...args: unknown[]) => unknown,
    fallback: unknown
  ) => {
    // Return a wrapper that calls fn and on error calls fallback
    return async (...args: unknown[]) => {
      try {
        return await fn(...args);
      } catch {
        return typeof fallback === 'function' ? fallback(...args) : fallback;
      }
    };
  },
}));

jest.mock('../../utils/metrics', () => ({
  circuitBreakerState: { set: jest.fn() },
  externalApiDuration: { observe: jest.fn() },
}));

const mockPlatformConfigFindMany = jest.fn();
jest.mock('../../config/database', () => ({
  __esModule: true,
  default: {
    platformConfig: { findMany: mockPlatformConfigFindMany },
  },
}));

const mockRedisGet = jest.fn();
const mockRedisSetEx = jest.fn().mockResolvedValue('OK');
jest.mock('../../config/redis', () => ({
  getRedisClient: () => ({ get: mockRedisGet, setEx: mockRedisSetEx }),
  isRedisReady: () => true,
}));

// Mock global fetch
const mockFetch = jest.fn();
global.fetch = mockFetch as typeof fetch;

// ── Import under test ──────────────────────────────────────────
import { FareService } from '../../services/fare.service';

// ── Helper: build a valid Google Maps Directions API response ──
function mapsResponse(distanceMeters: number, durationSeconds: number) {
  return {
    status: 'OK',
    routes: [
      {
        legs: [
          {
            distance: { value: distanceMeters },
            duration: { value: durationSeconds },
          },
        ],
        overview_polyline: { points: 'encoded_polyline_here' },
      },
    ],
  };
}

// ── Helper: resolve fetch with a JSON body ─────────────────────
function mockFetchOk(body: object) {
  mockFetch.mockResolvedValueOnce({
    ok: true,
    json: jest.fn().mockResolvedValue(body),
  });
}

// ── Baseline config mock ───────────────────────────────────────
// Keys must match CONSTANTS.CONFIG_KEYS values (lowercase_snake_case)
const defaultConfigs = [
  { key: 'base_fare_per_km', value: '12' },
  { key: 'base_fare_per_min', value: '2' },
  { key: 'commission_percentage', value: '15' },
  { key: 'surge_enabled', value: 'false' },
];

describe('FareService.estimateFare', () => {
  let service: FareService;
  const pickup = { lat: 28.4089, lng: 77.3178, address: 'Sector 21, Faridabad' };
  const drop = { lat: 28.5017, lng: 77.4041, address: 'Connaught Place, Delhi' };

  beforeEach(() => {
    jest.clearAllMocks();
    service = new FareService();
    // No Redis cache by default → falls through to DB
    mockRedisGet.mockResolvedValue(null);
    mockPlatformConfigFindMany.mockResolvedValue(defaultConfigs);
  });

  it('uses Google Maps distance when API returns OK', async () => {
    // 10 km, 20 minutes
    mockFetchOk(mapsResponse(10_000, 1_200));

    const result = await service.estimateFare(pickup, drop);

    expect(result.distanceKm).toBe(10);
    expect(result.durationMins).toBe(20);
    expect(result.totalFare).toBeGreaterThan(0);
    expect(result.currency).toBe('INR');
  });

  it('fare breakdown: distanceFare + timeFare + bookingFee ≥ minFare', async () => {
    // 5 km, 10 minutes — ₹12*5 + ₹2*10 + ₹5 = ₹85 >= ₹30 min
    mockFetchOk(mapsResponse(5_000, 600));

    const result = await service.estimateFare(pickup, drop);

    expect(result.distanceFare).toBe(60);   // 5 km × ₹12
    expect(result.timeFare).toBe(20);        // 10 min × ₹2
    expect(result.totalFare).toBe(result.baseFare); // No surge (disabled)
    expect(result.surgeMultiplier).toBe(1.0);
  });

  it('falls back to Haversine when Google Maps returns non-OK status', async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: jest.fn().mockResolvedValue({ status: 'ZERO_RESULTS', routes: [] }),
    });

    const result = await service.estimateFare(pickup, drop);

    // Should still return a valid estimate via Haversine fallback
    expect(result.distanceKm).toBeGreaterThan(0);
    expect(result.durationMins).toBeGreaterThan(0);
    expect(result.totalFare).toBeGreaterThanOrEqual(30);
  });

  it('falls back to Haversine when fetch throws (network error)', async () => {
    mockFetch.mockRejectedValueOnce(new Error('Network failure'));

    const result = await service.estimateFare(pickup, drop);

    expect(result.distanceKm).toBeGreaterThan(0);
    expect(result.totalFare).toBeGreaterThanOrEqual(30); // At least min fare
  });

  it('enforces minimum fare of ₹30', async () => {
    // 0.5 km, 2 minutes → ₹6 + ₹4 + ₹5 = ₹15 → clamped to ₹30
    mockFetchOk(mapsResponse(500, 120));

    const result = await service.estimateFare(pickup, drop);

    expect(result.totalFare).toBeGreaterThanOrEqual(30);
  });

  it('uses cached Redis config when available', async () => {
    const cachedConfig = {
      baseFarePerKm: 15,
      baseFarePerMin: 3,
      commissionPercentage: 15,
      surgeEnabled: false,
    };
    mockRedisGet.mockResolvedValueOnce(JSON.stringify(cachedConfig));
    // 10 km, 20 minutes
    mockFetchOk(mapsResponse(10_000, 1_200));

    const result = await service.estimateFare(pickup, drop);

    // ₹15*10 + ₹3*20 + ₹5 = ₹215
    expect(result.distanceFare).toBe(150); // 10 × 15
    expect(result.timeFare).toBe(60);      // 20 × 3
    // DB should NOT have been called when Redis cache hits
    expect(mockPlatformConfigFindMany).not.toHaveBeenCalled();
  });
});

describe('FareService.calculateFinalFare', () => {
  let service: FareService;

  beforeEach(() => {
    jest.clearAllMocks();
    service = new FareService();
    mockRedisGet.mockResolvedValue(null);
    mockPlatformConfigFindMany.mockResolvedValue(defaultConfigs);
  });

  it('deducts 15% commission for COMMISSION plan drivers', async () => {
    const result = await service.calculateFinalFare(10, 20, 1.0, 'COMMISSION');

    // Base: ₹12*10 + ₹2*20 + ₹5 = ₹165. With surge 1.0 → ₹165
    // Commission 15%: ₹24.75 → floor = ₹24
    expect(result.commissionAmount).toBeGreaterThan(0);
    expect(result.driverEarning).toBe(result.finalFare - result.commissionAmount);
  });

  it('keeps 100% fare for SUBSCRIPTION plan drivers', async () => {
    const result = await service.calculateFinalFare(10, 20, 1.0, 'SUBSCRIPTION');

    expect(result.commissionAmount).toBe(0);
    expect(result.driverEarning).toBe(result.finalFare);
  });

  it('applies surge multiplier — surged fare exceeds base fare', async () => {
    const base = await service.calculateFinalFare(10, 20, 1.0, 'SUBSCRIPTION');
    const surged = await service.calculateFinalFare(10, 20, 1.5, 'SUBSCRIPTION');

    // Surged fare must be strictly greater than base (1.5x, accounting for rounding)
    expect(surged.finalFare).toBeGreaterThan(base.finalFare);
    // Within ±1 rupee of 1.5× base (rounding may shift by ≤ 1)
    expect(surged.finalFare).toBeGreaterThanOrEqual(Math.floor(base.finalFare * 1.5));
    expect(surged.finalFare).toBeLessThanOrEqual(Math.ceil(base.finalFare * 1.5));
  });
});
