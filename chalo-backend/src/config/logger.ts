// ============================================================
// Chalo Backend — Winston Logger
// Structured logging with file rotation and console output
// ============================================================

import winston from 'winston';
import path from 'path';
import config from './index';

const logDir = path.resolve(__dirname, '../../logs');

// Custom format: timestamp + level + message + metadata
const logFormat = winston.format.combine(
  winston.format.timestamp({ format: 'YYYY-MM-DD HH:mm:ss.SSS' }),
  winston.format.errors({ stack: true }),
  winston.format.printf(({ timestamp, level, message, stack, ...meta }) => {
    const metaStr = Object.keys(meta).length ? ` ${JSON.stringify(meta)}` : '';
    const stackStr = stack ? `\n${stack}` : '';
    return `[${timestamp}] ${level.toUpperCase()}: ${message}${metaStr}${stackStr}`;
  })
);

// JSON format for production (structured logs for log aggregators)
const jsonFormat = winston.format.combine(
  winston.format.timestamp(),
  winston.format.errors({ stack: true }),
  winston.format.json()
);

const transports: winston.transport[] = [
  // Console — always active
  new winston.transports.Console({
    format: config.isProd ? jsonFormat : winston.format.combine(
      winston.format.colorize(),
      logFormat
    ),
  }),
];

// File transports — production only (or if log dir exists)
if (config.isProd || config.env !== 'test') {
  transports.push(
    // All logs
    new winston.transports.File({
      filename: path.join(logDir, 'combined.log'),
      maxsize: 10 * 1024 * 1024, // 10MB
      maxFiles: 5,
      format: jsonFormat,
    }),

    // Error logs only
    new winston.transports.File({
      filename: path.join(logDir, 'error.log'),
      level: 'error',
      maxsize: 10 * 1024 * 1024,
      maxFiles: 10,
      format: jsonFormat,
    })
  );
}

export const logger = winston.createLogger({
  level: config.logLevel,
  defaultMeta: { service: 'chalo-backend' },
  transports,
  // Don't exit on uncaught exceptions — let the process handler deal with it
  exitOnError: false,
});

// Stream for Morgan HTTP request logging
export const morganStream = {
  write: (message: string) => {
    logger.http(message.trim());
  },
};

export default logger;
