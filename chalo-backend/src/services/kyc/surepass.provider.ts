// ============================================================
// Chalo Backend — Surepass KYC Provider (V2)
// Requires SUREPASS_API_KEY env var. On any error, returns unverified.
// ============================================================

import { KYCProvider, KYCResult, FaceMatchResult } from './kyc.interface';
import logger from '../../config/logger';

const BASE_URL = 'https://kyc-api.surepass.io/api/v1';
const TIMEOUT_MS = 15_000;

export class SurepassKYCProvider implements KYCProvider {
  private readonly apiKey: string;

  constructor(apiKey: string) {
    this.apiKey = apiKey;
  }

  private async post<T>(path: string, body: Record<string, unknown>): Promise<T> {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), TIMEOUT_MS);

    try {
      const res = await fetch(`${BASE_URL}${path}`, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${this.apiKey}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(body),
        signal: controller.signal,
      });

      const data = await res.json() as T;
      return data;
    } finally {
      clearTimeout(timeout);
    }
  }

  async verifyDocument(params: {
    documentType: 'aadhaar' | 'license' | 'rc';
    documentNumber: string;
    documentUrl?: string;
  }): Promise<KYCResult> {
    try {
      const pathMap: Record<string, string> = {
        aadhaar: '/aadhaar-v3/generate-otp',  // OCR path in practice
        license:  '/driving-license',
        rc:       '/rc-lite',
      };

      const path = pathMap[params.documentType];
      const payload: Record<string, unknown> = {
        id_number: params.documentNumber,
      };

      const raw = await this.post<{ status_code: number; success: boolean; data?: unknown }>(path, payload);
      const verified = raw.success === true && raw.status_code === 200;

      return {
        verified,
        confidence: verified ? 1.0 : 0.0,
        provider: 'surepass',
        rawResponse: raw,
      };
    } catch (err) {
      logger.warn('Surepass verifyDocument failed', {
        documentType: params.documentType,
        error: String(err),
      });
      return { verified: false, confidence: null, provider: 'surepass' };
    }
  }

  async matchFace(params: {
    selfieUrl: string;
    documentUrl: string;
  }): Promise<FaceMatchResult> {
    try {
      const raw = await this.post<{
        status_code: number;
        success: boolean;
        data?: { match_result?: boolean; confidence?: number };
      }>('/face-match', {
        file1: params.selfieUrl,
        file2: params.documentUrl,
      });

      const matched = raw.success && raw.data?.match_result === true;
      const confidence = raw.data?.confidence ?? (matched ? 1.0 : 0.0);

      return {
        matched: matched ?? false,
        confidence,
        provider: 'surepass',
        rawResponse: raw,
      };
    } catch (err) {
      logger.warn('Surepass matchFace failed', { error: String(err) });
      return { matched: false, confidence: null, provider: 'surepass' };
    }
  }
}
