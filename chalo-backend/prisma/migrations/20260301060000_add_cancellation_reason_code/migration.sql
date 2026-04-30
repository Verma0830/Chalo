-- Add structured cancellation reason code to rides table.
-- Drives 3-tier fee logic: driver-fault (waived) / arrived (₹40) / time-based (₹20) / free window (₹0).
-- cancellationReason (String) is kept for optional free-text notes.

CREATE TYPE "CancellationReasonCode" AS ENUM (
  'DRIVER_ASKED_TO_CANCEL',
  'DRIVER_NOT_MOVING',
  'DRIVER_WRONG_VEHICLE',
  'DRIVER_BEHAVIOUR',
  'CHANGED_MIND',
  'BOOKED_BY_MISTAKE',
  'OTHER'
);

ALTER TABLE "rides" ADD COLUMN "cancellationReasonCode" "CancellationReasonCode";
