// ============================================================
// Chalo Backend — SMS Service
// Sends SMS and WhatsApp alerts via MSG91
// Used for SOS emergency alerts and transactional messages
// ============================================================

import config from '../config';
import logger from '../config/logger';
import CONSTANTS from '../utils/constants';

interface SMSRecipient {
  name: string;
  phone: string;
  channel: 'SMS' | 'WHATSAPP';
}

interface SOSAlertParams {
  customerName: string;
  driverName: string;
  vehicleNumber: string;
  lat: number;
  lng: number;
  recipients: SMSRecipient[];
}

/**
 * Send an SMS via MSG91 HTTP API
 * Docs: https://docs.msg91.com/reference/send-sms
 */
async function sendSMSViaMSG91(phone: string, message: string): Promise<void> {
  const authKey = config.msg91.authKey;
  const senderId = config.msg91.senderId;

  if (!authKey) {
    logger.warn('MSG91 auth key not configured — skipping SMS', { phone });
    return;
  }

  const controller = new AbortController();
  const timeout = setTimeout(
    () => controller.abort(),
    CONSTANTS.DEFAULT_API_TIMEOUT_MS
  );

  try {
    const response = await fetch('https://control.msg91.com/api/v5/flow/', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        authkey: authKey,
      },
      body: JSON.stringify({
        sender: senderId,
        route: '4', // Transactional route
        country: '91',
        sms: [
          {
            message,
            to: [phone.replace(/^\+91/, '')], // MSG91 expects without country code
          },
        ],
      }),
      signal: controller.signal,
    });

    if (!response.ok) {
      const body = await response.text();
      throw new Error(`MSG91 API error: ${response.status} — ${body}`);
    }

    logger.info('SMS sent via MSG91', { phone: phone.slice(-4) });
  } finally {
    clearTimeout(timeout);
  }
}

/**
 * Build the SOS alert message body
 */
function buildSOSMessage(params: Omit<SOSAlertParams, 'recipients'>): string {
  const mapsLink = `https://maps.google.com/?q=${params.lat},${params.lng}`;
  const timestamp = new Date().toLocaleString('en-IN', { timeZone: 'Asia/Kolkata' });

  return (
    `EMERGENCY: ${params.customerName} triggered an SOS during a ride. ` +
    `Driver: ${params.driverName} (${params.vehicleNumber}). ` +
    `Live location: ${mapsLink}. ` +
    `Time: ${timestamp}. ` +
    `Please contact them immediately.`
  );
}

/**
 * Send SOS alerts to all recipients via SMS
 * Fire-and-forget per recipient — individual failures are logged but don't block
 */
export async function sendSOSAlerts(params: SOSAlertParams): Promise<number> {
  const message = buildSOSMessage(params);
  let sentCount = 0;

  const results = await Promise.allSettled(
    params.recipients
      .filter((r) => r.channel === 'SMS')
      .map(async (recipient) => {
        await sendSMSViaMSG91(recipient.phone, message);
        sentCount++;
      })
  );

  const failures = results.filter((r) => r.status === 'rejected');
  if (failures.length > 0) {
    logger.error('Some SOS SMS deliveries failed', {
      total: params.recipients.length,
      failures: failures.length,
    });
  }

  return sentCount;
}

export default { sendSOSAlerts };
