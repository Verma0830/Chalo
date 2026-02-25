// ============================================================
// Chalo Backend — Payment Validators (Zod Schemas)
// ============================================================

import { z } from 'zod';

/**
 * Create Razorpay order (for UPI payments)
 */
export const createPaymentOrderSchema = z.object({
  rideId: z.string().cuid('Invalid ride ID'),
});

/**
 * Verify Razorpay payment
 */
export const verifyPaymentSchema = z.object({
  razorpayPaymentId: z.string().min(1, 'Payment ID is required'),
  razorpayOrderId: z.string().min(1, 'Order ID is required'),
  razorpaySignature: z.string().min(1, 'Signature is required'),
  rideId: z.string().cuid('Invalid ride ID'),
});

/**
 * Confirm cash payment (driver confirms cash collected)
 */
export const confirmCashPaymentSchema = z.object({
  rideId: z.string().cuid('Invalid ride ID'),
});

export type CreatePaymentOrderInput = z.infer<typeof createPaymentOrderSchema>;
export type VerifyPaymentInput = z.infer<typeof verifyPaymentSchema>;
export type ConfirmCashPaymentInput = z.infer<typeof confirmCashPaymentSchema>;
