// ============================================================
// Chalo Backend — Notification Controller
// ============================================================

import { Request, Response, NextFunction } from 'express';
import { notificationService } from '../services/notification.service';
import { ApiResponse, buildPaginationMeta } from '../utils/apiResponse';
import { AuthenticatedRequest } from '../types';

export class NotificationController {
  /**
   * GET /notifications
   * Get user's notifications
   */
  async getNotifications(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = (req as AuthenticatedRequest).user.id;
      const page = req.query.page as unknown as number;
      const limit = req.query.limit as unknown as number;

      const { notifications, total } = await notificationService.getUserNotifications(userId, page, limit);
      const meta = buildPaginationMeta(page, limit, total);

      ApiResponse.paginated(res, notifications, meta);
    } catch (error) {
      next(error);
    }
  }

  /**
   * GET /notifications/unread-count
   * Get count of unread notifications
   */
  async getUnreadCount(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = (req as AuthenticatedRequest).user.id;
      const count = await notificationService.getUnreadCount(userId);
      ApiResponse.success(res, { unreadCount: count });
    } catch (error) {
      next(error);
    }
  }

  /**
   * PATCH /notifications/:notificationId/read
   * Mark a single notification as read
   */
  async markAsRead(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = (req as AuthenticatedRequest).user.id;
      const { notificationId } = req.params;

      await notificationService.markAsRead(notificationId, userId);
      ApiResponse.success(res, null, 'Notification marked as read');
    } catch (error) {
      next(error);
    }
  }

  /**
   * PATCH /notifications/read-all
   * Mark all notifications as read
   */
  async markAllAsRead(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = (req as AuthenticatedRequest).user.id;
      await notificationService.markAllAsRead(userId);
      ApiResponse.success(res, null, 'All notifications marked as read');
    } catch (error) {
      next(error);
    }
  }
}

export const notificationController = new NotificationController();
export default notificationController;
