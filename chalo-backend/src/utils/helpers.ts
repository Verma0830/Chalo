// ============================================================
// Chalo Backend — Helper Utilities
// Pure functions used across the application
// ============================================================

import { PaginationQuery } from '../types';
import CONSTANTS from './constants';

/**
 * Calculate distance between two GPS coordinates using Haversine formula
 * Returns distance in kilometers
 */
export function haversineDistance(
  lat1: number,
  lng1: number,
  lat2: number,
  lng2: number
): number {
  const R = 6371; // Earth's radius in km
  const dLat = toRadians(lat2 - lat1);
  const dLng = toRadians(lng2 - lng1);

  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(toRadians(lat1)) *
      Math.cos(toRadians(lat2)) *
      Math.sin(dLng / 2) *
      Math.sin(dLng / 2);

  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return Number((R * c).toFixed(2));
}

function toRadians(degrees: number): number {
  return degrees * (Math.PI / 180);
}

/**
 * Calculate estimated fare based on distance and duration
 */
export function calculateBaseFare(
  distanceKm: number,
  durationMins: number,
  perKmRate: number = CONSTANTS.BASE_FARE_PER_KM,
  perMinRate: number = CONSTANTS.BASE_FARE_PER_MIN
): number {
  const distanceFare = distanceKm * perKmRate;
  const timeFare = durationMins * perMinRate;
  const total = distanceFare + timeFare + CONSTANTS.BOOKING_FEE;

  return Math.max(total, CONSTANTS.MIN_FARE);
}

/**
 * Apply surge multiplier to base fare
 */
export function applySurge(baseFare: number, multiplier: number): number {
  const clampedMultiplier = Math.min(
    Math.max(multiplier, CONSTANTS.SURGE_MIN_MULTIPLIER),
    CONSTANTS.SURGE_MAX_MULTIPLIER
  );
  return Math.round(baseFare * clampedMultiplier);
}

/**
 * Calculate commission from fare for commission-based drivers
 */
export function calculateCommission(fare: number, commissionPercentage: number): number {
  return Math.round((fare * commissionPercentage) / 100);
}

/**
 * Calculate T+N settlement due date
 */
export function calculateSettlementDate(
  completedAt: Date,
  settlementDays: number = CONSTANTS.SETTLEMENT_DAYS
): Date {
  const dueDate = new Date(completedAt);
  dueDate.setDate(dueDate.getDate() + settlementDays);
  return dueDate;
}

/**
 * Parse and sanitize pagination parameters from query string
 */
export function parsePagination(query: Record<string, unknown>): PaginationQuery {
  const page = Math.max(1, Number(query.page) || CONSTANTS.DEFAULT_PAGE);
  const limit = Math.min(
    CONSTANTS.MAX_LIMIT,
    Math.max(1, Number(query.limit) || CONSTANTS.DEFAULT_LIMIT)
  );

  return { page, limit };
}

/**
 * Calculate Prisma skip value from page + limit
 */
export function paginationToSkip(pagination: PaginationQuery): number {
  return (pagination.page - 1) * pagination.limit;
}

/**
 * Generate a random N-digit OTP
 */
export function generateOTP(length: number = CONSTANTS.OTP_LENGTH): string {
  const min = Math.pow(10, length - 1);
  const max = Math.pow(10, length) - 1;
  return String(Math.floor(min + Math.random() * (max - min + 1)));
}

/**
 * Mask phone number for display: +91 9876543210 → +91 98****3210
 */
export function maskPhone(phone: string): string {
  if (phone.length < 10) return phone;
  const last4 = phone.slice(-4);
  const first5 = phone.slice(0, phone.length - 8);
  return `${first5}****${last4}`;
}

/**
 * Format currency for display
 */
export function formatCurrency(amount: number): string {
  return `${CONSTANTS.CURRENCY_SYMBOL}${amount.toFixed(0)}`;
}

/**
 * Check if a scheduled time is within the allowed window (7 days)
 */
export function isValidScheduleTime(scheduledAt: Date): boolean {
  const now = new Date();
  const maxDate = new Date();
  maxDate.setDate(maxDate.getDate() + CONSTANTS.MAX_SCHEDULE_DAYS);

  return scheduledAt > now && scheduledAt <= maxDate;
}

/**
 * Check if driver is within "arrived" radius of pickup point
 */
export function isDriverNearPickup(
  driverLat: number,
  driverLng: number,
  pickupLat: number,
  pickupLng: number
): boolean {
  const distanceKm = haversineDistance(driverLat, driverLng, pickupLat, pickupLng);
  const distanceMeters = distanceKm * 1000;
  return distanceMeters <= CONSTANTS.DRIVER_ARRIVED_RADIUS_METERS;
}

/**
 * Sleep utility — for retry logic
 */
export function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
