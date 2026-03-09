// ============================================================
// Chalo Backend — Public Tracking Routes
// Shared family tracking links. No auth required.
// ============================================================

import { Router } from 'express';
import { rideController } from '../controllers/ride.controller';
import { validateParams } from '../middleware/validate';
import { trackTokenParamSchema } from '../validators/ride.validator';

const router = Router();

router.get(
  '/:token',
  validateParams(trackTokenParamSchema),
  rideController.getSharedTracking.bind(rideController)
);

export default router;