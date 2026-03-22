import { context, propagation } from '@opentelemetry/api';

export type TraceCarrier = Record<string, string>;

export function captureTraceContext(): TraceCarrier {
  const carrier: TraceCarrier = {};
  propagation.inject(context.active(), carrier);
  return carrier;
}

export function extractTraceContext(carrier?: TraceCarrier) {
  if (!carrier || Object.keys(carrier).length === 0) {
    return context.active();
  }
  return propagation.extract(context.active(), carrier);
}
