// ============================================================
// Chalo Backend — Payment Controller
// Handles HTTP requests for payment endpoints
// ============================================================

import { Request, Response, NextFunction } from 'express';
import { paymentService } from '../services/payment.service';
import { ApiResponse } from '../utils/apiResponse';
import { AuthenticatedRequest } from '../types';
import { CreatePaymentOrderInput, VerifyPaymentInput } from '../validators/payment.validator';

export class PaymentController {
  /**
   * POST /payments/order
   * Create a Razorpay order for UPI payment
   */
  async createOrder(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = (req as AuthenticatedRequest).user.id;
      const input = req.body as CreatePaymentOrderInput;

      const order = await paymentService.createOrder(input.rideId, userId);
      ApiResponse.created(res, order, 'Payment order created');
    } catch (error) {
      next(error);
    }
  }

  /**
   * POST /payments/verify
   * Verify Razorpay payment after completion
   */
  async verifyPayment(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const userId = (req as AuthenticatedRequest).user.id;
      const input = req.body as VerifyPaymentInput;

      const result = await paymentService.verifyPayment(
        input.razorpayPaymentId,
        input.razorpayOrderId,
        input.razorpaySignature,
        input.rideId,
        userId
      );

      ApiResponse.success(res, result, 'Payment verified');
    } catch (error) {
      next(error);
    }
  }

  /**
   * POST /payments/webhook
   * Razorpay webhook handler (no auth — verified via signature)
   */
  async handleWebhook(req: Request, res: Response, next: NextFunction): Promise<void> {
    try {
      const signature = req.headers['x-razorpay-signature'] as string;
      // Use raw body bytes for signature verification (not re-stringified JSON)
      const rawBody = Buffer.isBuffer(req.body) ? req.body : Buffer.from(JSON.stringify(req.body));
      const result = await paymentService.handleWebhook(rawBody, signature);
      ApiResponse.success(res, result, 'Webhook received');
    } catch (error) {
      next(error);
    }
  }
}

export const paymentController = new PaymentController();
export default paymentController;
