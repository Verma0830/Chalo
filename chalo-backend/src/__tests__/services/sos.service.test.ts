// ============================================================
// Tests — SOS Service (trigger, resolve, auto-resolve)
// ============================================================

import { RideStatus, SOSStatus } from '@prisma/client';

// ── Mocks ──────────────────────────────────────────────────────
const mockRideFindUnique = jest.fn();
const mockSOSAlertFindFirst = jest.fn();
const mockSOSAlertCreate = jest.fn();
const mockSOSAlertFindUnique = jest.fn();
const mockSOSAlertUpdate = jest.fn();
const mockSOSAlertUpdateMany = jest.fn();
const mockRideUpdate = jest.fn();

jest.mock('../../config/database', () => ({
  __esModule: true,
  default: {
    ride: {
      findUnique: mockRideFindUnique,
      update: mockRideUpdate,
    },
    sOSAlert: {
      findFirst: mockSOSAlertFindFirst,
      create: mockSOSAlertCreate,
      findUnique: mockSOSAlertFindUnique,
      update: mockSOSAlertUpdate,
      updateMany: mockSOSAlertUpdateMany,
    },
  },
}));

const mockSendSOSAlerts = jest.fn().mockResolvedValue(1);
jest.mock('../../services/sms.service', () => ({
  sendSOSAlerts: mockSendSOSAlerts,
}));

jest.mock('../../config/logger', () => ({
  __esModule: true,
  default: { info: jest.fn(), warn: jest.fn(), error: jest.fn() },
}));

// ── Import after mocks ─────────────────────────────────────────
import { SOSService } from '../../services/sos.service';

// ── Shared fixtures ────────────────────────────────────────────
const RIDE_ID = 'clride12345678901234567';
const CUSTOMER_ID = 'clcust12345678901234567';
const DRIVER_ID = 'cldriv12345678901234567';
const SOS_ID = 'clsos123456789012345678';

function buildRide(overrides = {}) {
  return {
    id: RIDE_ID,
    customerId: CUSTOMER_ID,
    driverId: DRIVER_ID,
    status: RideStatus.IN_PROGRESS,
    customer: {
      name: 'Priya Sharma',
      customerProfile: {
        emergencyContactName: 'Ramesh Sharma',
        emergencyContactPhone: '+919876543210',
      },
    },
    driver: {
      name: 'Raj Kumar',
      driverProfile: { vehicleNumber: 'HR 26 AB 1234' },
    },
    ...overrides,
  };
}

function buildSOSAlert(overrides = {}) {
  return {
    id: SOS_ID,
    rideId: RIDE_ID,
    userId: CUSTOMER_ID,
    status: SOSStatus.ACTIVE,
    lat: 28.47,
    lng: 77.40,
    alertSentTo: [],
    ...overrides,
  };
}

describe('SOSService.triggerSOS', () => {
  let service: SOSService;

  beforeEach(() => {
    jest.clearAllMocks();
    service = new SOSService();
    mockRideUpdate.mockResolvedValue({});
  });

  it('creates SOS alert and dispatches SMS for IN_PROGRESS ride', async () => {
    mockRideFindUnique.mockResolvedValue(buildRide());
    mockSOSAlertFindFirst.mockResolvedValue(null); // No active SOS
    mockSOSAlertCreate.mockResolvedValue(buildSOSAlert());

    const result = await service.triggerSOS(RIDE_ID, CUSTOMER_ID, 28.47, 77.40);

    expect(result.status).toBe(SOSStatus.ACTIVE);
    expect(result.sosAlertId).toBe(SOS_ID);
    expect(mockSOSAlertCreate).toHaveBeenCalledTimes(1);
    expect(mockSOSAlertCreate).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({
          rideId: RIDE_ID,
          userId: CUSTOMER_ID,
          lat: 28.47,
          lng: 77.40,
          status: SOSStatus.ACTIVE,
        }),
      })
    );
  });

  it('dispatches SMS to emergency contacts when configured', async () => {
    mockRideFindUnique.mockResolvedValue(buildRide());
    mockSOSAlertFindFirst.mockResolvedValue(null);
    mockSOSAlertCreate.mockResolvedValue(buildSOSAlert());

    await service.triggerSOS(RIDE_ID, CUSTOMER_ID, 28.47, 77.40);

    // SMS dispatch is fire-and-forget, but should be called once
    await new Promise((r) => setTimeout(r, 10)); // Let the Promise chain settle
    expect(mockSendSOSAlerts).toHaveBeenCalledTimes(1);
    expect(mockSendSOSAlerts).toHaveBeenCalledWith(
      expect.objectContaining({
        customerName: 'Priya Sharma',
        driverName: 'Raj Kumar',
        vehicleNumber: 'HR 26 AB 1234',
        lat: 28.47,
        lng: 77.40,
        recipients: expect.arrayContaining([
          expect.objectContaining({ phone: '+919876543210', channel: 'SMS' }),
        ]),
      })
    );
  });

  it('skips SMS when customer has no emergency contact configured', async () => {
    mockRideFindUnique.mockResolvedValue(
      buildRide({
        customer: {
          name: 'Priya Sharma',
          customerProfile: { emergencyContactName: null, emergencyContactPhone: null },
        },
      })
    );
    mockSOSAlertFindFirst.mockResolvedValue(null);
    mockSOSAlertCreate.mockResolvedValue(buildSOSAlert());

    await service.triggerSOS(RIDE_ID, CUSTOMER_ID, 28.47, 77.40);

    await new Promise((r) => setTimeout(r, 10));
    expect(mockSendSOSAlerts).not.toHaveBeenCalled();
  });

  it('throws NOT_FOUND when ride does not exist', async () => {
    mockRideFindUnique.mockResolvedValue(null);

    await expect(service.triggerSOS(RIDE_ID, CUSTOMER_ID, 28.47, 77.40))
      .rejects.toMatchObject({ statusCode: 404 });
  });

  it('throws FORBIDDEN when caller is not a ride participant', async () => {
    mockRideFindUnique.mockResolvedValue(buildRide());

    await expect(service.triggerSOS(RIDE_ID, 'outsider-id', 28.47, 77.40))
      .rejects.toMatchObject({ statusCode: 403 });
  });

  it('throws BAD_REQUEST for ride not in active status (REQUESTED)', async () => {
    mockRideFindUnique.mockResolvedValue(buildRide({ status: RideStatus.REQUESTED }));
    mockSOSAlertFindFirst.mockResolvedValue(null);

    await expect(service.triggerSOS(RIDE_ID, CUSTOMER_ID, 28.47, 77.40))
      .rejects.toMatchObject({ statusCode: 400 });
  });

  it('throws CONFLICT when SOS is already active for this ride', async () => {
    mockRideFindUnique.mockResolvedValue(buildRide());
    mockSOSAlertFindFirst.mockResolvedValue(buildSOSAlert()); // Existing active SOS

    await expect(service.triggerSOS(RIDE_ID, CUSTOMER_ID, 28.47, 77.40))
      .rejects.toMatchObject({ statusCode: 409 });
  });

  it('allows driver to trigger SOS for their own ride', async () => {
    mockRideFindUnique.mockResolvedValue(buildRide());
    mockSOSAlertFindFirst.mockResolvedValue(null);
    mockSOSAlertCreate.mockResolvedValue(buildSOSAlert({ userId: DRIVER_ID }));

    const result = await service.triggerSOS(RIDE_ID, DRIVER_ID, 28.47, 77.40);

    expect(result.status).toBe(SOSStatus.ACTIVE);
  });

  it('allows SOS during DRIVER_ASSIGNED status', async () => {
    mockRideFindUnique.mockResolvedValue(buildRide({ status: RideStatus.DRIVER_ASSIGNED }));
    mockSOSAlertFindFirst.mockResolvedValue(null);
    mockSOSAlertCreate.mockResolvedValue(buildSOSAlert());

    const result = await service.triggerSOS(RIDE_ID, CUSTOMER_ID, 28.47, 77.40);

    expect(result.status).toBe(SOSStatus.ACTIVE);
  });
});

describe('SOSService.resolveSOS', () => {
  let service: SOSService;

  beforeEach(() => {
    jest.clearAllMocks();
    service = new SOSService();
  });

  it('resolves SOS alert when called by the triggering user', async () => {
    mockSOSAlertFindUnique.mockResolvedValue(buildSOSAlert());
    mockSOSAlertUpdate.mockResolvedValue({ ...buildSOSAlert(), status: SOSStatus.RESOLVED });

    const result = await service.resolveSOS(SOS_ID, CUSTOMER_ID);

    expect(result.status).toBe(SOSStatus.RESOLVED);
    expect(mockSOSAlertUpdate).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({ status: SOSStatus.RESOLVED }),
      })
    );
  });

  it('auto-resolves when autoResolve=true (system call on ride completion)', async () => {
    mockSOSAlertFindUnique.mockResolvedValue(buildSOSAlert());
    mockSOSAlertUpdate.mockResolvedValue({ ...buildSOSAlert(), status: SOSStatus.AUTO_RESOLVED });

    const result = await service.resolveSOS(SOS_ID, 'system', true);

    expect(result.status).toBe(SOSStatus.AUTO_RESOLVED);
  });

  it('throws NOT_FOUND when SOS alert does not exist', async () => {
    mockSOSAlertFindUnique.mockResolvedValue(null);

    await expect(service.resolveSOS('nonexistent', CUSTOMER_ID))
      .rejects.toMatchObject({ statusCode: 404 });
  });

  it('throws BAD_REQUEST when SOS is already resolved', async () => {
    mockSOSAlertFindUnique.mockResolvedValue(buildSOSAlert({ status: SOSStatus.RESOLVED }));

    await expect(service.resolveSOS(SOS_ID, CUSTOMER_ID))
      .rejects.toMatchObject({ statusCode: 400 });
  });

  it('throws FORBIDDEN when a different user tries to resolve someone else SOS', async () => {
    mockSOSAlertFindUnique.mockResolvedValue(buildSOSAlert()); // userId = CUSTOMER_ID

    await expect(service.resolveSOS(SOS_ID, 'different-user-id'))
      .rejects.toMatchObject({ statusCode: 403 });
  });
});

describe('SOSService.autoResolveForRide', () => {
  let service: SOSService;

  beforeEach(() => {
    jest.clearAllMocks();
    service = new SOSService();
    mockSOSAlertUpdateMany.mockResolvedValue({ count: 1 });
  });

  it('batch-updates all active SOS alerts for a ride to AUTO_RESOLVED', async () => {
    await service.autoResolveForRide(RIDE_ID);

    expect(mockSOSAlertUpdateMany).toHaveBeenCalledWith({
      where: { rideId: RIDE_ID, status: SOSStatus.ACTIVE },
      data: { status: SOSStatus.AUTO_RESOLVED, resolvedAt: expect.any(Date) },
    });
  });

  it('does not throw when no active SOS alerts exist for the ride', async () => {
    mockSOSAlertUpdateMany.mockResolvedValue({ count: 0 });

    await expect(service.autoResolveForRide(RIDE_ID)).resolves.toBeUndefined();
  });
});
