// ============================================================
// Tests — Ride Validators (Zod schemas)
// ============================================================

import {
  fareEstimateSchema,
  createRideSchema,
  scheduleRideSchema,
  rateRideSchema,
  cancelRideSchema,
} from '../../validators/ride.validator';
import CONSTANTS from '../../utils/constants';

// reusable valid location
const validPickup = { lat: 28.4744, lng: 77.4024, address: 'Sector 15, Faridabad' };
const validDrop   = { lat: 28.5000, lng: 77.4200, address: 'Sector 21, Faridabad' };

// ─── fareEstimateSchema ───────────────────────────────────────
describe('fareEstimateSchema', () => {
  it('accepts valid pickup and drop', () => {
    expect(fareEstimateSchema.safeParse({ pickup: validPickup, drop: validDrop }).success).toBe(true);
  });

  it('rejects lat > 90', () => {
    expect(
      fareEstimateSchema.safeParse({ pickup: { ...validPickup, lat: 91 }, drop: validDrop }).success
    ).toBe(false);
  });

  it('rejects lng < -180', () => {
    expect(
      fareEstimateSchema.safeParse({ pickup: validPickup, drop: { ...validDrop, lng: -181 } }).success
    ).toBe(false);
  });

  it('rejects empty address', () => {
    expect(
      fareEstimateSchema.safeParse({ pickup: { ...validPickup, address: 'AB' }, drop: validDrop }).success
    ).toBe(false);
  });

  it('rejects missing drop', () => {
    expect(fareEstimateSchema.safeParse({ pickup: validPickup }).success).toBe(false);
  });
});

// ─── createRideSchema ─────────────────────────────────────────
describe('createRideSchema', () => {
  it('accepts valid ride with CASH payment', () => {
    const result = createRideSchema.safeParse({
      pickup: validPickup,
      drop: validDrop,
      paymentMethod: 'CASH',
    });
    expect(result.success).toBe(true);
  });

  it('accepts valid ride with UPI payment', () => {
    const result = createRideSchema.safeParse({
      pickup: validPickup,
      drop: validDrop,
      paymentMethod: 'UPI',
    });
    expect(result.success).toBe(true);
  });

  it('rejects invalid payment method', () => {
    expect(
      createRideSchema.safeParse({
        pickup: validPickup,
        drop: validDrop,
        paymentMethod: 'CARD',
      }).success
    ).toBe(false);
  });

  it('rejects missing paymentMethod', () => {
    expect(
      createRideSchema.safeParse({ pickup: validPickup, drop: validDrop }).success
    ).toBe(false);
  });
});

// ─── scheduleRideSchema ───────────────────────────────────────
describe('scheduleRideSchema', () => {
  const futureDate = () => {
    const d = new Date();
    d.setHours(d.getHours() + 2);
    return d.toISOString();
  };

  const base = {
    pickup: validPickup,
    drop: validDrop,
    paymentMethod: 'CASH' as const,
  };

  it('accepts a valid future schedule', () => {
    expect(scheduleRideSchema.safeParse({ ...base, scheduledAt: futureDate() }).success).toBe(true);
  });

  it('rejects a past scheduledAt', () => {
    const past = new Date(Date.now() - 3_600_000).toISOString();
    expect(scheduleRideSchema.safeParse({ ...base, scheduledAt: past }).success).toBe(false);
  });

  it('rejects scheduledAt more than 7 days ahead', () => {
    const tooFar = new Date();
    tooFar.setDate(tooFar.getDate() + CONSTANTS.MAX_SCHEDULE_DAYS + 1);
    expect(
      scheduleRideSchema.safeParse({ ...base, scheduledAt: tooFar.toISOString() }).success
    ).toBe(false);
  });

  it('rejects invalid ISO string', () => {
    expect(
      scheduleRideSchema.safeParse({ ...base, scheduledAt: 'not-a-date' }).success
    ).toBe(false);
  });
});

// ─── rateRideSchema ───────────────────────────────────────────
describe('rateRideSchema', () => {
  it('accepts rating 1–5', () => {
    for (let r = 1; r <= 5; r++) {
      expect(rateRideSchema.safeParse({ rating: r }).success).toBe(true);
    }
  });

  it('rejects rating 0', () => {
    expect(rateRideSchema.safeParse({ rating: 0 }).success).toBe(false);
  });

  it('rejects rating 6', () => {
    expect(rateRideSchema.safeParse({ rating: 6 }).success).toBe(false);
  });

  it('rejects non-integer rating', () => {
    expect(rateRideSchema.safeParse({ rating: 4.5 }).success).toBe(false);
  });

  it('accepts optional comment', () => {
    expect(rateRideSchema.safeParse({ rating: 5, comment: 'Great ride!' }).success).toBe(true);
  });

  it('rejects comment > 500 chars', () => {
    expect(
      rateRideSchema.safeParse({ rating: 3, comment: 'A'.repeat(501) }).success
    ).toBe(false);
  });
});

// ─── cancelRideSchema ─────────────────────────────────────────
describe('cancelRideSchema', () => {
  it('accepts empty object (reason is optional)', () => {
    expect(cancelRideSchema.safeParse({}).success).toBe(true);
  });

  it('accepts valid reason', () => {
    expect(cancelRideSchema.safeParse({ reason: 'Change of plans' }).success).toBe(true);
  });

  it('rejects reason > 500 chars', () => {
    expect(cancelRideSchema.safeParse({ reason: 'X'.repeat(501) }).success).toBe(false);
  });
});
