// ============================================================
// Chalo Backend — Fare Service
// Fare estimation, surge calculation, commission logic
// All business rules for pricing live here
// ============================================================

import prisma from '../config/database';
import config from '../config';
import CONSTANTS from '../utils/constants';
import { calculateBaseFare, applySurge, calculateCommission, haversineDistance } from '../utils/helpers';
import { FareEstimate, Location } from '../types';
import logger from '../config/logger';

export class FareService {
  /**
   * Get fare estimate for a ride
   * Called before booking to show customer the expected fare
   */
  async estimateFare(
    pickup: Location,
    drop: Location
  ): Promise<FareEstimate> {
    // Get route details from Google Maps Directions API
    const routeDetails = await this.getRouteDetails(pickup, drop);

    // Fetch current config from DB (allows runtime changes without redeploy)
    const runtimeConfig = await this.getRuntimeConfig();

    // Compute each component once — no duplicate arithmetic
    const { distanceKm, durationMins } = routeDetails;

    let surgeMultiplier = 1.0;
    if (runtimeConfig.surgeEnabled) {
      surgeMultiplier = await this.calculateSurgeMultiplier(pickup);
    }

    // distanceFare and timeFare are computed here (and only here) for both
    // the baseFare total and the itemised breakdown in the return value.
    const distanceFare = Math.round(distanceKm * runtimeConfig.baseFarePerKm);
    const timeFare = Math.round(durationMins * runtimeConfig.baseFarePerMin);
    const rawBase = distanceFare + timeFare + CONSTANTS.BOOKING_FEE;
    const baseFare = Math.max(rawBase, CONSTANTS.MIN_FARE);
    const surgeAmount = surgeMultiplier > 1 ? Math.round(baseFare * (surgeMultiplier - 1)) : 0;
    const totalFare = applySurge(baseFare, surgeMultiplier);

    return {
      baseFare,
      distanceFare,
      timeFare,
      surgeMultiplier,
      surgeAmount,
      totalFare,
      distanceKm,
      durationMins,
      currency: CONSTANTS.CURRENCY,
    };
  }

  /**
   * Calculate final fare after ride completion
   * Uses actual distance/duration if available
   */
  async calculateFinalFare(
    distanceKm: number,
    durationMins: number,
    surgeMultiplier: number,
    driverPlanType: 'COMMISSION' | 'SUBSCRIPTION'
  ): Promise<{
    finalFare: number;
    commissionAmount: number;
    driverEarning: number;
  }> {
    const runtimeConfig = await this.getRuntimeConfig();

    const baseFare = calculateBaseFare(
      distanceKm,
      durationMins,
      runtimeConfig.baseFarePerKm,
      runtimeConfig.baseFarePerMin
    );

    const finalFare = applySurge(baseFare, surgeMultiplier);

    // Commission depends on driver's plan
    let commissionAmount = 0;
    let driverEarning = finalFare;

    if (driverPlanType === 'COMMISSION') {
      commissionAmount = calculateCommission(finalFare, runtimeConfig.commissionPercentage);
      driverEarning = finalFare - commissionAmount;
    }
    // Subscription drivers keep 100%

    return {
      finalFare,
      commissionAmount,
      driverEarning,
    };
  }

  /**
   * Calculate surge multiplier based on demand/supply in an area
   * V1: Time-based surge (peak hours). V2: Dynamic demand-based.
   */
  private async calculateSurgeMultiplier(_pickup: Location): Promise<number> {
    const now = new Date();
    const hour = now.getHours();

    // V1 simple time-based surge rules
    // Morning rush: 8-10 AM → 1.3x
    // Evening rush: 5-8 PM → 1.5x
    // Late night: 10 PM - 5 AM → 1.3x
    // Normal: 1.0x
    if (hour >= 8 && hour < 10) return 1.3;
    if (hour >= 17 && hour < 20) return 1.5;
    if (hour >= 22 || hour < 5) return 1.3;

    // TODO V2: Query active rides vs available drivers in the area
    // If demand/supply ratio > threshold, apply dynamic surge

    return 1.0;
  }

  /**
   * Get route details from Google Maps Directions API
   * Returns distance in km and duration in minutes
   */
  private async getRouteDetails(
    pickup: Location,
    drop: Location
  ): Promise<{ distanceKm: number; durationMins: number; polyline: string }> {
    try {
      // Use haversineDistance — static import, not a dynamic require
      const straightLineKm = haversineDistance(
        pickup.lat,
        pickup.lng,
        drop.lat,
        drop.lng
      );

      // Road factor: actual road distance is ~1.3x straight-line distance in Indian cities
      const roadFactorKm = Number((straightLineKm * 1.3).toFixed(2));

      // Assume average speed of 25 km/h in Faridabad traffic
      const durationMins = Math.ceil((roadFactorKm / 25) * 60);

      return {
        distanceKm: roadFactorKm,
        durationMins: Math.max(durationMins, 3), // Minimum 3 minutes
        polyline: '', // Will be populated when Google Maps API is integrated
      };
    } catch (error) {
      logger.error('Failed to get route details', { error, pickup, drop });
      throw error;
    }
  }

  /**
   * In-memory cache for runtime config (avoids DB query on every fare estimate)
   * Refreshes every 60 seconds
   */
  private configCache: {
    data: { baseFarePerKm: number; baseFarePerMin: number; commissionPercentage: number; surgeEnabled: boolean };
    expiresAt: number;
  } | null = null;
  private static CONFIG_CACHE_TTL_MS = 60_000; // 1 minute

  /**
   * Fetch runtime-configurable business rules from DB (with in-memory cache)
   * Falls back to env/defaults if DB config not found
   */
  private async getRuntimeConfig(): Promise<{
    baseFarePerKm: number;
    baseFarePerMin: number;
    commissionPercentage: number;
    surgeEnabled: boolean;
  }> {
    // Return cached config if still valid
    if (this.configCache && Date.now() < this.configCache.expiresAt) {
      return this.configCache.data;
    }

    const data = await this.fetchRuntimeConfig();
    this.configCache = { data, expiresAt: Date.now() + FareService.CONFIG_CACHE_TTL_MS };
    return data;
  }

  /**
   * Actually fetch config from DB
   */
  private async fetchRuntimeConfig(): Promise<{
    baseFarePerKm: number;
    baseFarePerMin: number;
    commissionPercentage: number;
    surgeEnabled: boolean;
  }> {
    try {
      const configs = await prisma.platformConfig.findMany({
        where: {
          key: {
            in: [
              CONSTANTS.CONFIG_KEYS.BASE_FARE_PER_KM,
              CONSTANTS.CONFIG_KEYS.BASE_FARE_PER_MIN,
              CONSTANTS.CONFIG_KEYS.COMMISSION_PERCENTAGE,
              CONSTANTS.CONFIG_KEYS.SURGE_ENABLED,
            ],
          },
        },
      });

      const configMap = new Map(configs.map((c) => [c.key, c.value]));

      return {
        baseFarePerKm:
          Number(configMap.get(CONSTANTS.CONFIG_KEYS.BASE_FARE_PER_KM)) ||
          config.business.baseFarePerKm,
        baseFarePerMin:
          Number(configMap.get(CONSTANTS.CONFIG_KEYS.BASE_FARE_PER_MIN)) ||
          config.business.baseFarePerMin,
        commissionPercentage:
          Number(configMap.get(CONSTANTS.CONFIG_KEYS.COMMISSION_PERCENTAGE)) ||
          config.business.commissionPercentage,
        surgeEnabled:
          configMap.has(CONSTANTS.CONFIG_KEYS.SURGE_ENABLED)
            ? configMap.get(CONSTANTS.CONFIG_KEYS.SURGE_ENABLED) === 'true'
            : config.business.surgeEnabled,
      };
    } catch (error) {
      logger.warn('Failed to fetch runtime config — using defaults', { error });
      return {
        baseFarePerKm: config.business.baseFarePerKm,
        baseFarePerMin: config.business.baseFarePerMin,
        commissionPercentage: config.business.commissionPercentage,
        surgeEnabled: config.business.surgeEnabled,
      };
    }
  }
}

export const fareService = new FareService();
export default fareService;
