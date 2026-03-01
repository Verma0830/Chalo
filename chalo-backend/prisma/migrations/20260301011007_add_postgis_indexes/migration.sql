-- ============================================================
-- Migration: PostGIS Spatial Indexes for Driver Search
--
-- WHY: The ride matching query uses ST_DWithin() to find drivers
-- within a radius. Without a spatial index Postgres does a full
-- table scan — O(n) with 10,000 online drivers.
-- With a GIST geography index the same query uses an index scan
-- and executes in <5ms even at 50,000 driver records.
--
-- NOTE: No CONCURRENTLY — Prisma runs migrations in transactions,
-- and CONCURRENTLY cannot run inside a transaction block.
-- For zero-downtime production deploys, apply these manually.
-- ============================================================

-- GIST index on driver geography point for proximity search.
-- Uses ST_MakePoint(lng, lat) — note lon/lat order matches PostGIS convention.
CREATE INDEX IF NOT EXISTS idx_driver_profiles_location_gist
ON driver_profiles
USING GIST (
  CAST(ST_SetSRID(ST_MakePoint("currentLng", "currentLat"), 4326) AS geography)
)
WHERE "currentLat" IS NOT NULL AND "currentLng" IS NOT NULL;

-- Partial B-tree index: only online + verified drivers.
-- This is the exact WHERE clause used in the matching query,
-- so Postgres can use this index to pre-filter before the GIST scan.
CREATE INDEX IF NOT EXISTS idx_driver_profiles_online_verified
ON driver_profiles ("isOnline", "verificationStatus")
WHERE "isOnline" = true AND "verificationStatus" = 'VERIFIED';

-- B-tree index on rides for the driver's active ride lookup.
CREATE INDEX IF NOT EXISTS idx_rides_driver_status
ON rides ("driverId", "status")
WHERE "driverId" IS NOT NULL;

-- B-tree index on earnings for the driver earnings dashboard.
CREATE INDEX IF NOT EXISTS idx_earnings_driver_created
ON earnings ("driverProfileId", "createdAt" DESC);

-- B-tree index on withdrawals for the driver's withdrawal history.
CREATE INDEX IF NOT EXISTS idx_withdrawals_driver_status
ON withdrawals ("driverProfileId", "status");
