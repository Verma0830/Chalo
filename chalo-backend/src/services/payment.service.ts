// ============================================================
// Chalo Backend — Payment Service
// Razorpay integration for UPI + cash flow management
// ============================================================

import Razorpay from 'razorpay';
import crypto, { timingSafeEqual } from 'crypto';
import prisma from '../config/database';
import config from '../config';
import { ApiError } from '../utils/apiError';
import { withCircuitBreaker } from '../utils/circuitBreaker';
import logger from '../config/logger';
import { PaymentStatus, RideStatus } from '@prisma/client';

// Initialize Razorpay instance
const razorpay = new Razorpay({
  key_id: config.razorpay.keyId,
  key_secret: config.razorpay.keySecret,
});

// Wrap Razorpay order creation with circuit breaker
const createRazorpayOrder = withCircuitBreaker(
  'razorpay',
  async (params: { amount: number; currency: string; receipt: string; notes: Record<string, string> }) => {
    return razorpay.orders.create(params);
  },
  { timeout: 15000, resetTimeout: 30000 }
);

export class PaymentService {
  /**
   * Create a Razorpay order for UPI payment
   * Called when customer confirms a ride with UPI payment method
   */
  async createOrder(rideId: string, customerId: string) {
    const ride = await prisma.ride.findUnique({ where: { id: rideId } });

    if (!ride) {
      throw ApiError.notFound('Ride not found');
    }

    if (ride.customerId !== customerId) {
      throw ApiError.forbidden('Not authorized');
    }

    if (ride.paymentMethod !== 'UPI') {
      throw ApiError.badRequest('This ride is set for cash payment');
    }

    if (ride.razorpayOrderId) {
      // Return existing order if already created
      return {
        orderId: ride.razorpayOrderId,
        amount: ride.finalFare * 100, // Paise
        currency: 'INR',
        rideId,
      };
    }

    try {
      // Create Razorpay order (protected by circuit breaker)
      const order = await createRazorpayOrder({
        amount: Math.round(ride.finalFare * 100), // Convert to paise
        currency: 'INR',
        receipt: `ride_${rideId}`,
        notes: {
          rideId,
          customerId,
        },
      });

      // Save order ID to ride
      await prisma.ride.update({
        where: { id: rideId },
        data: { razorpayOrderId: order.id },
      });

      logger.info('Razorpay order created', { rideId, orderId: order.id, amount: ride.finalFare });

      return {
        orderId: order.id,
        amount: order.amount,
        currency: order.currency,
        rideId,
        keyId: config.razorpay.keyId, // Client needs this to open Razorpay checkout
      };
    } catch (error) {
      logger.error('Razorpay order creation failed', { rideId, error });
      throw ApiError.internal('Payment order creation failed. Please try again.');
    }
  }

  /**
   * Verify Razorpay payment signature (timing-safe)
   * Called after customer completes payment on Android
   */
  async verifyPayment(
    razorpayPaymentId: string,
    razorpayOrderId: string,
    razorpaySignature: string,
    rideId: string,
    userId: string
  ) {
    // 1. Fetch ride and validate ownership + order match
    const ride = await prisma.ride.findUnique({ where: { id: rideId } });

    if (!ride) {
      throw ApiError.notFound('Ride not found');
    }

    if (ride.customerId !== userId) {
      throw ApiError.forbidden('Not authorized to verify this payment');
    }

    if (ride.razorpayOrderId !== razorpayOrderId) {
      logger.warn('Payment order mismatch', { rideId, expected: ride.razorpayOrderId, received: razorpayOrderId });
      throw ApiError.badRequest('Payment order does not match this ride');
    }

    if (ride.paymentStatus === PaymentStatus.COMPLETED) {
      throw ApiError.conflict('Payment already completed for this ride');
    }

    // 2. Verify signature using timing-safe comparison to prevent timing attacks
    const body = `${razorpayOrderId}|${razorpayPaymentId}`;
    const expectedSignature = crypto
      .createHmac('sha256', config.razorpay.keySecret)
      .update(body)
      .digest('hex');

    // Timing-safe comparison to prevent timing attacks
    const expectedBuffer = Buffer.from(expectedSignature, 'hex');
    let providedBuffer: Buffer;
    try {
      providedBuffer = Buffer.from(razorpaySignature, 'hex');
    } catch {
      throw ApiError.badRequest('Payment verification failed — invalid signature format');
    }

    if (expectedBuffer.length !== providedBuffer.length) {
      logger.warn('Payment signature length mismatch', { rideId, razorpayPaymentId });
      throw ApiError.badRequest('Payment verification failed — invalid signature');
    }

    const isValidSignature = timingSafeEqual(expectedBuffer, providedBuffer);

    if (!isValidSignature) {
      logger.warn('Payment signature verification failed', { rideId, razorpayPaymentId });
      throw ApiError.badRequest('Payment verification failed — invalid signature');
    }

    // Update ride payment status
    const updatedRide = await prisma.ride.update({
      where: { id: rideId },
      data: {
        razorpayPaymentId,
        paymentStatus: PaymentStatus.COMPLETED,
        rideEvents: {
          create: {
            eventType: 'PAYMENT_COMPLETED',
            metadata: { razorpayPaymentId, razorpayOrderId, method: 'UPI' },
          },
        },
      },
    });

    logger.info('Payment verified successfully', { rideId, razorpayPaymentId });

    return {
      rideId: updatedRide.id,
      paymentStatus: updatedRide.paymentStatus,
      amount: updatedRide.finalFare,
    };
  }

  /**
   * Confirm cash payment (driver confirms cash collected)
   */
  async confirmCashPayment(rideId: string, driverId: string) {
    const ride = await prisma.ride.findUnique({ where: { id: rideId } });

    if (!ride) {
      throw ApiError.notFound('Ride not found');
    }

    if (ride.driverId !== driverId) {
      throw ApiError.forbidden('Only the assigned driver can confirm cash payment');
    }

    if (ride.paymentMethod !== 'CASH') {
      throw ApiError.badRequest('This ride uses UPI payment, not cash');
    }

    if (ride.status !== RideStatus.COMPLETED) {
      throw ApiError.badRequest('Ride must be completed before confirming payment');
    }

    const updated = await prisma.ride.update({
      where: { id: rideId },
      data: {
        paymentStatus: PaymentStatus.COMPLETED,
        rideEvents: {
          create: {
            eventType: 'CASH_COLLECTED',
            metadata: { confirmedBy: driverId, amount: ride.finalFare },
          },
        },
      },
    });

    logger.info('Cash payment confirmed', { rideId, driverId, amount: ride.finalFare });

    return {
      rideId: updated.id,
      paymentStatus: updated.paymentStatus,
      amount: updated.finalFare,
    };
  }

  /**
   * Handle Razorpay webhook events (timing-safe signature verification)
   * Razorpay sends payment status updates here
   */
  async handleWebhook(rawBody: Buffer, signature: string) {
    // Verify webhook signature using raw body bytes (not re-stringified JSON)
    if (!config.razorpay.webhookSecret) {
      logger.error('Webhook secret not configured — rejecting webhook');
      throw ApiError.internal('Webhook processing unavailable');
    }

    const expectedSignature = crypto
      .createHmac('sha256', config.razorpay.webhookSecret)
      .update(rawBody)
      .digest('hex');

    // Timing-safe comparison to prevent timing attacks
    const expectedBuffer = Buffer.from(expectedSignature, 'hex');
    let providedBuffer: Buffer;
    try {
      providedBuffer = Buffer.from(signature, 'hex');
    } catch {
      throw ApiError.unauthorized('Invalid webhook signature format');
    }

    if (expectedBuffer.length !== providedBuffer.length) {
      logger.warn('Invalid webhook signature length');
      throw ApiError.unauthorized('Invalid webhook signature');
    }

    const isValidSignature = timingSafeEqual(expectedBuffer, providedBuffer);

    if (!isValidSignature) {
      logger.warn('Invalid webhook signature');
      throw ApiError.unauthorized('Invalid webhook signature');
    }

    // Parse raw body into JSON after signature verification
    const payload = JSON.parse(rawBody.toString()) as Record<string, unknown>;
    const event = payload.event as string;
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const paymentEntity = (payload as any)?.payload?.payment?.entity;

    if (!paymentEntity) {
      logger.warn('Webhook payload missing payment entity', { event });
      return { received: true };
    }

    switch (event) {
      case 'payment.captured': {
        // Payment successful — update ride only if not already completed (idempotent)
        const orderId = paymentEntity.order_id;
        const ride = await prisma.ride.findFirst({
          where: { razorpayOrderId: orderId },
        });

        if (ride && ride.paymentStatus !== PaymentStatus.COMPLETED) {
          await prisma.ride.update({
            where: { id: ride.id },
            data: {
              razorpayPaymentId: paymentEntity.id,
              paymentStatus: PaymentStatus.COMPLETED,
            },
          });
          logger.info('Webhook: payment captured', { rideId: ride.id });
        } else if (ride) {
          logger.info('Webhook: payment.captured ignored — already completed', { rideId: ride.id });
        }
        break;
      }

      case 'payment.failed': {
        const orderId = paymentEntity.order_id;
        const ride = await prisma.ride.findFirst({
          where: { razorpayOrderId: orderId },
        });

        // Never overwrite a COMPLETED payment — Razorpay can send events out of order
        if (ride && ride.paymentStatus !== PaymentStatus.COMPLETED) {
          await prisma.ride.update({
            where: { id: ride.id },
            data: { paymentStatus: PaymentStatus.FAILED },
          });
          logger.warn('Webhook: payment failed', { rideId: ride.id });
        } else if (ride) {
          logger.warn('Webhook: payment.failed ignored — payment already completed', { rideId: ride.id });
        }
        break;
      }

      default:
        logger.info('Webhook: unhandled event', { event });
    }

    return { received: true };
  }
}

export const paymentService = new PaymentService();
export default paymentService;
