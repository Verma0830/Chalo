// ============================================================
// Chalo Backend — Payment Service
// Razorpay integration for UPI + cash flow management
// ============================================================

import Razorpay from 'razorpay';
import crypto from 'crypto';
import prisma from '../config/database';
import config from '../config';
import { ApiError } from '../utils/apiError';
import logger from '../config/logger';
import { PaymentStatus, RideStatus } from '@prisma/client';

// Initialize Razorpay instance
const razorpay = new Razorpay({
  key_id: config.razorpay.keyId,
  key_secret: config.razorpay.keySecret,
});

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
      // Create Razorpay order
      const order = await razorpay.orders.create({
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
   * Verify Razorpay payment signature
   * Called after customer completes payment on Android
   */
  async verifyPayment(
    razorpayPaymentId: string,
    razorpayOrderId: string,
    razorpaySignature: string,
    rideId: string
  ) {
    // Verify signature
    const body = `${razorpayOrderId}|${razorpayPaymentId}`;
    const expectedSignature = crypto
      .createHmac('sha256', config.razorpay.keySecret)
      .update(body)
      .digest('hex');

    if (expectedSignature !== razorpaySignature) {
      logger.warn('Payment signature verification failed', { rideId, razorpayPaymentId });
      throw ApiError.badRequest('Payment verification failed — invalid signature');
    }

    // Update ride payment status
    const ride = await prisma.ride.update({
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
      rideId: ride.id,
      paymentStatus: ride.paymentStatus,
      amount: ride.finalFare,
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
   * Handle Razorpay webhook events
   * Razorpay sends payment status updates here
   */
  async handleWebhook(payload: Record<string, unknown>, signature: string) {
    // Verify webhook signature
    const expectedSignature = crypto
      .createHmac('sha256', config.razorpay.webhookSecret)
      .update(JSON.stringify(payload))
      .digest('hex');

    if (expectedSignature !== signature) {
      logger.warn('Invalid webhook signature');
      throw ApiError.unauthorized('Invalid webhook signature');
    }

    const event = payload.event as string;
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const paymentEntity = (payload as any)?.payload?.payment?.entity;

    if (!paymentEntity) {
      logger.warn('Webhook payload missing payment entity', { event });
      return { received: true };
    }

    switch (event) {
      case 'payment.captured': {
        // Payment successful — update ride
        const orderId = paymentEntity.order_id;
        const ride = await prisma.ride.findFirst({
          where: { razorpayOrderId: orderId },
        });

        if (ride) {
          await prisma.ride.update({
            where: { id: ride.id },
            data: {
              razorpayPaymentId: paymentEntity.id,
              paymentStatus: PaymentStatus.COMPLETED,
            },
          });
          logger.info('Webhook: payment captured', { rideId: ride.id });
        }
        break;
      }

      case 'payment.failed': {
        const orderId = paymentEntity.order_id;
        const ride = await prisma.ride.findFirst({
          where: { razorpayOrderId: orderId },
        });

        if (ride) {
          await prisma.ride.update({
            where: { id: ride.id },
            data: { paymentStatus: PaymentStatus.FAILED },
          });
          logger.warn('Webhook: payment failed', { rideId: ride.id });
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
