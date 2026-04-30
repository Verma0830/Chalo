// ============================================================
// Chalo Backend — KYC Provider Interface
// Pluggable: ManualKYCProvider (V1) or SurepassKYCProvider (V2)
// ============================================================

export interface KYCResult {
  verified: boolean;
  confidence: number | null;   // 0.0 – 1.0, null if provider can't score
  provider: string;
  rawResponse?: unknown;       // Store the raw API response in verificationMetadata
}

export interface FaceMatchResult {
  matched: boolean;
  confidence: number | null;
  provider: string;
  rawResponse?: unknown;
}

export interface KYCProvider {
  /**
   * Verify a document (Aadhaar / DL / RC) by URL or number.
   * Returns a result — never throws; errors are captured as unverified.
   */
  verifyDocument(params: {
    documentType: 'aadhaar' | 'license' | 'rc';
    documentNumber: string;
    documentUrl?: string;
  }): Promise<KYCResult>;

  /**
   * Match a selfie against a document photo.
   */
  matchFace(params: {
    selfieUrl: string;
    documentUrl: string;
  }): Promise<FaceMatchResult>;
}
