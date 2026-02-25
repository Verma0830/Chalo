// ============================================================
// Tests — Helper Utilities
// Covers: haversine, fare calc, surge, commission, pagination,
//         OTP generation, phone masking, schedule validation
// ============================================================

import {
  haversineDistance,
  calculateBaseFare,
  applySurge,
  calculateCommission,
  calculateSettlementDate,
  parsePagination,
  paginationToSkip,
  generateOTP,
  maskPhone,
  formatCurrency,
  isValidScheduleTime,
  isDriverNearPickup,
} from '../../utils/helpers';
import CONSTANTS from '../../utils/constants';

// ─── haversineDistance ────────────────────────────────────────
describe('haversineDistance', () => {
  it('returns 0 for identical coordinates', () => {
    expect(haversineDistance(28.4744, 77.4024, 28.4744, 77.4024)).toBe(0);
  });

  it('calculates known distance correctly (approx Delhi → Faridabad ~25 km)', () => {
    // New Delhi ≈ (28.7041, 77.1025), Faridabad ≈ (28.4089, 77.3178)
    const dist = haversineDistance(28.7041, 77.1025, 28.4089, 77.3178);
    expect(dist).toBeGreaterThan(25);
    expect(dist).toBeLessThan(40);
  });

  it('is symmetric — A→B equals B→A', () => {
    const d1 = haversineDistance(28.4744, 77.4024, 28.5, 77.45);
    const d2 = haversineDistance(28.5, 77.45, 28.4744, 77.4024);
    expect(d1).toBe(d2);
  });

  it('returns a 2-decimal precision number', () => {
    const dist = haversineDistance(28.4744, 77.4024, 28.5, 77.45);
    expect(dist.toString().split('.')[1]?.length ?? 0).toBeLessThanOrEqual(2);
  });
});

// ─── calculateBaseFare ───────────────────────────────────────
describe('calculateBaseFare', () => {
  it('calculates correct fare: 5 km, 15 min with defaults', () => {
    // 5 * 12 + 15 * 2 + 5 (booking fee) = 60 + 30 + 5 = 95
    const fare = calculateBaseFare(5, 15);
    expect(fare).toBe(95);
  });

  it('never returns less than MIN_FARE (₹30)', () => {
    // 0.1 km, 0 min → fare = 1.2 + 0 + 5 = 6.2 → clamped to 30
    const fare = calculateBaseFare(0.1, 0);
    expect(fare).toBe(CONSTANTS.MIN_FARE);
  });

  it('accepts custom per-km and per-min rates', () => {
    // 2 km * ₹10 + 10 min * ₹3 + ₹5 = 20 + 30 + 5 = 55
    const fare = calculateBaseFare(2, 10, 10, 3);
    expect(fare).toBe(55);
  });

  it('includes the booking fee', () => {
    // 0 km, 0 min → only booking fee → clamped to MIN_FARE
    const fare = calculateBaseFare(0, 0);
    expect(fare).toBe(CONSTANTS.MIN_FARE);
  });
});

// ─── applySurge ──────────────────────────────────────────────
describe('applySurge', () => {
  it('applies 1x multiplier (no surge)', () => {
    expect(applySurge(100, 1.0)).toBe(100);
  });

  it('applies 1.5x surge correctly', () => {
    expect(applySurge(100, 1.5)).toBe(150);
  });

  it('clamps multiplier to max 2.0', () => {
    expect(applySurge(100, 5.0)).toBe(200); // capped at SURGE_MAX_MULTIPLIER
  });

  it('clamps multiplier to min 1.0', () => {
    expect(applySurge(100, 0.5)).toBe(100); // floored at SURGE_MIN_MULTIPLIER
  });

  it('returns rounded integer', () => {
    const result = applySurge(99, 1.3); // 99 * 1.3 = 128.7 → 129
    expect(result).toBe(129);
    expect(Number.isInteger(result)).toBe(true);
  });
});

// ─── calculateCommission ─────────────────────────────────────
describe('calculateCommission', () => {
  it('calculates 15% commission on ₹100', () => {
    expect(calculateCommission(100, 15)).toBe(15);
  });

  it('calculates 15% commission on ₹95 (real fare)', () => {
    // 95 * 15 / 100 = 14.25 → rounds to 14
    expect(calculateCommission(95, 15)).toBe(14);
  });

  it('returns 0 commission on 0 fare', () => {
    expect(calculateCommission(0, 15)).toBe(0);
  });

  it('handles non-integer commission percentages', () => {
    // 100 * 12.5 / 100 = 12.5 → rounds to 13
    expect(calculateCommission(100, 12.5)).toBe(13);
  });
});

// ─── calculateSettlementDate ─────────────────────────────────
describe('calculateSettlementDate', () => {
  it('defaults to T+2 days', () => {
    const now = new Date('2025-01-01T12:00:00Z');
    const dueDate = calculateSettlementDate(now);
    expect(dueDate.getDate()).toBe(3); // Jan 1 + 2 = Jan 3
  });

  it('respects custom settlement days', () => {
    const now = new Date('2025-06-10T00:00:00Z');
    const dueDate = calculateSettlementDate(now, 3);
    expect(dueDate.getDate()).toBe(13); // Jun 10 + 3 = Jun 13
  });

  it('does not mutate the original date', () => {
    const original = new Date('2025-03-15T00:00:00Z');
    const originalTime = original.getTime();
    calculateSettlementDate(original);
    expect(original.getTime()).toBe(originalTime);
  });
});

// ─── parsePagination ─────────────────────────────────────────
describe('parsePagination', () => {
  it('returns defaults when no query params provided', () => {
    const result = parsePagination({});
    expect(result).toEqual({ page: 1, limit: 20 });
  });

  it('parses valid page and limit', () => {
    const result = parsePagination({ page: '3', limit: '10' });
    expect(result).toEqual({ page: 3, limit: 10 });
  });

  it('clamps page to minimum 1', () => {
    const result = parsePagination({ page: '-5' });
    expect(result.page).toBe(1);
  });

  it('clamps limit to MAX_LIMIT (100)', () => {
    const result = parsePagination({ limit: '999' });
    expect(result.limit).toBe(CONSTANTS.MAX_LIMIT);
  });

  it('falls back to default limit when limit is 0 (falsy)', () => {
    // 0 is falsy: `0 || DEFAULT_LIMIT` → 20 (intentional API behaviour)
    const result = parsePagination({ limit: '0' });
    expect(result.limit).toBe(CONSTANTS.DEFAULT_LIMIT);
  });
});

// ─── paginationToSkip ────────────────────────────────────────
describe('paginationToSkip', () => {
  it('returns 0 for page 1', () => {
    expect(paginationToSkip({ page: 1, limit: 20 })).toBe(0);
  });

  it('returns correct skip for page 2, limit 20', () => {
    expect(paginationToSkip({ page: 2, limit: 20 })).toBe(20);
  });

  it('returns correct skip for page 5, limit 10', () => {
    expect(paginationToSkip({ page: 5, limit: 10 })).toBe(40);
  });
});

// ─── generateOTP ─────────────────────────────────────────────
describe('generateOTP', () => {
  it('generates a 4-digit OTP by default', () => {
    const otp = generateOTP();
    expect(otp).toHaveLength(4);
    expect(/^\d{4}$/.test(otp)).toBe(true);
  });

  it('generates OTP of requested length', () => {
    const otp = generateOTP(6);
    expect(otp).toHaveLength(6);
    expect(/^\d{6}$/.test(otp)).toBe(true);
  });

  it('always returns >= 1000 for 4-digit OTP', () => {
    for (let i = 0; i < 50; i++) {
      const otp = parseInt(generateOTP(4), 10);
      expect(otp).toBeGreaterThanOrEqual(1000);
      expect(otp).toBeLessThanOrEqual(9999);
    }
  });
});

// ─── maskPhone ───────────────────────────────────────────────
describe('maskPhone', () => {
  it('masks middle digits of a full phone number', () => {
    // '+919876543210' (len 13): first 5 chars shown, last 4 shown
    expect(maskPhone('+919876543210')).toBe('+9198****3210');
  });

  it('returns short strings unchanged', () => {
    expect(maskPhone('123')).toBe('123');
  });

  it('always exposes last 4 digits', () => {
    const masked = maskPhone('+919812345678');
    expect(masked.endsWith('5678')).toBe(true);
  });
});

// ─── formatCurrency ──────────────────────────────────────────
describe('formatCurrency', () => {
  it('formats ₹95 correctly', () => {
    expect(formatCurrency(95)).toBe('₹95');
  });

  it('rounds decimals', () => {
    expect(formatCurrency(95.7)).toBe('₹96');
  });

  it('includes rupee symbol', () => {
    expect(formatCurrency(0)).toBe('₹0');
  });
});

// ─── isValidScheduleTime ─────────────────────────────────────
describe('isValidScheduleTime', () => {
  it('returns false for past time', () => {
    const past = new Date(Date.now() - 60_000);
    expect(isValidScheduleTime(past)).toBe(false);
  });

  it('returns true for 1 hour from now', () => {
    const future = new Date(Date.now() + 3_600_000);
    expect(isValidScheduleTime(future)).toBe(true);
  });

  it('returns false for more than 7 days ahead', () => {
    const tooFar = new Date();
    tooFar.setDate(tooFar.getDate() + 8);
    expect(isValidScheduleTime(tooFar)).toBe(false);
  });

  it('returns true for exactly 7 days ahead (boundary)', () => {
    const boundary = new Date();
    boundary.setDate(boundary.getDate() + CONSTANTS.MAX_SCHEDULE_DAYS);
    boundary.setSeconds(boundary.getSeconds() - 1);
    expect(isValidScheduleTime(boundary)).toBe(true);
  });
});

// ─── isDriverNearPickup ──────────────────────────────────────
describe('isDriverNearPickup', () => {
  it('returns true when driver is within 200m of pickup', () => {
    // Same point → 0 distance
    expect(isDriverNearPickup(28.4744, 77.4024, 28.4744, 77.4024)).toBe(true);
  });

  it('returns false when driver is 1 km away from pickup', () => {
    // Approximately 1 km shift in latitude
    expect(isDriverNearPickup(28.4744, 77.4024, 28.4839, 77.4024)).toBe(false);
  });
});
