// ============================================================
// Chalo Backend — Ride Routes (Customer-facing)
// All ride lifecycle endpoints
// ============================================================

import { Router } from 'express';
import { rideController } from '../controllers/ride.controller';
import { authenticate, authorize } from '../middleware/auth';
import { validateBody, validateParams, validateQuery } from '../middleware/validate';
import { rideRateLimiter } from '../middleware/rateLimiter';
import {
  fareEstimateSchema,
  createRideSchema,
  scheduleRideSchema,
  rateRideSchema,
  cancelRideSchema,
  rideIdParamSchema,
  rideHistoryQuerySchema,
} from '../validators/ride.validator';

const router = Router();

// All ride routes require authentication
router.use(authenticate);

// -------------------------------------------------------
// Fare Estimation
// -------------------------------------------------------

router.post(
  '/fare-estimate',
  validateBody(fareEstimateSchema),
  rideController.getFareEstimate.bind(rideController)
);

// -------------------------------------------------------
// Ride Creation
// -------------------------------------------------------

// On-demand ride
router.post(
  '/',
  rideRateLimiter,
  authorize('CUSTOMER'),
  validateBody(createRideSchema),
  rideController.createRide.bind(rideController)
);

// Scheduled ride
router.post(
  '/schedule',
  rideRateLimiter,
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

// Ride details
router.get(
  '/:rideId',
  validateParams(rideIdParamSchema),
  rideController.getRideDetails.bind(rideController)
);

// -------------------------------------------------------
// Ride Actions
// -------------------------------------------------------

// Cancel ride
router.post(
  '/:rideId/cancel',
  authorize('CUSTOMER'),
  validateParams(rideIdParamSchema),
  validateBody(cancelRideSchema),
  rideController.cancelRide.bind(rideController)
);

// Rate ride
router.post(
  '/:rideId/rate',
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
  validateParams(rideIdParamSchema),
  rideController.triggerSOS.bind(rideController)
);

// Resolve SOS
router.post(
  '/sos/:sosAlertId/resolve',
  rideController.resolveSOS.bind(rideController)
);

export default router;
