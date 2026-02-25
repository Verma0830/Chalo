// ============================================================
// Tests — Payment Validators (Zod schemas)
// ============================================================

import {
  createPaymentOrderSchema,
  verifyPaymentSchema,
  confirmCashPaymentSchema,
} from '../../validators/payment.validator';

// A valid CUID for testing
const validCUID = 'clh7z2r3k0000356ohb5q8v7w';

// ─── createPaymentOrderSchema ─────────────────────────────────
describe('createPaymentOrderSchema', () => {
  it('accepts a valid CUID ride ID', () => {
    expect(createPaymentOrderSchema.safeParse({ rideId: validCUID }).success).toBe(true);
  });

  it('rejects an invalid ride ID format', () => {
    expect(createPaymentOrderSchema.safeParse({ rideId: 'not-a-cuid' }).success).toBe(false);
  });

  it('rejects empty string', () => {
    expect(createPaymentOrderSchema.safeParse({ rideId: '' }).success).toBe(false);
  });

  it('rejects missing rideId', () => {
    expect(createPaymentOrderSchema.safeParse({}).success).toBe(false);
  });
});

// ─── verifyPaymentSchema ──────────────────────────────────────
describe('verifyPaymentSchema', () => {
  const valid = {
    razorpayPaymentId: 'pay_123',
    razorpayOrderId: 'order_456',
    razorpaySignature: 'sig_789',
    rideId: validCUID,
  };

  it('accepts valid payment verification payload', () => {
    expect(verifyPaymentSchema.safeParse(valid).success).toBe(true);
  });

  it('rejects empty razorpayPaymentId', () => {
    expect(
      verifyPaymentSchema.safeParse({ ...valid, razorpayPaymentId: '' }).success
    ).toBe(false);
  });

  it('rejects empty razorpayOrderId', () => {
    expect(
      verifyPaymentSchema.safeParse({ ...valid, razorpayOrderId: '' }).success
    ).toBe(false);
  });

  it('rejects empty razorpaySignature', () => {
    expect(
      verifyPaymentSchema.safeParse({ ...valid, razorpaySignature: '' }).success
    ).toBe(false);
  });

  it('rejects invalid rideId', () => {
    expect(
      verifyPaymentSchema.safeParse({ ...valid, rideId: 'bad-id' }).success
    ).toBe(false);
  });

  it('rejects payload missing a field', () => {
    const { razorpaySignature: _sig, ...missing } = valid;
    expect(verifyPaymentSchema.safeParse(missing).success).toBe(false);
  });
});

// ─── confirmCashPaymentSchema ─────────────────────────────────
describe('confirmCashPaymentSchema', () => {
  it('accepts valid CUID', () => {
    expect(confirmCashPaymentSchema.safeParse({ rideId: validCUID }).success).toBe(true);
  });

  it('rejects invalid ride ID', () => {
    expect(confirmCashPaymentSchema.safeParse({ rideId: '12345' }).success).toBe(false);
  });
});
