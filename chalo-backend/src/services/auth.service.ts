// ============================================================
// Chalo Backend — Auth Service
// Handles OTP generation, verification, user creation
// ============================================================

import prisma from '../config/database';
import { ApiError } from '../utils/apiError';
import { generateOTP, maskPhone } from '../utils/helpers';
import CONSTANTS from '../utils/constants';
import config from '../config';
import logger from '../config/logger';
import { getAuth } from '../config/firebase';
import crypto from 'crypto';
import { UserRole } from '@prisma/client';
import { SendOTPInput, VerifyOTPInput, CompleteProfileInput, RegisterDriverInput } from '../validators/auth.validator';

/**
 * Hash OTP with SHA-256 before storing in database.
 * Prevents OTP exposure from database breaches.
 */
function hashOTP(otp: string): string {
  return crypto.createHash('sha256').update(otp).digest('hex');
}

export class AuthService {
  /**
   * Send OTP to phone number
   * In production: sends via Firebase Auth or SMS gateway
   * In development: logs OTP to console
   */
  async sendOTP(input: SendOTPInput): Promise<{ message: string; expiresIn: number }> {
    const { phone } = input;

    // Check rate limit: max 3 OTPs per phone per 15 mins
    const recentOTPs = await prisma.oTPVerification.count({
      where: {
        phone,
        createdAt: {
          gte: new Date(Date.now() - 15 * 60 * 1000),
        },
      },
    });

    if (recentOTPs >= CONSTANTS.MAX_OTP_ATTEMPTS) {
      throw ApiError.tooManyRequests('Too many OTP requests. Please wait 15 minutes.');
    }

    // Generate OTP
    const otpCode = generateOTP();
    const expiresAt = new Date(Date.now() + CONSTANTS.OTP_EXPIRY_MINS * 60 * 1000);

    // Store OTP (hashed to prevent exposure from DB breaches)
    await prisma.oTPVerification.create({
      data: {
        phone,
        otpCode: hashOTP(otpCode),
        expiresAt,
      },
    });

    // Log OTP only in development — never in production
    if (config.isDev) {
      logger.debug(`[DEV] OTP for ${phone}: ${otpCode}`);
    } else {
      logger.info(`OTP sent to ${maskPhone(phone)} (expires: ${expiresAt.toISOString()})`);
    }

    return {
      message: 'OTP sent successfully',
      expiresIn: CONSTANTS.OTP_EXPIRY_MINS * 60,
    };
  }

  /**
   * Verify OTP and return user data
   * Creates user account on first verification
   */
  async verifyOTP(input: VerifyOTPInput): Promise<{
    isNewUser: boolean;
    token: string;
    user: {
      id: string;
      phone: string;
      name: string | null;
      role: UserRole;
    };
  }> {
    const { phone, otp } = input;

    // Hash incoming OTP to compare against stored hash
    const hashedOtp = hashOTP(otp);

    // Use transaction for atomic check-and-update (prevents race conditions)
    const otpRecord = await prisma.$transaction(async (tx) => {
      const record = await tx.oTPVerification.findFirst({
        where: {
          phone,
          otpCode: hashedOtp,
          verified: false,
          expiresAt: { gte: new Date() },
        },
        orderBy: { createdAt: 'desc' },
      });

      if (!record) return null;

      if (record.attempts >= CONSTANTS.MAX_OTP_ATTEMPTS) {
        return 'MAX_ATTEMPTS' as const;
      }

      // Atomically mark as verified
      await tx.oTPVerification.update({
        where: { id: record.id },
        data: {
          verified: true,
          attempts: { increment: 1 },
        },
      });

      return record;
    });

    if (otpRecord === 'MAX_ATTEMPTS') {
      throw ApiError.tooManyRequests('Maximum OTP attempts exceeded. Request a new OTP.');
    }

    if (!otpRecord) {
      // Check if expired vs wrong code
      const expiredOTP = await prisma.oTPVerification.findFirst({
        where: { phone, otpCode: hashedOtp, verified: false },
        orderBy: { createdAt: 'desc' },
      });

      if (expiredOTP) {
        throw ApiError.badRequest('OTP has expired. Please request a new one.');
      }

      throw ApiError.badRequest('Invalid OTP. Please check and try again.');
    }

    // Atomically find or create user — prevents UniqueConstraintError on concurrent
    // requests for the same phone (e.g., two OTPs both verified in the same millisecond)
    const [user, isNewUser] = await prisma.$transaction(async (tx) => {
      const existing = await tx.user.findUnique({ where: { phone } });
      if (existing) return [existing, false] as const;

      const created = await tx.user.create({
        data: {
          phone,
          role: UserRole.CUSTOMER,
          customerProfile: {
            create: {},
          },
        },
      });

      logger.info(`New user created: ${phone}`, { userId: created.id });
      return [created, true] as const;
    });

    // Update last login (outside user-creation transaction — non-critical)
    await prisma.user.update({
      where: { id: user.id },
      data: { lastLoginAt: new Date() },
    });

    const token = await getAuth().createCustomToken(user.id);

    return {
      isNewUser,
      token,
      user: {
        id: user.id,
        phone: user.phone,
        name: user.name,
        role: user.role,
      },
    };
  }

  /**
   * Complete user profile after first login
   */
  async completeProfile(userId: string, input: CompleteProfileInput) {
    const user = await prisma.user.findUnique({ where: { id: userId } });

    if (!user) {
      throw ApiError.notFound('User not found');
    }

    const updated = await prisma.user.update({
      where: { id: userId },
      data: {
        name: input.name,
        email: input.email || null,
        languagePref: input.languagePref,
      },
      select: {
        id: true,
        phone: true,
        name: true,
        email: true,
        role: true,
        languagePref: true,
      },
    });

    return updated;
  }

  /**
   * Register driver by verifying OTP, upgrading the role, and ensuring a driver profile exists.
   */
  async registerDriver(input: RegisterDriverInput): Promise<{
    isNewUser: boolean;
    token: string;
    user: { id: string; phone: string; name: string | null; role: UserRole };
  }> {
    const otpResult = await this.verifyOTP({ phone: input.phone, otp: input.otp });

    const user = await prisma.$transaction(async (tx) => {
      const updated = await tx.user.update({
        where: { id: otpResult.user.id },
        data: {
          role: UserRole.DRIVER,
          name: input.name,
          lastLoginAt: new Date(),
        },
      });

      await tx.driverProfile.upsert({
        where: { userId: updated.id },
        update: {},
        create: { userId: updated.id },
      });

      return updated;
    });

    const token = await getAuth().createCustomToken(user.id);

    logger.info(`Driver registered: ${input.phone}`, { userId: user.id, isNewUser: otpResult.isNewUser });

    return {
      isNewUser: otpResult.isNewUser,
      token,
      user: {
        id: user.id,
        phone: user.phone,
        name: user.name,
        role: user.role,
      },
    };
  }

  /**
   * Get user profile with related data
   */
  async getProfile(userId: string) {
    const user = await prisma.user.findUnique({
      where: { id: userId },
      include: {
        customerProfile: true,
      },
    });

    if (!user) {
      throw ApiError.notFound('User not found');
    }

    return {
      id: user.id,
      phone: user.phone,
      name: user.name,
      email: user.email,
      role: user.role,
      languagePref: user.languagePref,
      emergencyContact: user.customerProfile
        ? {
            name: user.customerProfile.emergencyContactName,
            phone: user.customerProfile.emergencyContactPhone,
          }
        : null,
      savedLocations: user.customerProfile
        ? {
            home: user.customerProfile.savedHomeLat
              ? {
                  lat: user.customerProfile.savedHomeLat,
                  lng: user.customerProfile.savedHomeLng,
                  address: user.customerProfile.savedHomeAddress,
                }
              : null,
            work: user.customerProfile.savedWorkLat
              ? {
                  lat: user.customerProfile.savedWorkLat,
                  lng: user.customerProfile.savedWorkLng,
                  address: user.customerProfile.savedWorkAddress,
                }
              : null,
          }
        : null,
      createdAt: user.createdAt,
    };
  }

  /**
   * Update emergency contact
   */
  async updateEmergencyContact(
    userId: string,
    contactName: string,
    contactPhone: string
  ) {
    const profile = await prisma.customerProfile.findUnique({
      where: { userId },
    });

    if (!profile) {
      throw ApiError.notFound('Customer profile not found');
    }

    return prisma.customerProfile.update({
      where: { userId },
      data: {
        emergencyContactName: contactName,
        emergencyContactPhone: contactPhone,
      },
    });
  }

  /**
   * Update saved location (home / work)
   */
  async updateSavedLocation(
    userId: string,
    type: 'home' | 'work',
    lat: number,
    lng: number,
    address: string
  ) {
    const data =
      type === 'home'
        ? { savedHomeLat: lat, savedHomeLng: lng, savedHomeAddress: address }
        : { savedWorkLat: lat, savedWorkLng: lng, savedWorkAddress: address };

    return prisma.customerProfile.update({
      where: { userId },
      data,
    });
  }
  /**
   * Register or update FCM device token for push notifications
   */
  async registerDeviceToken(userId: string, fcmToken: string): Promise<void> {
    await prisma.user.update({
      where: { id: userId },
      data: { fcmToken, fcmTokenUpdatedAt: new Date() },
    });
    logger.info('FCM device token registered', { userId });
  }

  /**
   * Clean up expired and old verified OTP records (L3)
   * Should be called periodically (e.g., daily cron or startup)
   */
  async cleanupExpiredOTPs(): Promise<number> {
    const result = await prisma.oTPVerification.deleteMany({
      where: {
        OR: [
          { expiresAt: { lt: new Date() } },
          { verified: true, createdAt: { lt: new Date(Date.now() - 24 * 60 * 60 * 1000) } },
        ],
      },
    });
    logger.info(`Cleaned up ${result.count} expired OTP records`);
    return result.count;
  }
}

export const authService = new AuthService();
export default authService;


