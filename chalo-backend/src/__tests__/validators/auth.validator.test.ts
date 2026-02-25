// ============================================================
// Tests — Auth Validators (Zod schemas)
// ============================================================

import {
  sendOTPSchema,
  verifyOTPSchema,
  completeProfileSchema,
  updateEmergencyContactSchema,
} from '../../validators/auth.validator';

// ─── sendOTPSchema ────────────────────────────────────────────
describe('sendOTPSchema', () => {
  it('accepts valid Indian mobile number', () => {
    const result = sendOTPSchema.safeParse({ phone: '+919876543210' });
    expect(result.success).toBe(true);
  });

  it('accepts number starting with 6', () => {
    expect(sendOTPSchema.safeParse({ phone: '+916512345678' }).success).toBe(true);
  });

  it('rejects number without country code', () => {
    expect(sendOTPSchema.safeParse({ phone: '9876543210' }).success).toBe(false);
  });

  it('rejects number starting with 5 (invalid prefix)', () => {
    expect(sendOTPSchema.safeParse({ phone: '+915512345678' }).success).toBe(false);
  });

  it('rejects too-short number', () => {
    expect(sendOTPSchema.safeParse({ phone: '+9198765' }).success).toBe(false);
  });

  it('rejects empty string', () => {
    expect(sendOTPSchema.safeParse({ phone: '' }).success).toBe(false);
  });

  it('rejects missing phone field', () => {
    expect(sendOTPSchema.safeParse({}).success).toBe(false);
  });

  it('trims whitespace', () => {
    const result = sendOTPSchema.safeParse({ phone: ' +919876543210 ' });
    expect(result.success).toBe(true);
  });
});

// ─── verifyOTPSchema ──────────────────────────────────────────
describe('verifyOTPSchema', () => {
  const valid = { phone: '+919876543210', otp: '1234' };

  it('accepts valid phone + 4-digit OTP', () => {
    expect(verifyOTPSchema.safeParse(valid).success).toBe(true);
  });

  it('rejects OTP shorter than 4 digits', () => {
    expect(verifyOTPSchema.safeParse({ ...valid, otp: '123' }).success).toBe(false);
  });

  it('rejects OTP longer than 4 digits', () => {
    expect(verifyOTPSchema.safeParse({ ...valid, otp: '12345' }).success).toBe(false);
  });

  it('rejects non-numeric OTP', () => {
    expect(verifyOTPSchema.safeParse({ ...valid, otp: 'ABCD' }).success).toBe(false);
  });

  it('rejects OTP with spaces', () => {
    expect(verifyOTPSchema.safeParse({ ...valid, otp: '12 4' }).success).toBe(false);
  });
});

// ─── completeProfileSchema ────────────────────────────────────
describe('completeProfileSchema', () => {
  it('accepts valid profile with minimum fields', () => {
    const result = completeProfileSchema.safeParse({ name: 'Rajvir' });
    expect(result.success).toBe(true);
  });

  it('defaults languagePref to "pa"', () => {
    const result = completeProfileSchema.safeParse({ name: 'Gurpreet' });
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.languagePref).toBe('pa');
    }
  });

  it('accepts "en" as language preference', () => {
    const result = completeProfileSchema.safeParse({ name: 'Rahul', languagePref: 'en' });
    expect(result.success).toBe(true);
  });

  it('rejects invalid language preference', () => {
    expect(
      completeProfileSchema.safeParse({ name: 'Rahul', languagePref: 'hi' }).success
    ).toBe(false);
  });

  it('rejects name shorter than 2 chars', () => {
    expect(completeProfileSchema.safeParse({ name: 'R' }).success).toBe(false);
  });

  it('rejects name longer than 100 chars', () => {
    expect(completeProfileSchema.safeParse({ name: 'A'.repeat(101) }).success).toBe(false);
  });

  it('accepts empty string email (optional)', () => {
    const result = completeProfileSchema.safeParse({
      name: 'Gurpreet',
      email: '',
    });
    expect(result.success).toBe(true);
  });

  it('accepts valid email', () => {
    const result = completeProfileSchema.safeParse({
      name: 'Gurpreet',
      email: 'gurpreet@example.com',
    });
    expect(result.success).toBe(true);
  });

  it('rejects invalid email format', () => {
    expect(
      completeProfileSchema.safeParse({ name: 'Gurpreet', email: 'not-an-email' }).success
    ).toBe(false);
  });
});

// ─── updateEmergencyContactSchema ────────────────────────────
describe('updateEmergencyContactSchema', () => {
  const valid = {
    emergencyContactName: 'Balvir Singh',
    emergencyContactPhone: '+919876543210',
  };

  it('accepts valid emergency contact', () => {
    expect(updateEmergencyContactSchema.safeParse(valid).success).toBe(true);
  });

  it('rejects short name', () => {
    expect(
      updateEmergencyContactSchema.safeParse({ ...valid, emergencyContactName: 'A' }).success
    ).toBe(false);
  });

  it('rejects invalid phone', () => {
    expect(
      updateEmergencyContactSchema.safeParse({ ...valid, emergencyContactPhone: '9876543210' }).success
    ).toBe(false);
  });
});
