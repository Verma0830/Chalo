// ============================================================
// Chalo Backend — Notification Validators (Zod Schemas)
// ============================================================

import { z } from 'zod';
import CONSTANTS from '../utils/constants';

/**
 * GET /notifications query params — validates pagination
 */
export const notificationQuerySchema = z.object({
  page: z
    .string()
    .optional()
    .transform((val) => {
      const num = Number(val);
      return Number.isFinite(num) && num >= 1 ? num : CONSTANTS.DEFAULT_PAGE;
    }),
  limit: z
    .string()
    .optional()
    .transform((val) => {
      const num = Number(val);
      return Number.isFinite(num) && num >= 1
        ? Math.min(num, CONSTANTS.MAX_LIMIT)
        : CONSTANTS.DEFAULT_LIMIT;
    }),
});

export type NotificationQueryInput = z.infer<typeof notificationQuerySchema>;
