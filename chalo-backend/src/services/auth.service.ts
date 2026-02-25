// ============================================================
// Chalo Backend — Auth Service
// Handles OTP generation, verification, user creation
// ============================================================

import prisma from '../config/database';
import { ApiError } from '../utils/apiError';
import { generateOTP } from '../utils/helpers';
import CONSTANTS from '../utils/constants';
import logger from '../config/logger';
import { UserRole } from '@prisma/client';
import { SendOTPInput, VerifyOTPInput, CompleteProfileInput } from '../validators/auth.validator';

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

    // Store OTP
    await prisma.oTPVerification.create({
      data: {
        phone,
        otpCode,
        expiresAt,
      },
    });

    // TODO: In production, send OTP via Firebase Auth or SMS provider (MSG91, Twilio)
    // For now, log it (dev only)
    logger.info(`OTP for ${phone}: ${otpCode} (expires: ${expiresAt.toISOString()})`);

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
    user: {
      id: string;
      phone: string;
      name: string | null;
      role: UserRole;
    };
  }> {
    const { phone, otp } = input;

    // Find the most recent valid OTP for this phone
    const otpRecord = await prisma.oTPVerification.findFirst({
      where: {
        phone,
        otpCode: otp,
        verified: false,
        expiresAt: { gte: new Date() },
      },
      orderBy: { createdAt: 'desc' },
    });

    if (!otpRecord) {
      // Check if expired vs wrong code
      const expiredOTP = await prisma.oTPVerification.findFirst({
        where: { phone, otpCode: otp, verified: false },
        orderBy: { createdAt: 'desc' },
      });

      if (expiredOTP) {
        throw ApiError.badRequest('OTP has expired. Please request a new one.');
      }

      throw ApiError.badRequest('Invalid OTP. Please check and try again.');
    }

    // Check attempts
    if (otpRecord.attempts >= CONSTANTS.MAX_OTP_ATTEMPTS) {
      throw ApiError.tooManyRequests('Maximum OTP attempts exceeded. Request a new OTP.');
    }

    // Mark OTP as verified
    await prisma.oTPVerification.update({
      where: { id: otpRecord.id },
      data: {
        verified: true,
        attempts: { increment: 1 },
      },
    });

    // Check if user exists
    let user = await prisma.user.findUnique({
      where: { phone },
    });

    let isNewUser = false;

    if (!user) {
      // Create new user as CUSTOMER (default role)
      user = await prisma.user.create({
        data: {
          phone,
          role: UserRole.CUSTOMER,
          customerProfile: {
            create: {},
          },
        },
      });
      isNewUser = true;

      logger.info(`New user created: ${phone}`, { userId: user.id });
    }

    // Update last login
    await prisma.user.update({
      where: { id: user.id },
      data: { lastLoginAt: new Date() },
    });

    return {
      isNewUser,
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
}

export const authService = new AuthService();
export default authService;
