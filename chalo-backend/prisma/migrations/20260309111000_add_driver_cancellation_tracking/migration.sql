-- Track driver-initiated cancellations for quality monitoring
ALTER TABLE "driver_profiles"
ADD COLUMN "driverCancellationCount" INTEGER NOT NULL DEFAULT 0,
ADD COLUMN "driverCancellationCountDaily" INTEGER NOT NULL DEFAULT 0,
ADD COLUMN "driverCancellationLastAt" TIMESTAMP(3);