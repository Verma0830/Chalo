-- AlterTable: add KYC metadata fields to driver_profiles
ALTER TABLE "driver_profiles"
  ADD COLUMN "verificationNote" TEXT,
  ADD COLUMN "verificationMetadata" JSONB;
