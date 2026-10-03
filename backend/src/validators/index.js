import { z } from 'zod';

const id = z.string().regex(/^[a-f0-9]{24}$/i, 'Must be a valid ID.');
const phone = z.string().regex(/^([+])?[1-9][0-9]{6,14}$/, 'Enter a valid international phone number.');
const password = z.string().min(10).max(128).regex(/[a-z]/).regex(/[A-Z]/).regex(/[0-9]/);
const name = z.string().trim().min(2).max(100);
const time = z.string().regex(/^([01][0-9]|2[0-3]):[0-5][0-9]$/);
const date = z.string().regex(/^[0-9]{4}-[0-9]{2}-[0-9]{2}$/);
const safeText = (max = 200) => z.string().trim().max(max);

export const registerSchema = z.object({
  name, email: z.string().trim().email().max(254), phone, password,
  role: z.enum(['customer', 'provider']).default('customer')
}).strict();
export const loginSchema = z.object({ email: z.string().trim().email().max(254), password: z.string().min(1).max(128) }).strict();
export const verifyOtpSchema = z.object({ email: z.string().trim().email(), code: z.string().regex(/^[0-9]{6}$/), purpose: z.enum(['verify', 'reset']).default('verify') }).strict();
export const forgotPasswordSchema = z.object({ email: z.string().trim().email() }).strict();
export const resetPasswordSchema = z.object({ resetToken: z.string().min(20).max(2000), password }).strict();
export const refreshSchema = z.object({ refreshToken: z.string().min(20).max(2000) }).strict();
export const profileSchema = z.object({ name: name.optional(), phone: phone.optional(), location: safeText(160).optional() }).strict().refine((value) => Object.keys(value).length > 0);
export const providerSchema = z.object({
  businessName: name, category: z.string().trim().min(2).max(80), description: z.string().trim().min(10).max(3000),
  phone, email: z.string().trim().email(), address: z.string().trim().min(4).max(250), city: z.string().trim().min(2).max(100),
  latitude: z.coerce.number().min(-90).max(90).optional(), longitude: z.coerce.number().min(-180).max(180).optional(),
  priceRange: z.object({ min: z.coerce.number().min(0), max: z.coerce.number().min(0) }).strict().optional(),
  availability: z.array(z.object({ dayOfWeek: z.coerce.number().int().min(0).max(6), startTime: time, endTime: time, isAvailable: z.boolean().default(true) }).strict()).max(28).optional()
}).strict();
export const serviceSchema = z.object({
  providerId: id.optional(), category: z.string().trim().min(2).max(80), title: z.string().trim().min(3).max(140),
  description: z.string().trim().min(10).max(4000), price: z.coerce.number().min(0).max(100000000),
  pricingUnit: z.enum(['flat', 'perGuest']).default('flat'), location: z.string().trim().min(2).max(180),
  availability: z.array(date).max(366).optional(), features: z.array(z.string().trim().min(1).max(100)).max(30).optional()
}).strict();
export const bookingSchema = z.object({
  providerId: id, serviceId: id, eventType: z.string().trim().min(2).max(80), eventDate: date,
  startTime: time, endTime: time, guestCount: z.coerce.number().int().min(1).max(100000),
  eventLocation: z.string().trim().min(4).max(300), specialRequirements: safeText(2000).optional().default('')
}).strict();
export const bookingActionSchema = z.object({ reason: safeText(500).optional() }).strict();
export const paymentOrderSchema = z.object({ bookingId: id, paymentMethod: z.enum(['UPI', 'CARD', 'NET_BANKING', 'WALLET', 'PAY_LATER', 'RAZORPAY']).default('RAZORPAY') }).strict();
export const paymentVerifySchema = z.object({ razorpay_order_id: z.string().min(5).max(100), razorpay_payment_id: z.string().min(5).max(100), razorpay_signature: z.string().min(20).max(200) }).strict();
export const reviewSchema = z.object({ bookingId: id, rating: z.coerce.number().int().min(1).max(5), comment: z.string().trim().min(5).max(2000) }).strict();
export const reviewReportSchema = z.object({ reason: z.string().trim().min(5).max(500) }).strict();
export const reviewModerationSchema = z.object({ isVisible: z.boolean() }).strict();
export const categorySchema = z.object({ name: z.string().trim().min(2).max(100), description: safeText(500).optional(), image: z.string().url().max(500).optional(), isActive: z.boolean().optional() }).strict();
export const availabilitySchema = z.object({
  availability: z.array(z.object({ dayOfWeek: z.coerce.number().int().min(0).max(6), startTime: time, endTime: time, isAvailable: z.boolean().default(true) }).strict()).max(28),
  blockedDates: z.array(date).max(366).optional()
}).strict();
export const idParamSchema = z.object({ id });
export const providerParamSchema = z.object({ id });
export const searchSchema = z.object({
  keyword: safeText(100).optional(), category: safeText(80).optional(), city: safeText(100).optional(),
  minimumPrice: z.coerce.number().min(0).optional(), maximumPrice: z.coerce.number().min(0).optional(),
  minPrice: z.coerce.number().min(0).optional(), maxPrice: z.coerce.number().min(0).optional(),
  rating: z.coerce.number().min(0).max(5).optional(), eventDate: date.optional(),
  page: z.coerce.number().int().min(1).optional(), limit: z.coerce.number().int().min(1).max(100).optional(),
  sortBy: z.enum(['price', 'rating', 'createdAt', 'title']).optional(), order: z.enum(['asc', 'desc']).optional()
}).passthrough();

