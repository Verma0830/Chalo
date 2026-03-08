// ============================================================
// Chalo Backend — Driver Routes
// All routes require Firebase auth + DRIVER role.
// Validation middleware runs before controllers — services receive clean data.
// ============================================================

import { Router } from 'express';
import { authenticate, authorize } from '../middleware/auth';
import { validateBody, validateParams, validateQuery } from '../middleware/validate';
import { driverController } from '../controllers/driver.controller';
import {
  goOnlineSchema,
  updateLocationSchema,
  driverRideParamSchema,
  withdrawalIdParamSchema,
  declineRideSchema,
  startRideSchema,
  completeRideSchema,
  rateCustomerSchema,
  earningsSummaryQuerySchema,
  earningsQuerySchema,
  tripHistoryQuerySchema,
  withdrawalSchema,
  submitDocumentsSchema,
} from '../validators/driver.validator';

const router = Router();

// All driver routes require a valid Firebase token AND DRIVER role
router.use(authenticate);
router.use(authorize('DRIVER'));

// -----------------------------------------------------------------------
// Document Submission
// -----------------------------------------------------------------------

// POST /api/v1/driver/documents
router.post(
  '/documents',
  validateBody(submitDocumentsSchema),
  driverController.submitDocuments.bind(driverController)
);

// -----------------------------------------------------------------------
// Online / Offline
// -----------------------------------------------------------------------

// POST /api/v1/driver/go-online
router.post(
  '/go-online',
  validateBody(goOnlineSchema),
  driverController.goOnline.bind(driverController)
);

// POST /api/v1/driver/go-offline
router.post(
  '/go-offline',
  driverController.goOffline.bind(driverController)
);

// GET /api/v1/driver/status
router.get(
  '/status',
  driverController.getStatus.bind(driverController)
);

// -----------------------------------------------------------------------
// Location
// -----------------------------------------------------------------------

// POST /api/v1/driver/location
router.post(
  '/location',
  validateBody(updateLocationSchema),
  driverController.updateLocation.bind(driverController)
);

// -----------------------------------------------------------------------
// Incoming Ride
// -----------------------------------------------------------------------

// GET /api/v1/driver/rides/incoming
// Must be registered BEFORE /rides/:rideId routes to avoid param collision
router.get(
  '/rides/incoming',
  driverController.getIncomingRide.bind(driverController)
);

// -----------------------------------------------------------------------
// Ride Lifecycle
// -----------------------------------------------------------------------

// POST /api/v1/driver/rides/:rideId/accept
router.post(
  '/rides/:rideId/accept',
  validateParams(driverRideParamSchema),
  driverController.acceptRide.bind(driverController)
);

// POST /api/v1/driver/rides/:rideId/decline
router.post(
  '/rides/:rideId/decline',
  validateParams(driverRideParamSchema),
  validateBody(declineRideSchema),
  driverController.declineRide.bind(driverController)
);

// POST /api/v1/driver/rides/:rideId/arrived
router.post(
  '/rides/:rideId/arrived',
  validateParams(driverRideParamSchema),
  driverController.arrivedAtPickup.bind(driverController)
);

// POST /api/v1/driver/rides/:rideId/start
router.post(
  '/rides/:rideId/start',
  validateParams(driverRideParamSchema),
  validateBody(startRideSchema),
  driverController.startRide.bind(driverController)
);

// POST /api/v1/driver/rides/:rideId/complete
router.post(
  '/rides/:rideId/complete',
  validateParams(driverRideParamSchema),
  validateBody(completeRideSchema),
  driverController.completeRide.bind(driverController)
);

// POST /api/v1/driver/rides/:rideId/cancel
router.post(
  '/rides/:rideId/cancel',
  validateParams(driverRideParamSchema),
  validateBody(declineRideSchema),
  driverController.cancelRide.bind(driverController)
);

// POST /api/v1/driver/rides/:rideId/rate-customer
router.post(
  '/rides/:rideId/rate-customer',
  validateParams(driverRideParamSchema),
  validateBody(rateCustomerSchema),
  driverController.rateCustomer.bind(driverController)
);

// -----------------------------------------------------------------------
// Trip History
// -----------------------------------------------------------------------

// GET /api/v1/driver/trips
router.get(
  '/trips',
  validateQuery(tripHistoryQuerySchema),
  driverController.getTripHistory.bind(driverController)
);

// -----------------------------------------------------------------------
// Earnings
// -----------------------------------------------------------------------

// GET /api/v1/driver/earnings/summary (before /earnings to avoid collision)
router.get(
  '/earnings/summary',
  validateQuery(earningsSummaryQuerySchema),
  driverController.getEarningsSummary.bind(driverController)
);

// GET /api/v1/driver/earnings/settlement  (before /earnings to avoid collision)
router.get(
  '/earnings/settlement',
  driverController.getSettlementSummary.bind(driverController)
);

// GET /api/v1/driver/earnings
router.get(
  '/earnings',
  validateQuery(earningsQuerySchema),
  driverController.getEarnings.bind(driverController)
);

// -----------------------------------------------------------------------
// Withdrawals
// -----------------------------------------------------------------------

// POST /api/v1/driver/withdrawals
router.post(
  '/withdrawals',
  validateBody(withdrawalSchema),
  driverController.requestWithdrawal.bind(driverController)
);

// GET /api/v1/driver/withdrawals/:withdrawalId
router.get(
  '/withdrawals/:withdrawalId',
  validateParams(withdrawalIdParamSchema),
  driverController.getWithdrawalStatus.bind(driverController)
);

export default router;
