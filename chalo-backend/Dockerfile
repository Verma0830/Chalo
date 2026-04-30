# ============================================================
# Chalo Backend — Multi-stage Dockerfile
# Stage 1: Install + build (dev deps) → Stage 2: Production image
# --platform=linux/amd64 ensures consistent images on Apple Silicon / ARM hosts
# ============================================================

# ---------- Stage 1: Build ----------
FROM --platform=linux/amd64 node:20-alpine AS builder

# OpenSSL must be installed BEFORE prisma generate so Prisma detects the correct
# version (3.x on Alpine) and downloads the matching linux-musl-openssl-3.0.x binary
RUN apk add --no-cache openssl

WORKDIR /app

# Install dependencies first (layer caching)
# --no-audit --no-fund: skip slow npm registry network calls during CI/build
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

# Copy source and build
COPY tsconfig.json tsconfig.build.json ./
COPY prisma ./prisma/
COPY src ./src/

RUN npx prisma generate
RUN npm run build

# ---------- Stage 2: Production ----------
FROM --platform=linux/amd64 node:20-alpine AS production

# OpenSSL is required by the Prisma query engine binary
RUN apk add --no-cache openssl

# Non-root user for security
RUN addgroup -S chalo && adduser -S chalo -G chalo

WORKDIR /app

# Copy only production dependencies
COPY package.json package-lock.json ./
RUN npm ci --omit=dev --no-audit --no-fund && npm cache clean --force

# Copy Prisma schema + generated client (platform-specific binaries from builder)
COPY --from=builder /app/node_modules/.prisma ./node_modules/.prisma
COPY --from=builder /app/node_modules/@prisma/client ./node_modules/@prisma/client
COPY prisma ./prisma/

# Copy compiled JS
COPY --from=builder /app/dist ./dist/

# Give the non-root user ownership of everything it needs at runtime
RUN chown -R chalo:chalo /app

# Switch to non-root user
USER chalo

# Health check — wget is available in alpine
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:5000/health || exit 1

EXPOSE 5000

# NOTE: Migrations must be run via CI/CD before container starts (not here)
# Running migrations at container startup causes race conditions when scaling replicas.
# Use: npx prisma migrate deploy in your CI/CD pipeline or as a Kubernetes init container.
CMD ["node", "dist/server.js"]
