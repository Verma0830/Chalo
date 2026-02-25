// ============================================================
// Chalo Backend — Notification Service
// FCM push notifications + in-app notification storage
// ============================================================

import prisma from '../config/database';
import logger from '../config/logger';
import { PushNotificationPayload } from '../types';
import { NotificationType } from '@prisma/client';

export class NotificationService {
  /**
   * Send a push notification to a user via FCM
   * Also stores the notification in DB for in-app notification center
   */
  async sendPushNotification(payload: PushNotificationPayload): Promise<void> {
    const { userId, title, body, data } = payload;

    try {
      // 1. Store in database (for in-app history)
      await prisma.notification.create({
        data: {
          userId,
          type: this.inferNotificationType(data?.type),
          title,
          body,
          data: data || {},
        },
      });

      // 2. Send via FCM
      // TODO: Implement FCM sending once Firebase is fully set up
      // Need to store FCM device tokens per user
      //
      // const { getMessaging } = await import('../config/firebase');
      // const messaging = getMessaging();
      // await messaging.send({
      //   token: userFCMToken,
      //   notification: { title, body },
      //   data: data || {},
      //   android: {
      //     priority: 'high',
      //     notification: {
      //       channelId: 'ride_updates',
      //       priority: 'high',
      //       sound: 'default',
      //     },
      //   },
      // });

      logger.info('Notification sent', { userId, title, type: data?.type });
    } catch (error) {
      // Notification failure should not break the main flow
      logger.error('Failed to send notification', { userId, title, error });
    }
  }

  /**
   * Get user's notifications with pagination
   */
  async getUserNotifications(userId: string, page: number, limit: number) {
    const [notifications, total] = await Promise.all([
      prisma.notification.findMany({
        where: { userId },
        orderBy: { sentAt: 'desc' },
        skip: (page - 1) * limit,
        take: limit,
      }),
      prisma.notification.count({ where: { userId } }),
    ]);

    return { notifications, total };
  }

  /**
   * Mark notification as read
   */
  async markAsRead(notificationId: string, userId: string): Promise<void> {
    await prisma.notification.updateMany({
      where: { id: notificationId, userId },
      data: { isRead: true },
    });
  }

  /**
   * Mark all notifications as read
   */
  async markAllAsRead(userId: string): Promise<void> {
    await prisma.notification.updateMany({
      where: { userId, isRead: false },
      data: { isRead: true },
    });
  }

  /**
   * Get unread notification count
   */
  async getUnreadCount(userId: string): Promise<number> {
    return prisma.notification.count({
      where: { userId, isRead: false },
    });
  }

  /**
   * Infer notification type from data
   */
  private inferNotificationType(type?: string): NotificationType {
    switch (type) {
      case 'RIDE_REQUEST':
      case 'RIDE_CANCELLED':
      case 'NO_DRIVER':
      case 'DRIVER_ASSIGNED':
      case 'DRIVER_ARRIVED':
      case 'RIDE_STARTED':
      case 'RIDE_COMPLETED':
        return NotificationType.RIDE_STATUS;
      case 'PAYMENT_COMPLETED':
      case 'PAYMENT_FAILED':
        return NotificationType.PAYMENT;
      case 'SCHEDULED_REMINDER':
        return NotificationType.SCHEDULED_REMINDER;
      default:
        return NotificationType.SYSTEM;
    }
  }
}

export const notificationService = new NotificationService();
export default notificationService;
