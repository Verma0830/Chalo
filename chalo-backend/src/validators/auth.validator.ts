// ============================================================
// Chalo Backend — Auth Validators (Zod Schemas)
// ============================================================

import { z } from 'zod';

/**
 * Send OTP request
 */
export const sendOTPSchema = z.object({
  phone: z
    .string()
    .trim()
    .regex(/^\+91[6-9]\d{9}$/, 'Invalid Indian mobile number. Format: +91XXXXXXXXXX'),
});

/**
 * Verify OTP request
 */
export const verifyOTPSchema = z.object({
  phone: z
    .string()
    .trim()
    .regex(/^\+91[6-9]\d{9}$/, 'Invalid Indian mobile number'),
  otp: z
    .string()
    .length(4, 'OTP must be exactly 4 digits')
    .regex(/^\d{4}$/, 'OTP must contain only digits'),
});

/**
 * Complete profile (after first OTP verification)
 */
export const completeProfileSchema = z.object({
  name: z
    .string()
    .trim()
    .min(2, 'Name must be at least 2 characters')
    .max(100, 'Name too long'),
  email: z
    .string()
    .email('Invalid email address')
    .optional()
    .or(z.literal('')),
  languagePref: z
    .enum(['pa', 'en'], { message: 'Language must be "pa" (Punjabi) or "en" (English)' })
    .default('pa'),
});

/**
 * Update emergency contact
 */
export const updateEmergencyContactSchema = z.object({
  emergencyContactName: z
    .string()
    .trim()
    .min(2, 'Name must be at least 2 characters')
    .max(100, 'Name too long'),
  emergencyContactPhone: z
    .string()
    .trim()
    .regex(/^\+91[6-9]\d{9}$/, 'Invalid Indian mobile number'),
});

/**
 * Register FCM device token
 */
export const registerDeviceTokenSchema = z.object({
  fcmToken: z
    .string()
    .trim()
    .min(1, 'FCM token is required')
    .max(512, 'FCM token too long'),
});

/**
 * Driver registration — verifies OTP + creates driver account in one step
 */
export const registerDriverSchema = z.object({
  phone: z
    .string()
    .trim()
    .regex(/^\+91[6-9]\d{9}$/, 'Invalid Indian mobile number. Format: +91XXXXXXXXXX'),
  otp: z
    .string()
    .length(4, 'OTP must be exactly 4 digits')
    .regex(/^\d{4}$/, 'OTP must contain only digits'),
  name: z
    .string()
    .trim()
    .min(2, 'Name must be at least 2 characters')
    .max(100, 'Name too long'),
});

export type SendOTPInput = z.infer<typeof sendOTPSchema>;
export type VerifyOTPInput = z.infer<typeof verifyOTPSchema>;
export type CompleteProfileInput = z.infer<typeof completeProfileSchema>;
export type UpdateEmergencyContactInput = z.infer<typeof updateEmergencyContactSchema>;
export type RegisterDeviceTokenInput = z.infer<typeof registerDeviceTokenSchema>;
export type RegisterDriverInput = z.infer<typeof registerDriverSchema>;
