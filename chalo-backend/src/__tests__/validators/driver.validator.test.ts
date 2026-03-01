// ============================================================
// Tests — Driver Validators (Zod schemas)
// ============================================================

import {
  goOnlineSchema,
  updateLocationSchema,
  driverRideParamSchema,
  withdrawalIdParamSchema,
  declineRideSchema,
  completeRideSchema,
  earningsQuerySchema,
  tripHistoryQuerySchema,
  withdrawalSchema,
} from '../../validators/driver.validator';

// ─── goOnlineSchema ───────────────────────────────────────────
describe('goOnlineSchema', () => {
  it('accepts valid lat/lng', () => {
    expect(goOnlineSchema.safeParse({ lat: 28.47, lng: 77.40 }).success).toBe(true);
  });

  it('rejects lat > 90', () => {
    expect(goOnlineSchema.safeParse({ lat: 91, lng: 77 }).success).toBe(false);
  });

  it('rejects lat < -90', () => {
    expect(goOnlineSchema.safeParse({ lat: -91, lng: 77 }).success).toBe(false);
  });

  it('rejects lng > 180', () => {
    expect(goOnlineSchema.safeParse({ lat: 28, lng: 181 }).success).toBe(false);
  });

  it('rejects lng < -180', () => {
    expect(goOnlineSchema.safeParse({ lat: 28, lng: -181 }).success).toBe(false);
  });

  it('rejects missing lat', () => {
    expect(goOnlineSchema.safeParse({ lng: 77 }).success).toBe(false);
  });

  it('rejects missing lng', () => {
    expect(goOnlineSchema.safeParse({ lat: 28 }).success).toBe(false);
  });

  it('rejects string lat', () => {
    expect(goOnlineSchema.safeParse({ lat: '28.47', lng: 77 }).success).toBe(false);
  });

  it('accepts boundary values (lat=0, lng=0)', () => {
    expect(goOnlineSchema.safeParse({ lat: 0, lng: 0 }).success).toBe(true);
  });
});

// ─── updateLocationSchema ─────────────────────────────────────
describe('updateLocationSchema', () => {
  it('accepts valid Faridabad coordinates', () => {
    expect(updateLocationSchema.safeParse({ lat: 28.4089, lng: 77.3178 }).success).toBe(true);
  });

  it('rejects empty object', () => {
    expect(updateLocationSchema.safeParse({}).success).toBe(false);
  });
});

// ─── driverRideParamSchema ────────────────────────────────────
describe('driverRideParamSchema', () => {
  it('accepts valid CUID', () => {
    expect(driverRideParamSchema.safeParse({ rideId: 'clz1234567890abcdefghij' }).success).toBe(true);
  });

  it('rejects non-CUID string', () => {
    expect(driverRideParamSchema.safeParse({ rideId: 'not-a-cuid' }).success).toBe(false);
  });

  it('rejects empty string', () => {
    expect(driverRideParamSchema.safeParse({ rideId: '' }).success).toBe(false);
  });

  it('rejects missing rideId', () => {
    expect(driverRideParamSchema.safeParse({}).success).toBe(false);
  });
});

// ─── withdrawalIdParamSchema ──────────────────────────────────
describe('withdrawalIdParamSchema', () => {
  it('accepts valid CUID', () => {
    expect(withdrawalIdParamSchema.safeParse({ withdrawalId: 'clz1234567890abcdefghij' }).success).toBe(true);
  });

  it('rejects non-CUID', () => {
    expect(withdrawalIdParamSchema.safeParse({ withdrawalId: 'invalid' }).success).toBe(false);
  });
});

// ─── declineRideSchema ────────────────────────────────────────
describe('declineRideSchema', () => {
  it('accepts empty object (reason is optional)', () => {
    expect(declineRideSchema.safeParse({}).success).toBe(true);
  });

  it('accepts reason within 200 chars', () => {
    expect(declineRideSchema.safeParse({ reason: 'Too far from pickup' }).success).toBe(true);
  });

  it('rejects reason over 200 characters', () => {
    const longReason = 'x'.repeat(201);
    expect(declineRideSchema.safeParse({ reason: longReason }).success).toBe(false);
  });

  it('trims whitespace from reason', () => {
    const result = declineRideSchema.safeParse({ reason: '  Too far  ' });
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.reason).toBe('Too far');
    }
  });
});

// ─── completeRideSchema ───────────────────────────────────────
describe('completeRideSchema', () => {
  it('accepts empty object (note is optional)', () => {
    expect(completeRideSchema.safeParse({}).success).toBe(true);
  });

  it('accepts note within 500 chars', () => {
    expect(completeRideSchema.safeParse({ note: 'Great ride!' }).success).toBe(true);
  });

  it('rejects note over 500 characters', () => {
    expect(completeRideSchema.safeParse({ note: 'x'.repeat(501) }).success).toBe(false);
  });
});

// ─── earningsQuerySchema ──────────────────────────────────────
describe('earningsQuerySchema', () => {
  it('accepts valid period=today', () => {
    expect(earningsQuerySchema.safeParse({ period: 'today' }).success).toBe(true);
  });

  it('accepts valid period=week', () => {
    expect(earningsQuerySchema.safeParse({ period: 'week' }).success).toBe(true);
  });

  it('accepts valid period=month', () => {
    expect(earningsQuerySchema.safeParse({ period: 'month' }).success).toBe(true);
  });

  it('rejects invalid period', () => {
    expect(earningsQuerySchema.safeParse({ period: 'year' }).success).toBe(false);
  });

  it('defaults to today when period is missing', () => {
    const result = earningsQuerySchema.safeParse({});
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.period).toBe('today');
    }
  });

  it('coerces string page to number', () => {
    const result = earningsQuerySchema.safeParse({ period: 'today', page: '2', limit: '10' });
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.page).toBe(2);
    }
  });

  it('rejects page=0', () => {
    expect(earningsQuerySchema.safeParse({ period: 'today', page: 0 }).success).toBe(false);
  });
});

// ─── tripHistoryQuerySchema ───────────────────────────────────
describe('tripHistoryQuerySchema', () => {
  it('provides default page and limit', () => {
    const result = tripHistoryQuerySchema.safeParse({});
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.page).toBe(1);
      expect(typeof result.data.limit).toBe('number');
    }
  });

  it('coerces string limit to number', () => {
    const result = tripHistoryQuerySchema.safeParse({ limit: '25' });
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.limit).toBe(25);
    }
  });
});

// ─── withdrawalSchema ─────────────────────────────────────────
describe('withdrawalSchema', () => {
  it('accepts CASH_AGENT with no bank details', () => {
    expect(withdrawalSchema.safeParse({ amount: 500, method: 'CASH_AGENT' }).success).toBe(true);
  });

  it('accepts BANK_TRANSFER with valid UPI ID', () => {
    expect(
      withdrawalSchema.safeParse({ amount: 1000, method: 'BANK_TRANSFER', upiId: 'driver@paytm' }).success
    ).toBe(true);
  });

  it('accepts BANK_TRANSFER with account + IFSC', () => {
    expect(
      withdrawalSchema.safeParse({
        amount: 2000,
        method: 'BANK_TRANSFER',
        bankAccountNumber: '1234567890',
        bankIfsc: 'SBIN0001234',
      }).success
    ).toBe(true);
  });

  it('rejects BANK_TRANSFER with no payment details', () => {
    expect(
      withdrawalSchema.safeParse({ amount: 1000, method: 'BANK_TRANSFER' }).success
    ).toBe(false);
  });

  it('rejects BANK_TRANSFER with account but no IFSC', () => {
    expect(
      withdrawalSchema.safeParse({
        amount: 1000,
        method: 'BANK_TRANSFER',
        bankAccountNumber: '1234567890',
      }).success
    ).toBe(false);
  });

  it('rejects amount of 0', () => {
    expect(withdrawalSchema.safeParse({ amount: 0, method: 'CASH_AGENT' }).success).toBe(false);
  });

  it('rejects amount over ₹50,000', () => {
    expect(withdrawalSchema.safeParse({ amount: 50001, method: 'CASH_AGENT' }).success).toBe(false);
  });

  it('rejects invalid IFSC format', () => {
    expect(
      withdrawalSchema.safeParse({
        amount: 1000,
        method: 'BANK_TRANSFER',
        bankAccountNumber: '1234567890',
        bankIfsc: 'INVALID',
      }).success
    ).toBe(false);
  });

  it('rejects invalid UPI ID format', () => {
    expect(
      withdrawalSchema.safeParse({
        amount: 1000,
        method: 'BANK_TRANSFER',
        upiId: 'not-valid-upi',
      }).success
    ).toBe(false);
  });

  it('rejects negative amount', () => {
    expect(withdrawalSchema.safeParse({ amount: -100, method: 'CASH_AGENT' }).success).toBe(false);
  });

  it('rejects invalid method', () => {
    expect(withdrawalSchema.safeParse({ amount: 500, method: 'CRYPTO' }).success).toBe(false);
  });
});
