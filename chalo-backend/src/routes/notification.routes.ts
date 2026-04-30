// ============================================================
// Chalo Backend — Notification Routes
// ============================================================

import { Router } from 'express';
import { notificationController } from '../controllers/notification.controller';
import { authenticate } from '../middleware/auth';
import { validateQuery } from '../middleware/validate';
import { notificationQuerySchema } from '../validators/notification.validator';

const router = Router();

// All notification routes require authentication
router.use(authenticate);

// Get notifications (paginated)
router.get(
  '/',
  validateQuery(notificationQuerySchema),
  notificationController.getNotifications.bind(notificationController)
);

// Get unread count
router.get(
  '/unread-count',
  notificationController.getUnreadCount.bind(notificationController)
);

// Mark one as read
router.patch(
  '/:notificationId/read',
  notificationController.markAsRead.bind(notificationController)
);

// Mark all as read
router.patch(
  '/read-all',
  notificationController.markAllAsRead.bind(notificationController)
);

export default router;
