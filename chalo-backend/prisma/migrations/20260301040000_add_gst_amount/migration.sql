-- Add GST amount to rides table
-- Tracks 5% GST on ride fare for internal accounting
-- Not exposed in customer/driver API responses
ALTER TABLE "rides" ADD COLUMN "gstAmount" DOUBLE PRECISION NOT NULL DEFAULT 0;
