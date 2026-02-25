// ============================================================
// Chalo Backend — Ride Controller
// Handles HTTP requests for ride endpoints (Customer-facing)
// ============================================================

import { Request, Response, NextFunction } from 'express';
import { rideService } from '../services/ride.service';
import { fareService } from '../services/fare.service';
import { sosService } from '../services/sos.service';
import { ApiResponse } from '../utils/apiResponse';
import { AuthenticatedRequest } from '../types';
import {
  FareEstimateInput,
  CreateRideInput,
  ScheduleRideInput,
  RateRideInput,
  CancelRideInput,
  RideHistoryQuery,
} from '../validators/ride.validator';

export class RideController {
  /**
   * POST /rides/fare-estimate
   * Get fare estimate before booking
   */
  async getFareEstimate(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const input = req.body as FareEstimateInput;
      const estimate = await fareService.estimateFare(input.pickup, input.drop);
      ApiResponse.success(res, estimate, 'Fare estimated');
    } catch (error) {
      next(error);
    }
  }

  /**
   * POST /rides
   * Create an on-demand ride
   */
  async createRide(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = (req as AuthenticatedRequest).user.id;
      const input = req.body as CreateRideInput;

      const ride = await rideService.createRide(
        userId,
        input.pickup,
        input.drop,
        input.paymentMethod
      );

      ApiResponse.created(res, ride, 'Ride request created — searching for riders');
    } catch (error) {
      next(error);
    }
  }

  /**
   * POST /rides/schedule
   * Create a scheduled ride
   */
  async createScheduledRide(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = (req as AuthenticatedRequest).user.id;
      const input = req.body as ScheduleRideInput;

      const ride = await rideService.createScheduledRide(
        userId,
        input.pickup,
        input.drop,
        input.paymentMethod,
        input.scheduledAt
      );

      ApiResponse.created(res, ride, 'Ride scheduled successfully');
    } catch (error) {
      next(error);
    }
  }

  /**
   * GET /rides/:rideId
   * Get ride details
   */
  async getRideDetails(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = (req as AuthenticatedRequest).user.id;
      const { rideId } = req.params;

      const ride = await rideService.getRideDetails(rideId, userId);
      ApiResponse.success(res, ride);
    } catch (error) {
      next(error);
    }
  }

  /**
   * GET /rides/history
   * Get ride history with pagination
   */
  async getRideHistory(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = (req as AuthenticatedRequest).user.id;
      const query = req.query as unknown as RideHistoryQuery;

      const { rides, meta } = await rideService.getRideHistory(
        userId,
        query.page,
        query.limit,
        query.status
      );

      ApiResponse.paginated(res, rides, meta);
    } catch (error) {
      next(error);
    }
  }

  /**
   * GET /rides/scheduled
   * Get upcoming scheduled rides
   */
  async getScheduledRides(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = (req as AuthenticatedRequest).user.id;
      const rides = await rideService.getScheduledRides(userId);
      ApiResponse.success(res, rides);
    } catch (error) {
      next(error);
    }
  }

  /**
   * POST /rides/:rideId/cancel
   * Cancel a ride
   */
  async cancelRide(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = (req as AuthenticatedRequest).user.id;
      const { rideId } = req.params;
      const input = req.body as CancelRideInput;

      const result = await rideService.cancelRide(rideId, userId, input.reason);
      ApiResponse.success(res, result, 'Ride cancelled');
    } catch (error) {
      next(error);
    }
  }

  /**
   * POST /rides/:rideId/rate
   * Rate a completed ride
   */
  async rateRide(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = (req as AuthenticatedRequest).user.id;
      const { rideId } = req.params;
      const input = req.body as RateRideInput;

      const result = await rideService.rateRide(rideId, userId, input.rating, input.comment);
      ApiResponse.success(res, result, 'Thank you for rating!');
    } catch (error) {
      next(error);
    }
  }

  /**
   * POST /rides/:rideId/sos
   * Trigger SOS alert
   */
  async triggerSOS(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = (req as AuthenticatedRequest).user.id;
      const { rideId } = req.params;
      const { lat, lng } = req.body;

      const result = await sosService.triggerSOS(rideId, userId, lat, lng);
      ApiResponse.success(res, result, 'SOS alert sent');
    } catch (error) {
      next(error);
    }
  }

  /**
   * POST /rides/sos/:sosAlertId/resolve
   * Resolve an SOS alert
   */
  async resolveSOS(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = (req as AuthenticatedRequest).user.id;
      const { sosAlertId } = req.params;

      const result = await sosService.resolveSOS(sosAlertId, userId);
      ApiResponse.success(res, result, 'SOS alert resolved');
    } catch (error) {
      next(error);
    }
  }
}

export const rideController = new RideController();
export default rideController;
