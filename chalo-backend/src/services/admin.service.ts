// ============================================================
// Chalo Backend — Admin Service
// Driver verification, live ride monitoring, platform config
// ============================================================

import prisma from '../config/database';
import { ApiError, ErrorCode } from '../utils/apiError';
import { buildPaginationMeta } from '../utils/apiResponse';
import { notificationService } from './notification.service';
import { getKYCProvider } from './kyc';
import logger from '../config/logger';
import { VerificationStatus, RideStatus, UserRole } from '@prisma/client';

const AUTO_VERIFY_CONFIDENCE_THRESHOLD = 0.85;

class AdminService {

  // -------------------------------------------------------
  // Driver Management
  // -------------------------------------------------------

  async listPendingDrivers(page: number, limit: number) {
    const skip = (page - 1) * limit;

    const [drivers, total] = await Promise.all([
      prisma.driverProfile.findMany({
        where: {
          verificationStatus: { in: [VerificationStatus.PENDING, VerificationStatus.UNDER_REVIEW] },
        },
        include: {
          user: { select: { id: true, name: true, phone: true, email: true } },
        },
        orderBy: { createdAt: 'asc' }, // FIFO — oldest first
        skip,
        take: limit,
      }),
      prisma.driverProfile.count({
        where: {
          verificationStatus: { in: [VerificationStatus.PENDING, VerificationStatus.UNDER_REVIEW] },
        },
      }),
    ]);

    return { drivers, meta: buildPaginationMeta(page, limit, total) };
  }

  async getDriverDetail(driverId: string) {
    const driver = await prisma.driverProfile.findUnique({
      where: { userId: driverId },
      include: {
        user: { select: { id: true, name: true, phone: true, email: true, createdAt: true } },
      },
    });

    if (!driver) {
      throw ApiError.notFound('Driver not found', ErrorCode.DRIVER_NOT_FOUND);
    }

    return driver;
  }

  async approveDriver(driverId: string, note?: string) {
    const driver = await prisma.driverProfile.findUnique({
      where: { userId: driverId },
      select: { verificationStatus: true },
    });

    if (!driver) {
      throw ApiError.notFound('Driver not found', ErrorCode.DRIVER_NOT_FOUND);
    }

    await prisma.driverProfile.update({
      where: { userId: driverId },
      data: {
        verificationStatus: VerificationStatus.VERIFIED,
        verifiedAt: new Date(),
        verificationNote: note ?? null,
      },
    });

    void notificationService.sendPushNotification({
      userId: driverId,
      title: 'Account Approved!',
      body: 'Your account has been verified. You can now go online and accept rides.',
      data: { type: 'ACCOUNT_APPROVED' },
    }).catch((err) => {
      logger.warn('FCM failed after driver approval', { driverId, error: String(err) });
    });

    logger.info('Driver approved by admin', { driverId, note });
    return { driverId, status: VerificationStatus.VERIFIED };
  }

  async rejectDriver(driverId: string, reason: string) {
    const driver = await prisma.driverProfile.findUnique({
      where: { userId: driverId },
      select: { verificationStatus: true },
    });

    if (!driver) {
      throw ApiError.notFound('Driver not found', ErrorCode.DRIVER_NOT_FOUND);
    }

    await prisma.driverProfile.update({
      where: { userId: driverId },
      data: {
        verificationStatus: VerificationStatus.REJECTED,
        rejectionReason: reason,
        verificationNote: reason,
      },
    });

    void notificationService.sendPushNotification({
      userId: driverId,
      title: 'Account Not Approved',
      body: 'Your account verification was unsuccessful. Please contact support.',
      data: { type: 'ACCOUNT_REJECTED' },
    }).catch((err) => {
      logger.warn('FCM failed after driver rejection', { driverId, error: String(err) });
    });

    logger.info('Driver rejected by admin', { driverId, reason });
    return { driverId, status: VerificationStatus.REJECTED };
  }

  /**
   * Run KYC API against driver documents.
   * If confidence > threshold → auto-approve; otherwise return result for admin to decide.
   */
  async autoVerifyDriver(driverId: string) {
    const driver = await prisma.driverProfile.findUnique({
      where: { userId: driverId },
      select: {
        verificationStatus: true,
        aadharNumber: true,
        aadharUrl: true,
        licenseNumber: true,
        licenseUrl: true,
      },
    });

    if (!driver) {
      throw ApiError.notFound('Driver not found', ErrorCode.DRIVER_NOT_FOUND);
    }

    const provider = getKYCProvider();

    // Verify primary document (Aadhaar preferred, fall back to DL)
    const docResult = driver.aadharNumber
      ? await provider.verifyDocument({
          documentType: 'aadhaar',
          documentNumber: driver.aadharNumber,
          documentUrl: driver.aadharUrl ?? undefined,
        })
      : driver.licenseNumber
      ? await provider.verifyDocument({
          documentType: 'license',
          documentNumber: driver.licenseNumber,
          documentUrl: driver.licenseUrl ?? undefined,
        })
      : null;

    const metadata = docResult ?? { verified: false, confidence: null, provider: 'none' };
    const autoApprove =
      docResult !== null &&
      docResult.confidence !== null &&
      docResult.confidence >= AUTO_VERIFY_CONFIDENCE_THRESHOLD;

    // Store KYC result in DB regardless of outcome
    await prisma.driverProfile.update({
      where: { userId: driverId },
      data: {
        verificationMetadata: metadata as object,
        verificationStatus: autoApprove ? VerificationStatus.VERIFIED : VerificationStatus.UNDER_REVIEW,
        ...(autoApprove ? { verifiedAt: new Date(), verificationNote: 'Auto-verified by KYC API' } : {}),
      },
    });

    if (autoApprove) {
      void notificationService.sendPushNotification({
        userId: driverId,
        title: 'Account Approved!',
        body: 'Your documents have been verified. You can now go online and accept rides.',
        data: { type: 'ACCOUNT_APPROVED' },
      }).catch(() => null);
    }

    logger.info('Driver auto-verify completed', { driverId, autoApprove, provider: metadata.provider });

    return {
      driverId,
      autoApproved: autoApprove,
      confidence: metadata.confidence,
      provider: metadata.provider,
      status: autoApprove ? VerificationStatus.VERIFIED : VerificationStatus.UNDER_REVIEW,
    };
  }

  // -------------------------------------------------------
  // Live Ride Monitoring
  // -------------------------------------------------------

  async getLiveRides(page: number, limit: number) {
    const skip = (page - 1) * limit;

    const [rides, total] = await Promise.all([
      prisma.ride.findMany({
        where: {
          status: { in: [RideStatus.DRIVER_ASSIGNED, RideStatus.DRIVER_ARRIVED, RideStatus.IN_PROGRESS] },
        },
        include: {
          customer: { select: { id: true, name: true, phone: true } },
          driver: {
            select: {
              id: true,
              name: true,
              phone: true,
              driverProfile: { select: { vehicleNumber: true, ratingAvg: true } },
            },
          },
        },
        orderBy: { createdAt: 'desc' },
        skip,
        take: limit,
      }),
      prisma.ride.count({
        where: {
          status: { in: [RideStatus.DRIVER_ASSIGNED, RideStatus.DRIVER_ARRIVED, RideStatus.IN_PROGRESS] },
        },
      }),
    ]);

    return { rides, meta: buildPaginationMeta(page, limit, total) };
  }

  /**
   * General ride search for admin — supports filtering by paymentStatus.
   * Primary use case: find rides with paymentStatus=FAILED for manual follow-up.
   */
  async getRidesByFilter(
    page: number,
    limit: number,
    filters: { paymentStatus?: string; status?: string }
  ) {
    const skip = (page - 1) * limit;
    const where: Record<string, unknown> = {};
    if (filters.paymentStatus) where['paymentStatus'] = filters.paymentStatus;
    if (filters.status) where['status'] = filters.status;

    const [rides, total] = await Promise.all([
      prisma.ride.findMany({
        where,
        include: {
          customer: { select: { id: true, name: true, phone: true } },
          driver: {
            select: {
              id: true,
              name: true,
              phone: true,
              driverProfile: { select: { vehicleNumber: true } },
            },
          },
        },
        orderBy: { createdAt: 'desc' },
        skip,
        take: limit,
      }),
      prisma.ride.count({ where }),
    ]);

    return { rides, meta: buildPaginationMeta(page, limit, total) };
  }

  // -------------------------------------------------------
  // Platform Config
  // -------------------------------------------------------

  async getAllConfig() {
    return prisma.platformConfig.findMany({ orderBy: { key: 'asc' } });
  }

  async updateConfig(key: string, value: string) {
    const config = await prisma.platformConfig.upsert({
      where: { key },
      update: { value },
      create: { key, value },
    });

    logger.info('Platform config updated', { key, value });
    return config;
  }

  // -------------------------------------------------------
  // Admin Promotion
  // -------------------------------------------------------

  /**
   * Promote a user to ADMIN by phone number.
   * Protected by INTERNAL_API_KEY — not reachable without the secret.
   * Idempotent: returns existing admin without error.
   */
  async promoteToAdmin(phone: string): Promise<{ id: string; phone: string; role: UserRole }> {
    const user = await prisma.user.findUnique({ where: { phone } });

    if (!user) {
      throw ApiError.notFound(`No user found with phone ${phone}`);
    }

    if (user.role === UserRole.ADMIN) {
      logger.info('User is already ADMIN — idempotent', { userId: user.id, phone });
      return { id: user.id, phone: user.phone, role: user.role };
    }

    const updated = await prisma.user.update({
      where: { id: user.id },
      data: { role: UserRole.ADMIN },
      select: { id: true, phone: true, role: true },
    });

    logger.info('User promoted to ADMIN', { userId: updated.id, phone });
    return updated;
  }
}

export const adminService = new AdminService();
export default adminService;
