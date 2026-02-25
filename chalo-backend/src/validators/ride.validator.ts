// ============================================================
// Chalo Backend — Ride Validators (Zod Schemas)
// ============================================================

import { z } from 'zod';
import CONSTANTS from '../utils/constants';

/**
 * Location object — reused in pickup and drop
 */
const locationSchema = z.object({
  lat: z.number().min(-90).max(90),
  lng: z.number().min(-180).max(180),
  address: z.string().trim().min(3, 'Address too short').max(500, 'Address too long'),
});

/**
 * Get fare estimate — before booking
 */
export const fareEstimateSchema = z.object({
  pickup: locationSchema,
  drop: locationSchema,
});

/**
 * Create a ride (on-demand)
 */
export const createRideSchema = z.object({
  pickup: locationSchema,
  drop: locationSchema,
  paymentMethod: z.enum(['CASH', 'UPI'], { message: 'Payment method must be CASH or UPI' }),
});

/**
 * Schedule a ride
 */
export const scheduleRideSchema = z.object({
  pickup: locationSchema,
  drop: locationSchema,
  paymentMethod: z.enum(['CASH', 'UPI']),
  scheduledAt: z
    .string()
    .datetime({ message: 'scheduledAt must be a valid ISO 8601 datetime' })
    .transform((val) => new Date(val))
    .refine((date) => {
      const now = new Date();
      return date > now;
    }, 'Scheduled time must be in the future')
    .refine((date) => {
      const maxDate = new Date();
      maxDate.setDate(maxDate.getDate() + CONSTANTS.MAX_SCHEDULE_DAYS);
      return date <= maxDate;
    }, `Cannot schedule more than ${CONSTANTS.MAX_SCHEDULE_DAYS} days ahead`),
});

/**
 * Rate a completed ride
 */
export const rateRideSchema = z.object({
  rating: z
    .number()
    .int()
    .min(CONSTANTS.MIN_RATING, `Rating must be at least ${CONSTANTS.MIN_RATING}`)
    .max(CONSTANTS.MAX_RATING, `Rating must be at most ${CONSTANTS.MAX_RATING}`),
  comment: z
    .string()
    .trim()
    .max(500, 'Comment too long')
    .optional(),
});

/**
 * Cancel a ride
 */
export const cancelRideSchema = z.object({
  reason: z
    .string()
    .trim()
    .max(500, 'Reason too long')
    .optional(),
});

/**
 * Ride ID param validation
 */
export const rideIdParamSchema = z.object({
  rideId: z.string().cuid('Invalid ride ID'),
});

/**
 * SOS alert ID param validation
 */
export const sosAlertIdParamSchema = z.object({
  sosAlertId: z.string().cuid('Invalid SOS alert ID'),
});

/**
 * Trigger SOS body validation
 */
export const triggerSOSSchema = z.object({
  lat: z.number().min(-90).max(90),
  lng: z.number().min(-180).max(180),
});

/**
 * Ride history query params
 */
export const rideHistoryQuerySchema = z.object({
  page: z.coerce.number().int().min(1).default(CONSTANTS.DEFAULT_PAGE),
  limit: z.coerce.number().int().min(1).max(CONSTANTS.MAX_LIMIT).default(CONSTANTS.DEFAULT_LIMIT),
  status: z.enum(['COMPLETED', 'CANCELLED', 'ALL']).default('ALL'),
});

/**
 * Saved location update
 */
export const savedLocationSchema = z.object({
  type: z.enum(['home', 'work']),
  lat: z.number().min(-90).max(90),
  lng: z.number().min(-180).max(180),
  address: z.string().trim().min(3).max(500),
});

export type FareEstimateInput = z.infer<typeof fareEstimateSchema>;
export type CreateRideInput = z.infer<typeof createRideSchema>;
export type ScheduleRideInput = z.infer<typeof scheduleRideSchema>;
export type RateRideInput = z.infer<typeof rateRideSchema>;
export type CancelRideInput = z.infer<typeof cancelRideSchema>;
export type RideHistoryQuery = z.infer<typeof rideHistoryQuerySchema>;
export type SavedLocationInput = z.infer<typeof savedLocationSchema>;
