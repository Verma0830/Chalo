// ============================================================
// Chalo Backend — Payment Routes
// ============================================================

import { Router } from 'express';
import express from 'express';
import { paymentController } from '../controllers/payment.controller';
import { authenticate } from '../middleware/auth';
import { validateBody } from '../middleware/validate';
import { webhookRateLimiter } from '../middleware/rateLimiter';
import {
  createPaymentOrderSchema,
  verifyPaymentSchema,
} from '../validators/payment.validator';

const router = Router();

// -------------------------------------------------------
// Protected Routes (auth required)
// -------------------------------------------------------

// Create Razorpay order
router.post(
  '/order',
  authenticate,
  validateBody(createPaymentOrderSchema),
  paymentController.createOrder.bind(paymentController)
);

// Verify payment
router.post(
  '/verify',
  authenticate,
  validateBody(verifyPaymentSchema),
  paymentController.verifyPayment.bind(paymentController)
);

// -------------------------------------------------------
// Webhook (NO auth — verified via Razorpay signature)
// -------------------------------------------------------

router.post(
  '/webhook',
  webhookRateLimiter,
  express.raw({ type: 'application/json' }),
  paymentController.handleWebhook.bind(paymentController)
);

export default router;
