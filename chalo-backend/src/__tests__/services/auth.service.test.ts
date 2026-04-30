// ============================================================
// Tests — Auth Service — registerDriver()
// Tests driver registration with mocked DB
// ============================================================

import { UserRole } from '@prisma/client';

const mockCreateCustomToken = jest.fn();

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
    driverProfile: {
      upsert: jest.fn(),
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

jest.mock('../../config/firebase', () => ({
  __esModule: true,
  getAuth: () => ({
    createCustomToken: mockCreateCustomToken,
  }),
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
    mockCreateCustomToken.mockResolvedValue('firebase-custom-token');
  });

  function mockVerifyOTPTransactionResult(result: unknown) {
    (prisma.$transaction as jest.Mock).mockImplementationOnce(async (fn) => fn(prisma));
    (prisma.oTPVerification.findFirst as jest.Mock).mockResolvedValue(result);
    if (result && result !== 'MAX_ATTEMPTS') {
      (prisma.oTPVerification.update as jest.Mock).mockResolvedValue({});
      (prisma.user.findUnique as jest.Mock).mockResolvedValue({
        id: 'user_001',
        phone: '+919876543210',
        role: UserRole.CUSTOMER,
        name: null,
      });
      (prisma.user.update as jest.Mock).mockResolvedValue({});
    }
  }

  it('throws INVALID_OTP when no matching OTP record exists', async () => {
    mockVerifyOTPTransactionResult(null);
    (prisma.oTPVerification.findFirst as jest.Mock).mockResolvedValueOnce(null).mockResolvedValueOnce(null);

    await expect(
      authService.registerDriver({ phone: '+919876543210', otp: '0000', name: 'Test Driver' })
    ).rejects.toMatchObject({ message: expect.stringContaining('Invalid OTP') });
  });

  it('throws when OTP has expired', async () => {
    mockVerifyOTPTransactionResult(null);
    (prisma.oTPVerification.findFirst as jest.Mock)
      .mockResolvedValueOnce(null)
      .mockResolvedValueOnce({
      ...mockOTPRecord,
      expiresAt: new Date(Date.now() - 1000), // already expired
      });

    await expect(
      authService.registerDriver({ phone: '+919876543210', otp: VALID_OTP, name: 'Test Driver' })
    ).rejects.toMatchObject({ message: expect.stringContaining('expired') });
  });

  it('throws TOO_MANY_REQUESTS when max attempts exceeded', async () => {
    mockVerifyOTPTransactionResult('MAX_ATTEMPTS');

    await expect(
      authService.registerDriver({ phone: '+919876543210', otp: VALID_OTP, name: 'Test Driver' })
    ).rejects.toMatchObject({ message: expect.stringContaining('attempts') });
  });

  it('upgrades an existing customer to DRIVER and returns a token', async () => {
    mockVerifyOTPTransactionResult(mockOTPRecord);
    (prisma.$transaction as jest.Mock).mockImplementationOnce(async (fn) => fn(prisma));
    (prisma.user.update as jest.Mock).mockResolvedValue({
      id: 'user_001',
      phone: '+919876543210',
      role: UserRole.DRIVER,
      name: 'Test Driver',
    });
    (prisma.driverProfile.upsert as jest.Mock).mockResolvedValue({ userId: 'user_001' });

    const result = await authService.registerDriver({
      phone: '+919876543210',
      otp: VALID_OTP,
      name: 'Test Driver',
    });

    expect(result.isNewUser).toBe(false);
    expect(result.token).toBe('firebase-custom-token');
    expect(result.user.role).toBe(UserRole.DRIVER);
    expect(prisma.driverProfile.upsert).toHaveBeenCalledWith({
      where: { userId: 'user_001' },
      update: {},
      create: { userId: 'user_001' },
    });
  });

  it('returns isNewUser=false when driver is already registered and refreshes token data', async () => {
    mockVerifyOTPTransactionResult(mockOTPRecord);
    (prisma.$transaction as jest.Mock).mockImplementationOnce(async (fn) => fn(prisma));
    (prisma.user.update as jest.Mock).mockResolvedValue({
      id: 'user_002',
      phone: '+919876543210',
      role: UserRole.DRIVER,
      name: 'Updated Driver',
    });
    (prisma.driverProfile.upsert as jest.Mock).mockResolvedValue({ userId: 'user_002' });

    const result = await authService.registerDriver({
      phone: '+919876543210',
      otp: VALID_OTP,
      name: 'Updated Driver',
    });

    expect(result.isNewUser).toBe(false);
    expect(result.token).toBe('firebase-custom-token');
    expect(result.user.role).toBe(UserRole.DRIVER);
    expect(result.user.id).toBe('user_002');
  });

  it('upgrades a newly created customer from verifyOTP into a DRIVER account', async () => {
    (prisma.$transaction as jest.Mock).mockImplementationOnce(async (fn) => fn(prisma));
    (prisma.oTPVerification.findFirst as jest.Mock).mockResolvedValue(mockOTPRecord);
    (prisma.oTPVerification.update as jest.Mock).mockResolvedValue({});
    (prisma.user.findUnique as jest.Mock).mockResolvedValueOnce(null);
    (prisma.user.create as jest.Mock).mockResolvedValue({
      id: 'user_new_001',
      phone: '+919876543210',
      role: UserRole.CUSTOMER,
      name: null,
    });
    (prisma.user.update as jest.Mock).mockResolvedValue({
      id: 'user_new_001',
      phone: '+919876543210',
      name: 'New Driver',
      role: UserRole.DRIVER,
    });
    (prisma.$transaction as jest.Mock).mockImplementationOnce(async (fn) => fn(prisma));
    (prisma.driverProfile.upsert as jest.Mock).mockResolvedValue({ userId: 'user_new_001' });

    const result = await authService.registerDriver({
      phone: '+919876543210',
      otp: VALID_OTP,
      name: 'New Driver',
    });

    expect(result.isNewUser).toBe(true);
    expect(result.token).toBe('firebase-custom-token');
    expect(result.user.role).toBe(UserRole.DRIVER);
    expect(result.user.phone).toBe('+919876543210');
    expect(result.user.name).toBe('New Driver');
  });
});
