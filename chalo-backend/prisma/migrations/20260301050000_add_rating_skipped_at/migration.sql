-- Add rating skip tracking to rides table
-- Set when customer explicitly taps "Skip" on the rating prompt (never ask again)
-- NULL = customer has not skipped, NOT NULL = skipped at this timestamp
ALTER TABLE "rides" ADD COLUMN "ratingSkippedAt" TIMESTAMP(3);
