// ============================================================
// Chalo Backend — Admin Routes
// All endpoints require a valid Firebase token AND ADMIN role
// ============================================================

import { Router, Request, Response, NextFunction } from 'express';
import { authenticate, authorize } from '../middleware/auth';
import { validateBody, validateParams, validateQuery } from '../middleware/validate';
import { adminController } from '../controllers/admin.controller';
import { ApiError } from '../utils/apiError';
import {
  adminPaginationSchema,
  adminDriverParamSchema,
  approveDriverSchema,
  rejectDriverSchema,
  configKeyParamSchema,
  updateConfigSchema,
  promoteAdminSchema,
} from '../validators/admin.validator';

const router = Router();

// -------------------------------------------------------
// Internal-only: promote first admin (no Firebase auth required)
// Protected by INTERNAL_API_KEY header instead of role check.
// -------------------------------------------------------
function requireInternalKey(req: Request, _res: Response, next: NextFunction) {
  const key = req.headers['x-internal-api-key'];
  if (!process.env.INTERNAL_API_KEY || key !== process.env.INTERNAL_API_KEY) {
    return next(ApiError.forbidden('Invalid or missing internal API key'));
  }
  return next();
}

// POST /api/v1/admin/promote
router.post(
  '/promote',
  requireInternalKey,
  validateBody(promoteAdminSchema),
  adminController.promoteToAdmin.bind(adminController)
);

// All other admin routes require authentication + ADMIN role
router.use(authenticate);
router.use(authorize('ADMIN'));

// -------------------------------------------------------
// Driver Management
// -------------------------------------------------------

// GET /api/v1/admin/drivers/pending
router.get(
  '/drivers/pending',
  validateQuery(adminPaginationSchema),
  adminController.listPendingDrivers.bind(adminController)
);

// GET /api/v1/admin/drivers/:driverId
router.get(
  '/drivers/:driverId',
  validateParams(adminDriverParamSchema),
  adminController.getDriverDetail.bind(adminController)
);

// POST /api/v1/admin/drivers/:driverId/approve
router.post(
  '/drivers/:driverId/approve',
  validateParams(adminDriverParamSchema),
  validateBody(approveDriverSchema),
  adminController.approveDriver.bind(adminController)
);

// POST /api/v1/admin/drivers/:driverId/reject
router.post(
  '/drivers/:driverId/reject',
  validateParams(adminDriverParamSchema),
  validateBody(rejectDriverSchema),
  adminController.rejectDriver.bind(adminController)
);

// POST /api/v1/admin/drivers/:driverId/auto-verify
router.post(
  '/drivers/:driverId/auto-verify',
  validateParams(adminDriverParamSchema),
  adminController.autoVerifyDriver.bind(adminController)
);

// -------------------------------------------------------
// Live Ride Monitoring
// -------------------------------------------------------

// GET /api/v1/admin/rides/live
router.get(
  '/rides/live',
  validateQuery(adminPaginationSchema),
  adminController.getLiveRides.bind(adminController)
);

// -------------------------------------------------------
// Platform Config
// -------------------------------------------------------

// GET /api/v1/admin/config
router.get('/config', adminController.getAllConfig.bind(adminController));

// PUT /api/v1/admin/config/:key
router.put(
  '/config/:key',
  validateParams(configKeyParamSchema),
  validateBody(updateConfigSchema),
  adminController.updateConfig.bind(adminController)
);

export default router;
