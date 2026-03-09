// ============================================================
// Chalo Backend — Route Registry
// All routes registered here, mounted in app.ts
// ============================================================

import { Router } from 'express';
import authRoutes from './auth.routes';
import rideRoutes from './ride.routes';
import driverRoutes from './driver.routes';
import paymentRoutes from './payment.routes';
import notificationRoutes from './notification.routes';
import adminRoutes from './admin.routes';
import trackRoutes from './track.routes';

const router = Router();

// -------------------------------------------------------
// API Routes — /api/v1/*
// -------------------------------------------------------

router.use('/auth', authRoutes);
router.use('/track', trackRoutes);
router.use('/rides', rideRoutes);
router.use('/driver', driverRoutes);
router.use('/payments', paymentRoutes);
router.use('/notifications', notificationRoutes);
router.use('/admin', adminRoutes);

export default router;
