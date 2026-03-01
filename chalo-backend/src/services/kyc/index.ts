// ============================================================
// Chalo Backend — KYC Provider Factory
// Returns SurepassKYCProvider if SUREPASS_API_KEY is set, else ManualKYCProvider
// ============================================================

import { KYCProvider } from './kyc.interface';
import { ManualKYCProvider } from './manual.provider';
import { SurepassKYCProvider } from './surepass.provider';

let _instance: KYCProvider | null = null;

export function getKYCProvider(): KYCProvider {
  if (!_instance) {
    const apiKey = process.env['SUREPASS_API_KEY'];
    _instance = apiKey ? new SurepassKYCProvider(apiKey) : new ManualKYCProvider();
  }
  return _instance;
}

export type { KYCProvider, KYCResult, FaceMatchResult } from './kyc.interface';
