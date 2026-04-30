-- Public family tracking links for active rides
CREATE TABLE "ride_share_links" (
  "id" TEXT NOT NULL,
  "rideId" TEXT NOT NULL,
  "tokenHash" TEXT NOT NULL,
  "expiresAt" TIMESTAMP(3) NOT NULL,
  "revokedAt" TIMESTAMP(3),
  "createdById" TEXT NOT NULL,
  "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "updatedAt" TIMESTAMP(3) NOT NULL,

  CONSTRAINT "ride_share_links_pkey" PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX "ride_share_links_tokenHash_key" ON "ride_share_links"("tokenHash");
CREATE INDEX "ride_share_links_rideId_revokedAt_idx" ON "ride_share_links"("rideId", "revokedAt");
CREATE INDEX "ride_share_links_expiresAt_idx" ON "ride_share_links"("expiresAt");

ALTER TABLE "ride_share_links"
ADD CONSTRAINT "ride_share_links_rideId_fkey"
FOREIGN KEY ("rideId") REFERENCES "rides"("id") ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE "ride_share_links"
ADD CONSTRAINT "ride_share_links_createdById_fkey"
FOREIGN KEY ("createdById") REFERENCES "users"("id") ON DELETE RESTRICT ON UPDATE CASCADE;