// ============================================================
// Chalo Backend — Auth Controller
// Handles HTTP requests for authentication endpoints
// ============================================================

import { Request, Response, NextFunction } from 'express';
import { authService } from '../services/auth.service';
import { ApiResponse } from '../utils/apiResponse';
import { AuthenticatedRequest } from '../types';
import {
  SendOTPInput,
  VerifyOTPInput,
  CompleteProfileInput,
  UpdateEmergencyContactInput,
  RegisterDeviceTokenInput,
  RegisterDriverInput,
} from '../validators/auth.validator';
import { SavedLocationInput } from '../validators/ride.validator';

export class AuthController {
  /**
   * POST /auth/otp/send
   * Send OTP to phone number
   */
  async sendOTP(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const input = req.body as SendOTPInput;
      const result = await authService.sendOTP(input);
      ApiResponse.success(res, result, 'OTP sent successfully');
    } catch (error) {
      next(error);
    }
  }

  /**
   * POST /auth/otp/verify
   * Verify OTP and authenticate user
   */
  async verifyOTP(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const input = req.body as VerifyOTPInput;
      const result = await authService.verifyOTP(input);
      ApiResponse.success(res, result, result.isNewUser ? 'Welcome to Chalo!' : 'Login successful');
    } catch (error) {
      next(error);
    }
  }

  /**
   * PUT /auth/profile
   * Complete or update user profile
   */
  async completeProfile(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = (req as AuthenticatedRequest).user.id;
      const input = req.body as CompleteProfileInput;
      const result = await authService.completeProfile(userId, input);
      ApiResponse.success(res, result, 'Profile updated');
    } catch (error) {
      next(error);
    }
  }

  /**
   * GET /auth/profile
   * Get current user's profile
   */
  async getProfile(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = (req as AuthenticatedRequest).user.id;
      const result = await authService.getProfile(userId);
      ApiResponse.success(res, result);
    } catch (error) {
      next(error);
    }
  }

  /**
   * PUT /auth/emergency-contact
   * Update emergency contact
   */
  async updateEmergencyContact(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = (req as AuthenticatedRequest).user.id;
      const input = req.body as UpdateEmergencyContactInput;
      const result = await authService.updateEmergencyContact(
        userId,
        input.emergencyContactName,
        input.emergencyContactPhone
      );
      ApiResponse.success(res, result, 'Emergency contact updated');
    } catch (error) {
      next(error);
    }
  }

  /**
   * PUT /auth/saved-location
   * Save home or work location
   */
  async updateSavedLocation(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = (req as AuthenticatedRequest).user.id;
      const input = req.body as SavedLocationInput;
      const result = await authService.updateSavedLocation(
        userId,
        input.type,
        input.lat,
        input.lng,
        input.address
      );
      ApiResponse.success(res, result, `${input.type} location saved`);
    } catch (error) {
      next(error);
    }
  }

  /**
   * POST /auth/register-driver
   * Verify OTP + create driver account atomically
   */
  async registerDriver(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const input = req.body as RegisterDriverInput;
      const result = await authService.registerDriver(input);
      ApiResponse.success(res, result, 'Driver registered successfully');
    } catch (error) {
      next(error);
    }
  }

  /**
   * PUT /auth/device-token
   * Register or update FCM device token for push notifications
   */
  async registerDeviceToken(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = (req as AuthenticatedRequest).user.id;
      const { fcmToken } = req.body as RegisterDeviceTokenInput;
      await authService.registerDeviceToken(userId, fcmToken);
      ApiResponse.success(res, null, 'Device token registered');
    } catch (error) {
      next(error);
    }
  }
}

export const authController = new AuthController();
export default authController;
