-- Add OTP ride start field
ALTER TABLE "rides" ADD COLUMN "rideStartOtp" TEXT;

-- Add driver-to-customer rating fields
ALTER TABLE "rides" ADD COLUMN "driverRating" INTEGER;
ALTER TABLE "rides" ADD COLUMN "driverComment" TEXT;
