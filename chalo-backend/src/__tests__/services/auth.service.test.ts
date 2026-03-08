// ============================================================
// Tests — Auth Service — registerDriver()
// Tests driver registration with mocked DB
// ============================================================

import { UserRole } from '@prisma/client';

// Mock dependencies
jest.mock('../../config/database', () => {
  const mockPrisma: Record<string, unknown> = {
    oTPVerification: {
      findFirst: jest.fn(),
      create: jest.fn(),
      count: jest.fn(),
      update: jest.fn(),
      deleteMany: jest.fn(),
    },
    user: {
      findUnique: jest.fn(),
      create: jest.fn(),
      update: jest.fn(),
    },
    $transaction: jest.fn(),
  };
  (mockPrisma.$transaction as jest.Mock).mockImplementation(
    (fn: (tx: typeof mockPrisma) => Promise<unknown>) => fn(mockPrisma)
  );
  return {
    __esModule: true,
    default: mockPrisma,
  };
});

jest.mock('../../config', () => ({
  __esModule: true,
  default: { isDev: true },
}));

jest.mock('../../config/logger', () => ({
  __esModule: true,
  default: {
    info: jest.fn(),
    warn: jest.fn(),
    error: jest.fn(),
    debug: jest.fn(),
  },
}));

import prisma from '../../config/database';
import { AuthService } from '../../services/auth.service';

const authService = new AuthService();

// ─── Helpers ──────────────────────────────────────────────────
import crypto from 'crypto';
function hashOTP(otp: string) {
  return crypto.createHash('sha256').update(otp).digest('hex');
}

const VALID_OTP = '4242';
const HASHED_OTP = hashOTP(VALID_OTP);

const mockOTPRecord = {
  id: 'otp_001',
  phone: '+919876543210',
  otpCode: HASHED_OTP,
  verified: false,
  attempts: 0,
  expiresAt: new Date(Date.now() + 5 * 60 * 1000), // 5 min from now
};

describe('AuthService.registerDriver', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('throws INVALID_OTP when no matching OTP record exists', async () => {
    // Transaction returns null — no OTP found
    (prisma.$transaction as jest.Mock).mockResolvedValue(null);
    // Expired OTP check also returns null
    (prisma.oTPVerification.findFirst as jest.Mock).mockResolvedValue(null);

    await expect(
      authService.registerDriver({ phone: '+919876543210', otp: '0000', name: 'Test Driver' })
    ).rejects.toMatchObject({ message: expect.stringContaining('Invalid OTP') });
  });

  it('throws when OTP has expired', async () => {
    // Transaction returns null (no valid OTP in window)
    (prisma.$transaction as jest.Mock).mockResolvedValue(null);
    // Expired record exists
    (prisma.oTPVerification.findFirst as jest.Mock).mockResolvedValue({
      ...mockOTPRecord,
      expiresAt: new Date(Date.now() - 1000), // already expired
    });

    await expect(
      authService.registerDriver({ phone: '+919876543210', otp: VALID_OTP, name: 'Test Driver' })
    ).rejects.toMatchObject({ message: expect.stringContaining('expired') });
  });

  it('throws TOO_MANY_REQUESTS when max attempts exceeded', async () => {
    (prisma.$transaction as jest.Mock).mockResolvedValue('MAX_ATTEMPTS');

    await expect(
      authService.registerDriver({ phone: '+919876543210', otp: VALID_OTP, name: 'Test Driver' })
    ).rejects.toMatchObject({ message: expect.stringContaining('attempts') });
  });

  it('throws CONFLICT when phone is already registered as CUSTOMER', async () => {
    (prisma.$transaction as jest.Mock).mockResolvedValue(mockOTPRecord);
    (prisma.user.findUnique as jest.Mock).mockResolvedValue({
      id: 'user_001',
      phone: '+919876543210',
      role: UserRole.CUSTOMER,
      name: 'Existing Customer',
    });

    await expect(
      authService.registerDriver({ phone: '+919876543210', otp: VALID_OTP, name: 'Test Driver' })
    ).rejects.toMatchObject({ message: expect.stringContaining('customer') });
  });

  it('returns isNewUser=false when driver is already registered (idempotent)', async () => {
    (prisma.$transaction as jest.Mock).mockResolvedValue(mockOTPRecord);
    (prisma.user.findUnique as jest.Mock).mockResolvedValue({
      id: 'user_002',
      phone: '+919876543210',
      role: UserRole.DRIVER,
      name: 'Existing Driver',
    });
    (prisma.user.update as jest.Mock).mockResolvedValue({});

    const result = await authService.registerDriver({
      phone: '+919876543210',
      otp: VALID_OTP,
      name: 'Test Driver',
    });

    expect(result.isNewUser).toBe(false);
    expect(result.user.role).toBe(UserRole.DRIVER);
    expect(result.user.id).toBe('user_002');
  });

  it('creates new DRIVER user + DriverProfile on first registration', async () => {
    (prisma.$transaction as jest.Mock)
      // First call: OTP verification transaction
      .mockResolvedValueOnce(mockOTPRecord)
      // Second call: user creation transaction
      .mockResolvedValueOnce({
        id: 'user_new_001',
        phone: '+919876543210',
        name: 'New Driver',
        role: UserRole.DRIVER,
      });

    // No existing user
    (prisma.user.findUnique as jest.Mock).mockResolvedValue(null);

    const result = await authService.registerDriver({
      phone: '+919876543210',
      otp: VALID_OTP,
      name: 'New Driver',
    });

    expect(result.isNewUser).toBe(true);
    expect(result.user.role).toBe(UserRole.DRIVER);
    expect(result.user.phone).toBe('+919876543210');
    expect(result.user.name).toBe('New Driver');
  });
});
