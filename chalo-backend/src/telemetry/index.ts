import logger from '../config/logger';
import { NodeSDK } from '@opentelemetry/sdk-node';
import { getNodeAutoInstrumentations } from '@opentelemetry/auto-instrumentations-node';
import { OTLPTraceExporter } from '@opentelemetry/exporter-trace-otlp-http';

let sdk: NodeSDK | null = null;

function telemetryEnabled(): boolean {
  return process.env.OTEL_ENABLED === 'true';
}

export async function initTelemetry(): Promise<void> {
  if (!telemetryEnabled()) {
    return;
  }

  try {
    const serviceName = process.env.OTEL_SERVICE_NAME || 'chalo-backend';
    const endpoint = process.env.OTEL_EXPORTER_OTLP_TRACES_ENDPOINT || 'http://localhost:4318/v1/traces';

    sdk = new NodeSDK({
      serviceName,
      traceExporter: new OTLPTraceExporter({ url: endpoint }),
      instrumentations: [getNodeAutoInstrumentations()],
    });

    await Promise.resolve(sdk.start());
    logger.info('OpenTelemetry initialized', { serviceName, endpoint });
  } catch (error) {
    logger.error('Failed to initialize OpenTelemetry', { error });
  }
}

export async function shutdownTelemetry(): Promise<void> {
  if (!sdk) {
    return;
  }

  try {
    await sdk.shutdown();
    logger.info('OpenTelemetry shutdown complete');
  } catch (error) {
    logger.error('OpenTelemetry shutdown failed', { error });
  } finally {
    sdk = null;
  }
}
