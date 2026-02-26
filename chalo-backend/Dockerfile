# ============================================================
# Chalo Backend — Multi-stage Dockerfile
# Stage 1: Install + build (dev deps) → Stage 2: Production image
# ============================================================

# ---------- Stage 1: Build ----------
FROM node:20-alpine AS builder

WORKDIR /app

# Install dependencies first (layer caching)
COPY package.json package-lock.json* ./
RUN npm ci

# Copy source and build
COPY tsconfig.json tsconfig.build.json ./
COPY prisma ./prisma/
COPY src ./src/

RUN npx prisma generate
RUN npm run build

# ---------- Stage 2: Production ----------
FROM node:20-alpine AS production

# Non-root user for security
RUN addgroup -S chalo && adduser -S chalo -G chalo

WORKDIR /app

# Copy only production dependencies
COPY package.json package-lock.json* ./
RUN npm ci --omit=dev && npm cache clean --force

# Copy Prisma schema + generated client
COPY --from=builder /app/node_modules/.prisma ./node_modules/.prisma
COPY --from=builder /app/node_modules/@prisma/client ./node_modules/@prisma/client
COPY prisma ./prisma/

# Copy compiled JS
COPY --from=builder /app/dist ./dist/

# Switch to non-root user
USER chalo

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:5000/health || exit 1

EXPOSE 5000

CMD ["node", "dist/server.js"]
