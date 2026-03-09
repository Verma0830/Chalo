// ============================================================
// Chalo Backend — TypeScript Types & Interfaces
// Central type definitions used across the application
// ============================================================

import { Request } from 'express';
import { UserRole } from '@prisma/client';

// -------------------------------------------------------
// Auth Types
// -------------------------------------------------------

export interface TokenUser {
  id: string;
  phone: string;
  name: string | null;
  role: UserRole;
  languagePref: string;
}

export interface AuthenticatedRequest extends Request {
  user: TokenUser;
}

// -------------------------------------------------------
// API Response Types
// -------------------------------------------------------

export interface ApiResponseData<T = unknown> {
  success: boolean;
  statusCode: number;
  message: string;
  data?: T;
  meta?: PaginationMeta;
  timestamp: string;
}

export interface PaginationMeta {
  page: number;
  limit: number;
  total: number;
  totalPages: number;
  hasNext: boolean;
  hasPrev: boolean;
}

export interface PaginationQuery {
  page: number;
  limit: number;
}

// -------------------------------------------------------
// Ride Types
// -------------------------------------------------------

export interface Location {
  lat: number;
  lng: number;
  address: string;
}

export interface FareEstimate {
  baseFare: number;
  distanceFare: number;
  timeFare: number;
  surgeMultiplier: number;
  surgeAmount: number;
  totalFare: number;
  distanceKm: number;
  durationMins: number;
  currency: string;
  /** GST amount (5% of totalFare) — internal accounting only, not exposed to customer/driver */
  gstAmount: number;
}

export interface RideRequest {
  customerId: string;
  pickup: Location;
  drop: Location;
  paymentMethod: 'CASH' | 'UPI';
  isScheduled: boolean;
  scheduledAt?: Date;
}

export interface DriverSearchResult {
  driverId: string;
  driverProfileId: string;
  name: string;
  phone: string;
  vehicleNumber: string;
  vehicleModel: string;
  rating: number;
  distanceKm: number;
  etaMins: number;
  photoUrl?: string;
}

// -------------------------------------------------------
// Notification Types
// -------------------------------------------------------

export interface PushNotificationPayload {
  userId: string;
  title: string;
  body: string;
  data?: Record<string, string>;
}

// -------------------------------------------------------
// Payment Types
// -------------------------------------------------------

export interface RazorpayOrderParams {
  amount: number;       // In paise (₹1 = 100 paise)
  currency: string;
  receipt: string;
  rideId: string;
}

export interface RazorpayWebhookPayload {
  event: string;
  payload: {
    payment: {
      entity: {
        id: string;
        order_id: string;
        amount: number;
        status: string;
        method: string;
      };
    };
  };
}

// -------------------------------------------------------
// Google Maps Types
// -------------------------------------------------------

export interface DirectionsResult {
  distanceMeters: number;
  durationSeconds: number;
  polyline: string;
}

// -------------------------------------------------------
// SOS Types
// -------------------------------------------------------

export interface SOSAlertPayload {
  rideId: string;
  customerName: string;
  driverName: string;
  vehicleNumber: string;
  liveLocationUrl: string;
  timestamp: string;
}
