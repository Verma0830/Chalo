// ============================================================
// Chalo Backend — Ride Routes (Customer-facing)
// All ride lifecycle endpoints
// ============================================================

import { Router } from 'express';
import express from 'express';
import { rideController } from '../controllers/ride.controller';
import { authenticate, authorize } from '../middleware/auth';
import { validateBody, validateParams, validateQuery } from '../middleware/validate';
import { rideRateLimiter } from '../middleware/rateLimiter';
import { idempotencyMiddleware } from '../middleware/idempotency';
import {
  fareEstimateSchema,
  createRideSchema,
  scheduleRideSchema,
  rateRideSchema,
  cancelRideSchema,
  rideIdParamSchema,
  rideHistoryQuerySchema,
  triggerSOSSchema,
  sosAlertIdParamSchema,
} from '../validators/ride.validator';

const router = Router();

// All ride routes require authentication
router.use(authenticate);

// -------------------------------------------------------
// Fare Estimation
// -------------------------------------------------------

router.post(
  '/fare-estimate',
  express.json({ limit: '10kb' }), // Strict body limit for fare estimates
  validateBody(fareEstimateSchema),
  rideController.getFareEstimate.bind(rideController)
);

// -------------------------------------------------------
// Ride Creation (with idempotency protection)
// -------------------------------------------------------

// On-demand ride
router.post(
  '/',
  express.json({ limit: '10kb' }), // Strict body limit
  rideRateLimiter,
  idempotencyMiddleware, // Prevent duplicate rides on retry
  authorize('CUSTOMER'),
  validateBody(createRideSchema),
  rideController.createRide.bind(rideController)
);

// Scheduled ride
router.post(
  '/schedule',
  express.json({ limit: '10kb' }),
  rideRateLimiter,
  idempotencyMiddleware,
  authorize('CUSTOMER'),
  validateBody(scheduleRideSchema),
  rideController.createScheduledRide.bind(rideController)
);

// -------------------------------------------------------
// Ride History & Details
// -------------------------------------------------------

// Ride history (paginated)
router.get(
  '/history',
  authorize('CUSTOMER'),
  validateQuery(rideHistoryQuerySchema),
  rideController.getRideHistory.bind(rideController)
);

// Scheduled rides
router.get(
  '/scheduled',
  authorize('CUSTOMER'),
  rideController.getScheduledRides.bind(rideController)
);

// Ride receipt (completed rides only)
router.get(
  '/:rideId/receipt',
  authorize('CUSTOMER'),
  validateParams(rideIdParamSchema),
  rideController.getRideReceipt.bind(rideController)
);

// Ride details
router.get(
  '/:rideId',
  validateParams(rideIdParamSchema),
  rideController.getRideDetails.bind(rideController)
);

// -------------------------------------------------------
// Ride Tracking (real-time location)
// -------------------------------------------------------

router.get(
  '/:rideId/location',
  validateParams(rideIdParamSchema),
  rideController.getRideLocation.bind(rideController)
);

// -------------------------------------------------------
// Ride Actions
// -------------------------------------------------------

// Cancel ride
router.post(
  '/:rideId/cancel',
  express.json({ limit: '5kb' }),
  authorize('CUSTOMER'),
  validateParams(rideIdParamSchema),
  validateBody(cancelRideSchema),
  rideController.cancelRide.bind(rideController)
);

router.post(
  '/:rideId/share',
  authorize('CUSTOMER'),
  validateParams(rideIdParamSchema),
  rideController.shareRide.bind(rideController)
);

// Skip rating (customer pressed Skip — never ask again)
router.post(
  '/:rideId/skip-rating',
  authorize('CUSTOMER'),
  validateParams(rideIdParamSchema),
  rideController.skipRating.bind(rideController)
);

// Rate ride
router.post(
  '/:rideId/rate',
  express.json({ limit: '5kb' }),
  authorize('CUSTOMER'),
  validateParams(rideIdParamSchema),
  validateBody(rateRideSchema),
  rideController.rateRide.bind(rideController)
);

// -------------------------------------------------------
// SOS
// -------------------------------------------------------

// Trigger SOS
router.post(
  '/:rideId/sos',
  express.json({ limit: '5kb' }),
  validateParams(rideIdParamSchema),
  validateBody(triggerSOSSchema),
  rideController.triggerSOS.bind(rideController)
);

// Resolve SOS
router.post(
  '/sos/:sosAlertId/resolve',
  express.json({ limit: '5kb' }),
  validateParams(sosAlertIdParamSchema),
  rideController.resolveSOS.bind(rideController)
);

export default router;
