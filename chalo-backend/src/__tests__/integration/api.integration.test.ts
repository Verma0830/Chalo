// ============================================================
// Tests — Integration Tests for API Endpoints
// Uses supertest to test HTTP endpoints end-to-end
// ============================================================

import request from 'supertest';
import { createTestApp } from '../../app';

jest.mock('../../config/database', () => ({
  __esModule: true,
  default: {
    oTPVerification: {
      count: jest.fn().mockResolvedValue(0),
      create: jest.fn().mockResolvedValue({ id: 'otp_1' }),
      findFirst: jest.fn().mockResolvedValue(null),
      update: jest.fn().mockResolvedValue({}),
    },
    user: {
      findUnique: jest.fn().mockResolvedValue(null),
      create: jest.fn().mockResolvedValue({
        id: 'user_1',
        phone: '+919876543210',
        name: null,
        role: 'CUSTOMER',
      }),
      update: jest.fn().mockResolvedValue({}),
    },
    customerProfile: {
      findUnique: jest.fn().mockResolvedValue(null),
      update: jest.fn().mockResolvedValue({}),
    },
  },
  prisma: {
    $queryRaw: jest.fn().mockResolvedValue([{ '1': 1 }]),
  },
  disconnectDatabase: jest.fn().mockResolvedValue(undefined),
}));

jest.mock('../../config/redis', () => ({
  pingRedis: jest.fn().mockResolvedValue(true),
  getRedisClient: jest.fn().mockReturnValue(null),
  isRedisReady: jest.fn().mockReturnValue(false),
}));

describe('API Integration Tests', () => {
  const app = createTestApp();

  describe('Health Check', () => {
    it('GET /health returns 200 and status ok', async () => {
      const response = await request(app)
        .get('/health')
        .expect('Content-Type', /json/)
        .expect(200);

      expect(response.body.status).toBe('ok');
      expect(response.body.checks.database).toBe('ok');
    });
  });

  describe('Request ID Middleware', () => {
    it('adds X-Request-Id header to response', async () => {
      const response = await request(app).get('/health');

      expect(response.headers['x-request-id']).toBe('test-uuid-0000-0000-0000-000000000000');
    });

    it('accepts upstream X-Request-Id header', async () => {
      const customId = 'custom-request-id-123';
      const response = await request(app)
        .get('/health')
        .set('X-Request-Id', customId);

      expect(response.headers['x-request-id']).toBe(customId);
    });
  });

  describe('Auth Endpoints', () => {
    it('POST /api/v1/auth/otp/send validates phone format', async () => {
      const response = await request(app)
        .post('/api/v1/auth/otp/send')
        .send({ phone: 'invalid' })
        .expect(400);

      expect(response.body.success).toBe(false);
      expect(response.body.code).toBe('VALIDATION_ERROR');
    });

    it('POST /api/v1/auth/otp/send returns 200 for valid phone', async () => {
      const response = await request(app)
        .post('/api/v1/auth/otp/send')
        .send({ phone: '+919876543210' })
        .expect(200);

      expect(response.body.success).toBe(true);
      expect(response.body.message).toBe('OTP sent successfully');
    });
  });

  describe('Body Size Limits', () => {
    it('rejects oversized payloads', async () => {
      const largePayload = { data: 'x'.repeat(200 * 1024) };

      const response = await request(app)
        .post('/api/v1/auth/otp/send')
        .send(largePayload);

      expect(response.status).toBe(413);
    });
  });

  describe('404 Handling', () => {
    it('returns 404 for unknown routes', async () => {
      const response = await request(app)
        .get('/api/v1/nonexistent')
        .expect(404);

      expect(response.body.code).toBe('NOT_FOUND');
      expect(response.body.requestId).toBeDefined();
    });
  });

  describe('CORS Headers', () => {
    it('allows Idempotency-Key header', async () => {
      const response = await request(app)
        .options('/api/v1/rides')
        .set('Origin', 'http://localhost:3000')
        .set('Access-Control-Request-Headers', 'Idempotency-Key');

      expect(response.headers['access-control-allow-headers']).toMatch(/idempotency-key/i);
    });
  });

  describe('Protected Endpoints', () => {
    it('requires authentication for ride creation', async () => {
      const response = await request(app)
        .post('/api/v1/rides')
        .send({
          pickup: { lat: 28.4, lng: 77.3, address: 'Test' },
          drop: { lat: 28.5, lng: 77.4, address: 'Drop' },
          paymentMethod: 'CASH',
        })
        .expect(401);

      expect(response.body.success).toBe(false);
    });

    it('requires authentication for ride history', async () => {
      const response = await request(app)
        .get('/api/v1/rides/history')
        .expect(401);

      expect(response.body.success).toBe(false);
    });

    it('requires authentication for notifications', async () => {
      const response = await request(app)
        .get('/api/v1/notifications')
        .expect(401);

      expect(response.body.success).toBe(false);
    });

    it('rejects malformed Bearer token', async () => {
      const response = await request(app)
        .get('/api/v1/rides/history')
        .set('Authorization', 'Bearer invalid-token-here')
        .expect(401);

      expect(response.body.success).toBe(false);
    });

    it('rejects missing Bearer prefix', async () => {
      const response = await request(app)
        .get('/api/v1/rides/history')
        .set('Authorization', 'some-token-without-bearer')
        .expect(401);

      expect(response.body.success).toBe(false);
    });
  });
});
