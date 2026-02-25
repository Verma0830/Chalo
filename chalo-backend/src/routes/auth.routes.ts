// ============================================================
// Chalo Backend — Auth Routes
// Public: OTP send/verify | Protected: profile, settings
// ============================================================

import { Router } from 'express';
import { authController } from '../controllers/auth.controller';
import { authenticate } from '../middleware/auth';
import { validateBody } from '../middleware/validate';
import { authRateLimiter } from '../middleware/rateLimiter';
import {
  sendOTPSchema,
  verifyOTPSchema,
  completeProfileSchema,
  updateEmergencyContactSchema,
} from '../validators/auth.validator';
import { savedLocationSchema } from '../validators/ride.validator';

const router = Router();

// -------------------------------------------------------
// Public Routes (no auth required)
// -------------------------------------------------------

// Send OTP — rate limited (5 per 15 mins)
router.post(
  '/otp/send',
  authRateLimiter,
  validateBody(sendOTPSchema),
  authController.sendOTP.bind(authController)
);

// Verify OTP
router.post(
  '/otp/verify',
  authRateLimiter,
  validateBody(verifyOTPSchema),
  authController.verifyOTP.bind(authController)
);

// -------------------------------------------------------
// Protected Routes (auth required)
// -------------------------------------------------------

// Get profile
router.get(
  '/profile',
  authenticate,
  authController.getProfile.bind(authController)
);

// Complete/update profile
router.put(
  '/profile',
  authenticate,
  validateBody(completeProfileSchema),
  authController.completeProfile.bind(authController)
);

// Update emergency contact
router.put(
  '/emergency-contact',
  authenticate,
  validateBody(updateEmergencyContactSchema),
  authController.updateEmergencyContact.bind(authController)
);

// Save home/work location
router.put(
  '/saved-location',
  authenticate,
  validateBody(savedLocationSchema),
  authController.updateSavedLocation.bind(authController)
);

export default router;
