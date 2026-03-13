// ============================================================
// Chalo Backend — Admin Controller
// Thin handlers: validate → delegate to adminService → respond
// ============================================================

import { Request, Response, NextFunction } from 'express';
import { adminService } from '../services/admin.service';
import { ApiResponse } from '../utils/apiResponse';
import {
  AdminPaginationQuery,
  AdminDriverParam,
  ApproveDriverInput,
  RejectDriverInput,
  ConfigKeyParam,
  UpdateConfigInput,
  PromoteAdminInput,
} from '../validators/admin.validator';

export class AdminController {

  // -------------------------------------------------------
  // Driver Management
  // -------------------------------------------------------

  async listPendingDrivers(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const { page, limit } = req.query as unknown as AdminPaginationQuery;
      const result = await adminService.listPendingDrivers(page, limit);
      ApiResponse.paginated(res, result.drivers, result.meta, 'Pending drivers retrieved');
    } catch (error) {
      next(error);
    }
  }

  async getDriverDetail(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const { driverId } = req.params as AdminDriverParam;
      const driver = await adminService.getDriverDetail(driverId);
      ApiResponse.success(res, driver, 'Driver detail retrieved');
    } catch (error) {
      next(error);
    }
  }

  async approveDriver(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const { driverId } = req.params as AdminDriverParam;
      const { note } = req.body as ApproveDriverInput;
      const result = await adminService.approveDriver(driverId, note);
      ApiResponse.success(res, result, 'Driver approved');
    } catch (error) {
      next(error);
    }
  }

  async rejectDriver(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const { driverId } = req.params as AdminDriverParam;
      const { reason } = req.body as RejectDriverInput;
      const result = await adminService.rejectDriver(driverId, reason);
      ApiResponse.success(res, result, 'Driver rejected');
    } catch (error) {
      next(error);
    }
  }

  async autoVerifyDriver(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const { driverId } = req.params as AdminDriverParam;
      const result = await adminService.autoVerifyDriver(driverId);
      ApiResponse.success(res, result, 'KYC verification completed');
    } catch (error) {
      next(error);
    }
  }

  // -------------------------------------------------------
  // Live Ride Monitoring
  // -------------------------------------------------------

  async getLiveRides(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const { page, limit } = req.query as unknown as AdminPaginationQuery;
      const result = await adminService.getLiveRides(page, limit);
      ApiResponse.paginated(res, result.rides, result.meta, 'Live rides retrieved');
    } catch (error) {
      next(error);
    }
  }

  async getRides(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const { page, limit } = req.query as unknown as AdminPaginationQuery;
      const { paymentStatus, status } = req.query as { paymentStatus?: string; status?: string };
      const result = await adminService.getRidesByFilter(page, limit, { paymentStatus, status });
      ApiResponse.paginated(res, result.rides, result.meta, 'Rides retrieved');
    } catch (error) {
      next(error);
    }
  }

  // -------------------------------------------------------
  // Platform Config
  // -------------------------------------------------------

  async getAllConfig(_req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const config = await adminService.getAllConfig();
      ApiResponse.success(res, config, 'Platform config retrieved');
    } catch (error) {
      next(error);
    }
  }

  async updateConfig(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const { key } = req.params as ConfigKeyParam;
      const { value } = req.body as UpdateConfigInput;
      const config = await adminService.updateConfig(key, value);
      ApiResponse.success(res, config, 'Platform config updated');
    } catch (error) {
      next(error);
    }
  }

  async promoteToAdmin(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const { phone } = req.body as PromoteAdminInput;
      const result = await adminService.promoteToAdmin(phone);
      ApiResponse.success(res, result, 'User promoted to ADMIN');
    } catch (error) {
      next(error);
    }
  }
}

export const adminController = new AdminController();
