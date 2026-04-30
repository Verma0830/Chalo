// ============================================================
// Chalo Backend — Manual KYC Provider (V1)
// No API call — always returns unverified so admin must review
// ============================================================

import { KYCProvider, KYCResult, FaceMatchResult } from './kyc.interface';

export class ManualKYCProvider implements KYCProvider {
  async verifyDocument(_params: {
    documentType: 'aadhaar' | 'license' | 'rc';
    documentNumber: string;
    documentUrl?: string;
  }): Promise<KYCResult> {
    return {
      verified: false,
      confidence: null,
      provider: 'manual',
    };
  }

  async matchFace(_params: {
    selfieUrl: string;
    documentUrl: string;
  }): Promise<FaceMatchResult> {
    return {
      matched: false,
      confidence: null,
      provider: 'manual',
    };
  }
}
