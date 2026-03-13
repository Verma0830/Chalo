// ============================================================
// Chalo Backend — Fare Service
// Fare estimation, surge calculation, commission logic
// All business rules for pricing live here
// ============================================================

import prisma from '../config/database';
import config from '../config';
import CONSTANTS from '../utils/constants';
import { calculateBaseFare, applySurge, calculateCommission, haversineDistance } from '../utils/helpers';
import { withCircuitBreakerFallback } from '../utils/circuitBreaker';
import { getRedisClient, isRedisReady } from '../config/redis';
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
    const minimumFare = runtimeConfig.minFare;
    const minimumFareApplied = rawBase < minimumFare;
    const baseFare = Math.max(rawBase, minimumFare);
    const surgeAmount = surgeMultiplier > 1 ? Math.round(baseFare * (surgeMultiplier - 1)) : 0;
    const totalFare = applySurge(baseFare, surgeMultiplier);
    // GST is included in the fare (not added on top) — tracked for accounting only
    const gstAmount = Math.round(totalFare * runtimeConfig.gstPercentage) / 100;

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
      gstAmount,
      minimumFareApplied,
      minimumFare,
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
   * Circuit breaker falls back to Haversine estimate on failure
   */
  private getRouteDetails = withCircuitBreakerFallback<
    [Location, Location],
    { distanceKm: number; durationMins: number; polyline: string }
  >(
    'google-maps-directions',
    async (pickup: Location, drop: Location) => {
      const origin = `${pickup.lat},${pickup.lng}`;
      const destination = `${drop.lat},${drop.lng}`;
      const url = `https://maps.googleapis.com/maps/api/directions/json?origin=${origin}&destination=${destination}&key=${config.googleMaps.apiKey}&mode=driving&region=in`;

      const controller = new AbortController();
      const timeout = setTimeout(
        () => controller.abort(),
        CONSTANTS.GOOGLE_MAPS_TIMEOUT_MS
      );

      try {
        const response = await fetch(url, { signal: controller.signal });
        const json = (await response.json()) as {
          status: string;
          routes: Array<{
            legs: Array<{
              distance: { value: number };
              duration: { value: number };
            }>;
            overview_polyline: { points: string };
          }>;
        };

        if (json.status !== 'OK' || !json.routes.length) {
          throw new Error(`Google Directions API returned status: ${json.status}`);
        }

        const leg = json.routes[0].legs[0];
        return {
          distanceKm: Number((leg.distance.value / 1000).toFixed(2)),
          durationMins: Math.max(Math.ceil(leg.duration.value / 60), 3),
          polyline: json.routes[0].overview_polyline.points,
        };
      } finally {
        clearTimeout(timeout);
      }
    },
    // Fallback: Haversine estimate when Google Maps is unreachable
    (pickup: Location, drop: Location) => {
      logger.warn('Google Maps API unavailable — using Haversine fallback', {
        pickup,
        drop,
      });
      return this.haversineFallback(pickup, drop);
    },
    {
      timeout: CONSTANTS.GOOGLE_MAPS_TIMEOUT_MS,
      errorThresholdPercentage: CONSTANTS.CIRCUIT_BREAKER_THRESHOLD_PERCENT,
      resetTimeout: CONSTANTS.CIRCUIT_BREAKER_RESET_MS,
      volumeThreshold: CONSTANTS.CIRCUIT_BREAKER_MIN_REQUESTS,
    }
  );

  /**
   * Haversine-based route estimate (fallback when Maps API is down)
   * Uses 1.3x road factor for Indian cities and 25 km/h avg speed
   */
  private haversineFallback(
    pickup: Location,
    drop: Location
  ): { distanceKm: number; durationMins: number; polyline: string } {
    const ROAD_FACTOR = 1.3;
    const AVG_SPEED_KMH = 25;
    const MIN_DURATION_MINS = 3;

    const straightLineKm = haversineDistance(
      pickup.lat,
      pickup.lng,
      drop.lat,
      drop.lng
    );
    const roadFactorKm = Number((straightLineKm * ROAD_FACTOR).toFixed(2));
    const durationMins = Math.ceil((roadFactorKm / AVG_SPEED_KMH) * 60);

    return {
      distanceKm: roadFactorKm,
      durationMins: Math.max(durationMins, MIN_DURATION_MINS),
      polyline: '', // No polyline from Haversine
    };
  }

  /**
   * Redis-backed cache for runtime config.
   * Shares config across all instances and falls back to in-memory when Redis is down.
   * TTL: 60 seconds.
   */
  private configCache: {
    data: { baseFarePerKm: number; baseFarePerMin: number; commissionPercentage: number; surgeEnabled: boolean; gstPercentage: number; minFare: number };
    expiresAt: number;
  } | null = null;
  private static CONFIG_CACHE_TTL_SECS = 60; // 1 minute
  private static REDIS_CONFIG_KEY = 'fare:runtime-config';

  /**
   * Fetch runtime-configurable business rules from DB (with Redis + in-memory cache)
   * Falls back to env/defaults if DB config not found
   */
  private async getRuntimeConfig(): Promise<{
    baseFarePerKm: number;
    baseFarePerMin: number;
    commissionPercentage: number;
    surgeEnabled: boolean;
    gstPercentage: number;
    minFare: number;
  }> {
    // L1: In-memory cache (hot path — zero latency)
    if (this.configCache && Date.now() < this.configCache.expiresAt) {
      return this.configCache.data;
    }

    // L2: Redis cache (shared across instances)
    const redis = getRedisClient();
    if (redis && isRedisReady()) {
      try {
        const cached = await redis.get(FareService.REDIS_CONFIG_KEY);
        if (cached) {
          const data = JSON.parse(cached);
          this.configCache = { data, expiresAt: Date.now() + FareService.CONFIG_CACHE_TTL_SECS * 1000 };
          return data;
        }
      } catch (err) {
        logger.warn('FareService Redis config cache read failed', { error: String(err) });
      }
    }

    // L3: Database fetch
    const data = await this.fetchRuntimeConfig();
    this.configCache = { data, expiresAt: Date.now() + FareService.CONFIG_CACHE_TTL_SECS * 1000 };

    // Populate Redis cache (fire-and-forget)
    if (redis && isRedisReady()) {
      redis
        .setEx(FareService.REDIS_CONFIG_KEY, FareService.CONFIG_CACHE_TTL_SECS, JSON.stringify(data))
        .catch((err) => {
          logger.warn('FareService Redis config cache write failed', { error: String(err) });
        });
    }

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
    gstPercentage: number;
    minFare: number;
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
              CONSTANTS.CONFIG_KEYS.GST_PERCENTAGE,
              CONSTANTS.CONFIG_KEYS.MIN_FARE,
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
        gstPercentage:
          Number(configMap.get(CONSTANTS.CONFIG_KEYS.GST_PERCENTAGE)) ||
          CONSTANTS.DEFAULT_GST_PERCENTAGE,
        minFare:
          Number(configMap.get(CONSTANTS.CONFIG_KEYS.MIN_FARE)) ||
          CONSTANTS.MIN_FARE,
      };
    } catch (error) {
      logger.warn('Failed to fetch runtime config — using defaults', { error });
      return {
        baseFarePerKm: config.business.baseFarePerKm,
        baseFarePerMin: config.business.baseFarePerMin,
        commissionPercentage: config.business.commissionPercentage,
        surgeEnabled: config.business.surgeEnabled,
        gstPercentage: CONSTANTS.DEFAULT_GST_PERCENTAGE,
        minFare: CONSTANTS.MIN_FARE,
      };
    }
  }
}

export const fareService = new FareService();
export default fareService;
