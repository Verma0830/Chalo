// ============================================================
// Chalo Backend — Driver Validators (Zod Schemas)
// ============================================================

import { z } from 'zod';
import CONSTANTS from '../utils/constants';

/**
 * Coordinate schemas — shared across location-bearing endpoints
 */
const latSchema = z.number().min(-90).max(90, 'Latitude must be between -90 and 90');
const lngSchema = z.number().min(-180).max(180, 'Longitude must be between -180 and 180');

/**
 * Go online — driver must provide current GPS location
 */
export const goOnlineSchema = z.object({
  lat: latSchema,
  lng: lngSchema,
});

/**
 * Update location — same as go-online, separate endpoint for polling clarity
 */
export const updateLocationSchema = z.object({
  lat: latSchema,
  lng: lngSchema,
});

/**
 * Ride ID param (used on multiple ride lifecycle endpoints)
 */
export const driverRideParamSchema = z.object({
  rideId: z.string().cuid('Invalid ride ID'),
});

/**
 * Withdrawal ID param
 */
export const withdrawalIdParamSchema = z.object({
  withdrawalId: z.string().cuid('Invalid withdrawal ID'),
});

/**
 * Decline a ride request — optional reason
 */
export const declineRideSchema = z.object({
  reason: z.string().trim().max(200, 'Reason too long').optional(),
});

/**
 * Complete ride — optional note from driver
 */
export const completeRideSchema = z.object({
  note: z.string().trim().max(500).optional(),
});

/**
 * Earnings query — period filter
 */
export const earningsQuerySchema = z.object({
  period: z.enum(['today', 'week', 'month']).default('today'),
  page: z
    .coerce.number().int().min(1)
    .default(CONSTANTS.DEFAULT_PAGE),
  limit: z
    .coerce.number().int().min(1).max(CONSTANTS.MAX_LIMIT)
    .default(CONSTANTS.DEFAULT_LIMIT),
});

/**
 * Trip history query
 */
export const tripHistoryQuerySchema = z.object({
  page: z
    .coerce.number().int().min(1)
    .default(CONSTANTS.DEFAULT_PAGE),
  limit: z
    .coerce.number().int().min(1).max(CONSTANTS.MAX_LIMIT)
    .default(CONSTANTS.DEFAULT_LIMIT),
});

/**
 * Request a withdrawal payout
 * Conditional validation: BANK_TRANSFER requires upiId OR (bankAccountNumber + bankIfsc)
 */
export const withdrawalSchema = z
  .object({
    amount: z
      .number()
      .positive('Amount must be greater than zero')
      .max(50000, 'Maximum single withdrawal is ₹50,000'),
    method: z.enum(['BANK_TRANSFER', 'CASH_AGENT']),
    // Bank transfer details (optional at schema level — validated in refine)
    bankAccountNumber: z.string().trim().min(8).max(20).optional(),
    bankIfsc: z
      .string()
      .trim()
      .regex(/^[A-Z]{4}0[A-Z0-9]{6}$/, 'Invalid IFSC code format')
      .optional(),
    upiId: z
      .string()
      .trim()
      .regex(/^[\w.\-_]{2,256}@[a-zA-Z]{2,64}$/, 'Invalid UPI ID format')
      .optional(),
  })
  .refine(
    (data) => {
      if (data.method === 'BANK_TRANSFER') {
        return (
          !!data.upiId || (!!data.bankAccountNumber && !!data.bankIfsc)
        );
      }
      return true;
    },
    {
      message:
        'Bank transfer requires either a UPI ID or bank account number + IFSC code',
      path: ['method'],
    }
  );

// -------------------------------------------------------
// Inferred types — controllers cast validated req.body to these
// -------------------------------------------------------

export type GoOnlineInput = z.infer<typeof goOnlineSchema>;
export type UpdateLocationInput = z.infer<typeof updateLocationSchema>;
export type DriverRideParam = z.infer<typeof driverRideParamSchema>;
export type WithdrawalIdParam = z.infer<typeof withdrawalIdParamSchema>;
export type DeclineRideInput = z.infer<typeof declineRideSchema>;
export type CompleteRideInput = z.infer<typeof completeRideSchema>;
export type EarningsQuery = z.infer<typeof earningsQuerySchema>;
export type TripHistoryQuery = z.infer<typeof tripHistoryQuerySchema>;
export type WithdrawalInput = z.infer<typeof withdrawalSchema>;
