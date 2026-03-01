// ============================================================
// Chalo Backend — Driver Controller
// HTTP handlers for all driver-side endpoints.
// Each handler is a thin shell: validate → delegate to service → respond.
// ============================================================

import { Request, Response, NextFunction } from 'express';
import { driverService } from '../services/driver.service';
import { ApiResponse } from '../utils/apiResponse';
import { AuthenticatedRequest } from '../types';
import {
  GoOnlineInput,
  UpdateLocationInput,
  DriverRideParam,
  WithdrawalIdParam,
  DeclineRideInput,
  EarningsQuery,
  TripHistoryQuery,
  WithdrawalInput,
} from '../validators/driver.validator';

export class DriverController {
  // -----------------------------------------------------------------------
  // ONLINE / OFFLINE
  // -----------------------------------------------------------------------

  /**
   * POST /driver/go-online
   * Set driver online with their starting GPS coordinates.
   */
  async goOnline(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = (req as AuthenticatedRequest).user.id;
      const { lat, lng } = req.body as GoOnlineInput;

      const result = await driverService.goOnline(userId, lat, lng);
      ApiResponse.success(res, result, 'You are now online and accepting rides');
    } catch (error) {
      next(error);
    }
  }

  /**
   * POST /driver/go-offline
   * Set driver offline. Blocked if an active ride is in progress.
   */
  async goOffline(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = (req as AuthenticatedRequest).user.id;

      const result = await driverService.goOffline(userId);
      ApiResponse.success(res, result, 'You are now offline');
    } catch (error) {
      next(error);
    }
  }

  /**
   * GET /driver/status
   * Returns online state, current location, active ride (if any).
   */
  async getStatus(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = (req as AuthenticatedRequest).user.id;

      const status = await driverService.getStatus(userId);
      ApiResponse.success(res, status);
    } catch (error) {
      next(error);
    }
  }

  // -----------------------------------------------------------------------
  // LOCATION
  // -----------------------------------------------------------------------

  /**
   * POST /driver/location
   * Receive a GPS update from the driver app.
   * Writes to Redis (primary) → Postgres (async) → RTDB (if active ride).
   */
  async updateLocation(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = (req as AuthenticatedRequest).user.id;
      const { lat, lng } = req.body as UpdateLocationInput;

      const result = await driverService.updateLocation(userId, lat, lng);
      ApiResponse.success(res, result, 'Location updated');
    } catch (error) {
      next(error);
    }
  }

  // -----------------------------------------------------------------------
  // INCOMING RIDE
  // -----------------------------------------------------------------------

  /**
   * GET /driver/rides/incoming
   * Poll for the current ride offer sent to this driver via FCM.
   * Returns null if no offer is pending.
   */
  async getIncomingRide(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = (req as AuthenticatedRequest).user.id;

      const offer = await driverService.getIncomingRide(userId);
      ApiResponse.success(res, offer, offer ? 'Incoming ride available' : 'No incoming ride');
    } catch (error) {
      next(error);
    }
  }

  // -----------------------------------------------------------------------
  // RIDE LIFECYCLE
  // -----------------------------------------------------------------------

  /**
   * POST /driver/rides/:rideId/accept
   * Atomically accept a ride offer. Race-safe via DB compare-and-swap.
   */
  async acceptRide(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = (req as AuthenticatedRequest).user.id;
      const { rideId } = req.params as DriverRideParam;

      const result = await driverService.acceptRide(userId, rideId);
      ApiResponse.success(res, result, 'Ride accepted — navigate to pickup');
    } catch (error) {
      next(error);
    }
  }

  /**
   * POST /driver/rides/:rideId/decline
   * Decline a ride offer. Triggers re-search for next available driver.
   */
  async declineRide(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = (req as AuthenticatedRequest).user.id;
      const { rideId } = req.params as DriverRideParam;
      const { reason } = req.body as DeclineRideInput;

      const result = await driverService.declineRide(userId, rideId, reason);
      ApiResponse.success(res, result, 'Ride declined');
    } catch (error) {
      next(error);
    }
  }

  /**
   * POST /driver/rides/:rideId/arrived
   * Mark driver as arrived at pickup location.
   */
  async arrivedAtPickup(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = (req as AuthenticatedRequest).user.id;
      const { rideId } = req.params as DriverRideParam;

      const result = await driverService.arrivedAtPickup(userId, rideId);
      ApiResponse.success(res, result, 'Marked as arrived at pickup');
    } catch (error) {
      next(error);
    }
  }

  /**
   * POST /driver/rides/:rideId/start
   * Start the ride — customer is on board, heading to destination.
   */
  async startRide(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = (req as AuthenticatedRequest).user.id;
      const { rideId } = req.params as DriverRideParam;

      const result = await driverService.startRide(userId, rideId);
      ApiResponse.success(res, result, 'Ride started');
    } catch (error) {
      next(error);
    }
  }

  /**
   * POST /driver/rides/:rideId/complete
   * Complete the ride. Creates earning record atomically.
   */
  async completeRide(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = (req as AuthenticatedRequest).user.id;
      const { rideId } = req.params as DriverRideParam;

      const result = await driverService.completeRide(userId, rideId);
      ApiResponse.success(res, result, 'Ride completed successfully');
    } catch (error) {
      next(error);
    }
  }

  /**
   * POST /driver/rides/:rideId/cancel
   * Cancel a ride (driver side). Only allowed before ride starts.
   */
  async cancelRide(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = (req as AuthenticatedRequest).user.id;
      const { rideId } = req.params as DriverRideParam;
      const { reason } = req.body as DeclineRideInput;

      const result = await driverService.cancelRide(userId, rideId, reason);
      ApiResponse.success(res, result, 'Ride cancelled');
    } catch (error) {
      next(error);
    }
  }

  // -----------------------------------------------------------------------
  // TRIP HISTORY
  // -----------------------------------------------------------------------

  /**
   * GET /driver/trips
   * Paginated list of completed and cancelled trips for this driver.
   */
  async getTripHistory(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = (req as AuthenticatedRequest).user.id;
      const query = req.query as unknown as TripHistoryQuery;

      const { trips, meta } = await driverService.getTripHistory(userId, query.page, query.limit);
      ApiResponse.paginated(res, trips, meta);
    } catch (error) {
      next(error);
    }
  }

  // -----------------------------------------------------------------------
  // EARNINGS
  // -----------------------------------------------------------------------

  /**
   * GET /driver/earnings
   * Earnings summary + paginated per-trip breakdown for a period.
   */
  async getEarnings(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = (req as AuthenticatedRequest).user.id;
      const query = req.query as unknown as EarningsQuery;

      const result = await driverService.getEarnings(
        userId,
        query.period,
        query.page,
        query.limit
      );
      ApiResponse.success(res, result);
    } catch (error) {
      next(error);
    }
  }

  /**
   * GET /driver/earnings/settlement
   * Breakdown of pending / processing / settled amounts for this driver.
   */
  async getSettlementSummary(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = (req as AuthenticatedRequest).user.id;

      const result = await driverService.getSettlementSummary(userId);
      ApiResponse.success(res, result);
    } catch (error) {
      next(error);
    }
  }

  // -----------------------------------------------------------------------
  // WITHDRAWALS
  // -----------------------------------------------------------------------

  /**
   * POST /driver/withdrawals
   * Request a payout to bank account or via cash agent.
   */
  async requestWithdrawal(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = (req as AuthenticatedRequest).user.id;
      const input = req.body as WithdrawalInput;

      const result = await driverService.requestWithdrawal(userId, input.amount, input.method, {
        bankAccountNumber: input.bankAccountNumber,
        bankIfsc: input.bankIfsc,
        upiId: input.upiId,
      });
      ApiResponse.created(res, result, 'Withdrawal request submitted');
    } catch (error) {
      next(error);
    }
  }

  /**
   * GET /driver/withdrawals/:withdrawalId
   * Get status of a specific withdrawal request.
   */
  async getWithdrawalStatus(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = (req as AuthenticatedRequest).user.id;
      const { withdrawalId } = req.params as WithdrawalIdParam;

      const result = await driverService.getWithdrawalStatus(userId, withdrawalId);
      ApiResponse.success(res, result);
    } catch (error) {
      next(error);
    }
  }
}

export const driverController = new DriverController();
export default driverController;
