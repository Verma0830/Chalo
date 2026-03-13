-- Migration: add driver accept/decline counters and customer cancellation cooldown
-- Reason: track driver engagement quality + enforce serial canceller policy

-- Driver accept/decline tracking (for acceptance rate calculation)
ALTER TABLE "driver_profiles"
  ADD COLUMN "driverAcceptCount"  INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN "driverDeclineCount" INTEGER NOT NULL DEFAULT 0;

-- Customer serial canceller cooldown
ALTER TABLE "customer_profiles"
  ADD COLUMN "cancellationCooldownUntil" TIMESTAMP(3);
