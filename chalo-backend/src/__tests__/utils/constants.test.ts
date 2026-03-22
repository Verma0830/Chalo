// ============================================================
// Tests — Application Constants (BusinessLogic sanity checks)
// Ensures all business-critical constants match locked decisions
// ============================================================

import CONSTANTS from '../../utils/constants';

describe('CONSTANTS — locked business decisions', () => {
  it('commission is 15%', () => {
    expect(CONSTANTS.DEFAULT_COMMISSION_PERCENTAGE).toBe(15);
  });

  it('weekly subscription is ₹199', () => {
    expect(CONSTANTS.DEFAULT_SUBSCRIPTION_FEE_WEEKLY).toBe(199);
  });

  it('settlement is T+2 days', () => {
    expect(CONSTANTS.SETTLEMENT_DAYS).toBe(2);
  });

  it('minimum fare is ₹30', () => {
    expect(CONSTANTS.MIN_FARE).toBe(30);
  });

  it('base fare per km is ₹12', () => {
    expect(CONSTANTS.BASE_FARE_PER_KM).toBe(12);
  });

  it('base fare per minute is ₹2', () => {
    expect(CONSTANTS.BASE_FARE_PER_MIN).toBe(2);
  });

  it('booking fee is ₹5', () => {
    expect(CONSTANTS.BOOKING_FEE).toBe(5);
  });
});

describe('CONSTANTS — surge pricing bounds', () => {
  it('max surge multiplier is 2.0', () => {
    expect(CONSTANTS.SURGE_MAX_MULTIPLIER).toBe(2.0);
  });

  it('min surge multiplier is 1.0 (no discount)', () => {
    expect(CONSTANTS.SURGE_MIN_MULTIPLIER).toBe(1.0);
  });
});

describe('CONSTANTS — OTP', () => {
  it('OTP length is 6', () => {
    expect(CONSTANTS.OTP_LENGTH).toBe(6);
  });

  it('OTP expires in 5 minutes', () => {
    expect(CONSTANTS.OTP_EXPIRY_MINS).toBe(5);
  });

  it('max OTP attempts is 3', () => {
    expect(CONSTANTS.MAX_OTP_ATTEMPTS).toBe(3);
  });

  it('phone regex validates correct Indian number', () => {
    expect(CONSTANTS.PHONE_REGEX.test('+919876543210')).toBe(true);
    expect(CONSTANTS.PHONE_REGEX.test('9876543210')).toBe(false);
    expect(CONSTANTS.PHONE_REGEX.test('+915512345678')).toBe(false); // starts with 5
  });
});

describe('CONSTANTS — pagination', () => {
  it('default page is 1', () => {
    expect(CONSTANTS.DEFAULT_PAGE).toBe(1);
  });

  it('default limit is 20', () => {
    expect(CONSTANTS.DEFAULT_LIMIT).toBe(20);
  });

  it('max limit is 100', () => {
    expect(CONSTANTS.MAX_LIMIT).toBe(100);
  });
});

describe('CONSTANTS — rating bounds', () => {
  it('min rating is 1', () => {
    expect(CONSTANTS.MIN_RATING).toBe(1);
  });

  it('max rating is 5', () => {
    expect(CONSTANTS.MAX_RATING).toBe(5);
  });
});

describe('CONSTANTS — schedule window', () => {
  it('max schedule days is 7', () => {
    expect(CONSTANTS.MAX_SCHEDULE_DAYS).toBe(7);
  });
});

describe('CONSTANTS — config keys', () => {
  it('all expected config keys are present', () => {
    const keys = CONSTANTS.CONFIG_KEYS;
    expect(keys.COMMISSION_PERCENTAGE).toBe('commission_percentage');
    expect(keys.SUBSCRIPTION_FEE_WEEKLY).toBe('subscription_fee_weekly');
    expect(keys.SURGE_ENABLED).toBe('surge_enabled');
    expect(keys.SURGE_MULTIPLIER).toBe('surge_multiplier');
    expect(keys.MIN_FARE).toBe('min_fare');
    expect(keys.BASE_FARE_PER_KM).toBe('base_fare_per_km');
    expect(keys.BASE_FARE_PER_MIN).toBe('base_fare_per_min');
    expect(keys.SETTLEMENT_DAYS).toBe('settlement_days');
  });
});
