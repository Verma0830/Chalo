// ============================================================
// Chalo Backend — Admin Validators (Zod Schemas)
// ============================================================

import { z } from 'zod';
import CONSTANTS from '../utils/constants';

export const adminPaginationSchema = z.object({
  page:  z.coerce.number().int().min(1).default(CONSTANTS.DEFAULT_PAGE),
  limit: z.coerce.number().int().min(1).max(CONSTANTS.MAX_LIMIT).default(CONSTANTS.DEFAULT_LIMIT),
});
export type AdminPaginationQuery = z.infer<typeof adminPaginationSchema>;

export const adminDriverParamSchema = z.object({
  driverId: z.string().cuid('Invalid driver ID'),
});
export type AdminDriverParam = z.infer<typeof adminDriverParamSchema>;

export const approveDriverSchema = z.object({
  note: z.string().trim().max(500).optional(),
});
export type ApproveDriverInput = z.infer<typeof approveDriverSchema>;

export const rejectDriverSchema = z.object({
  reason: z.string().trim().min(5, 'Reason must be at least 5 characters').max(500),
});
export type RejectDriverInput = z.infer<typeof rejectDriverSchema>;

export const configKeyParamSchema = z.object({
  key: z.string().min(1),
});
export type ConfigKeyParam = z.infer<typeof configKeyParamSchema>;

export const updateConfigSchema = z.object({
  value: z.string().min(1),
});
export type UpdateConfigInput = z.infer<typeof updateConfigSchema>;

export const promoteAdminSchema = z.object({
  phone: z
    .string()
    .trim()
    .regex(/^\+91[6-9]\d{9}$/, 'Invalid Indian mobile number. Format: +91XXXXXXXXXX'),
});
export type PromoteAdminInput = z.infer<typeof promoteAdminSchema>;
