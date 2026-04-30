// ============================================================
// Tests — Notification Service (FCM push + in-app DB storage)
// ============================================================

import { NotificationType } from '@prisma/client';

// ── Mocks ──────────────────────────────────────────────────────
const mockSend = jest.fn();
jest.mock('../../config/firebase', () => ({
  getMessaging: jest.fn().mockReturnValue({ send: mockSend }),
}));

const mockNotificationCreate = jest.fn();
const mockUserFindUnique = jest.fn();
const mockUserUpdate = jest.fn();
jest.mock('../../config/database', () => ({
  __esModule: true,
  default: {
    notification: { create: mockNotificationCreate },
    user: { findUnique: mockUserFindUnique, update: mockUserUpdate },
  },
}));

jest.mock('../../config/logger', () => ({
  __esModule: true,
  default: { info: jest.fn(), warn: jest.fn(), error: jest.fn(), debug: jest.fn() },
}));

// ── Import after mocks ─────────────────────────────────────────
import { NotificationService } from '../../services/notification.service';

describe('NotificationService.sendPushNotification', () => {
  let service: NotificationService;

  beforeEach(() => {
    jest.clearAllMocks();
    service = new NotificationService();
    mockNotificationCreate.mockResolvedValue({ id: 'notif-1' });
  });

  it('stores notification in DB and sends FCM when token exists', async () => {
    mockUserFindUnique.mockResolvedValue({ fcmToken: 'valid-fcm-token' });
    mockSend.mockResolvedValue('message-id-123');

    await service.sendPushNotification({
      userId: 'user-1',
      title: 'Driver Found',
      body: 'Your driver is on the way',
      data: { rideId: 'ride-1', type: 'DRIVER_ASSIGNED' },
    });

    expect(mockNotificationCreate).toHaveBeenCalledTimes(1);
    expect(mockNotificationCreate).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({
          userId: 'user-1',
          title: 'Driver Found',
          body: 'Your driver is on the way',
          type: NotificationType.RIDE_STATUS,
        }),
      })
    );

    expect(mockSend).toHaveBeenCalledTimes(1);
    expect(mockSend).toHaveBeenCalledWith(
      expect.objectContaining({
        token: 'valid-fcm-token',
        notification: { title: 'Driver Found', body: 'Your driver is on the way' },
        android: expect.objectContaining({ priority: 'high' }),
      })
    );
  });

  it('stores notification in DB but skips FCM when no token', async () => {
    mockUserFindUnique.mockResolvedValue({ fcmToken: null });

    await service.sendPushNotification({
      userId: 'user-2',
      title: 'No Driver',
      body: 'No driver available',
    });

    expect(mockNotificationCreate).toHaveBeenCalledTimes(1);
    expect(mockSend).not.toHaveBeenCalled();
  });

  it('clears stale FCM token on registration-token-not-registered error', async () => {
    mockUserFindUnique.mockResolvedValue({ fcmToken: 'stale-token' });
    const staleError = Object.assign(new Error('Stale token'), {
      code: 'messaging/registration-token-not-registered',
    });
    mockSend.mockRejectedValue(staleError);
    mockUserUpdate.mockResolvedValue({});

    await service.sendPushNotification({
      userId: 'user-3',
      title: 'Test',
      body: 'Test message',
    });

    expect(mockUserUpdate).toHaveBeenCalledWith(
      expect.objectContaining({
        where: { id: 'user-3' },
        data: { fcmToken: null, fcmTokenUpdatedAt: null },
      })
    );
  });

  it('clears stale FCM token on invalid-registration-token error', async () => {
    mockUserFindUnique.mockResolvedValue({ fcmToken: 'invalid-token' });
    const invalidError = Object.assign(new Error('Invalid token'), {
      code: 'messaging/invalid-registration-token',
    });
    mockSend.mockRejectedValue(invalidError);
    mockUserUpdate.mockResolvedValue({});

    await service.sendPushNotification({ userId: 'user-4', title: 'Test', body: 'Test' });

    expect(mockUserUpdate).toHaveBeenCalledWith(
      expect.objectContaining({ data: { fcmToken: null, fcmTokenUpdatedAt: null } })
    );
  });

  it('does NOT clear token on non-stale FCM error (e.g. quota exceeded)', async () => {
    mockUserFindUnique.mockResolvedValue({ fcmToken: 'good-token' });
    const quotaError = Object.assign(new Error('Quota exceeded'), {
      code: 'messaging/message-rate-exceeded',
    });
    mockSend.mockRejectedValue(quotaError);

    await service.sendPushNotification({ userId: 'user-5', title: 'Test', body: 'Test' });

    expect(mockUserUpdate).not.toHaveBeenCalled();
  });

  it('does not throw when DB notification create fails', async () => {
    mockNotificationCreate.mockRejectedValue(new Error('DB error'));

    await expect(
      service.sendPushNotification({ userId: 'user-6', title: 'Test', body: 'Test' })
    ).resolves.toBeUndefined();

    expect(mockSend).not.toHaveBeenCalled();
  });

  it('infers PAYMENT notification type from data.type', async () => {
    mockUserFindUnique.mockResolvedValue({ fcmToken: null });

    await service.sendPushNotification({
      userId: 'user-7',
      title: 'Payment done',
      body: 'Paid',
      data: { type: 'PAYMENT_COMPLETED' },
    });

    expect(mockNotificationCreate).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({ type: NotificationType.PAYMENT }),
      })
    );
  });

  it('infers SYSTEM type for unknown data.type', async () => {
    mockUserFindUnique.mockResolvedValue({ fcmToken: null });

    await service.sendPushNotification({
      userId: 'user-8',
      title: 'Hello',
      body: 'World',
      data: { type: 'UNKNOWN_TYPE' },
    });

    expect(mockNotificationCreate).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({ type: NotificationType.SYSTEM }),
      })
    );
  });
});

describe('NotificationService.markAsRead', () => {
  const mockNotificationUpdateMany = jest.fn().mockResolvedValue({ count: 1 });

  beforeEach(() => {
    jest.clearAllMocks();
    // Augment the existing mock
    const prisma = jest.requireMock('../../config/database').default;
    prisma.notification.updateMany = mockNotificationUpdateMany;
  });

  it('updates only the notification owned by the user', async () => {
    const service = new NotificationService();
    await service.markAsRead('notif-1', 'user-1');

    expect(mockNotificationUpdateMany).toHaveBeenCalledWith({
      where: { id: 'notif-1', userId: 'user-1' },
      data: { isRead: true },
    });
  });
});
