import 'dotenv/config';
import { z } from 'zod';
const schema = z.object({
  NODE_ENV: z.enum(['development','test','production']).default('development'),
  PORT: z.coerce.number().int().min(1).max(65535).default(5000),
  MONGODB_URI: z.string().min(1), JWT_SECRET: z.string().min(32),
  JWT_EXPIRES_IN: z.string().default('1h'), JWT_REFRESH_SECRET: z.string().min(32),
  JWT_REFRESH_EXPIRES_IN: z.string().default('30d'), CLIENT_URL: z.string().default(''),
  CLOUDINARY_CLOUD_NAME: z.string().default(''), CLOUDINARY_API_KEY: z.string().default(''), CLOUDINARY_API_SECRET: z.string().default(''),
  RAZORPAY_KEY_ID: z.string().default(''), RAZORPAY_KEY_SECRET: z.string().default(''), RAZORPAY_WEBHOOK_SECRET: z.string().default(''),
  SMTP_HOST: z.string().default(''), SMTP_PORT: z.coerce.number().int().default(587), SMTP_SECURE: z.string().default('false'),
  SMTP_USER: z.string().default(''), SMTP_PASSWORD: z.string().default(''), SMTP_FROM: z.string().default('ShaadiX <no-reply@example.com>'),
  OTP_TTL_MINUTES: z.coerce.number().int().min(1).max(30).default(10),
  PASSWORD_RESET_TTL_MINUTES: z.coerce.number().int().min(1).max(60).default(15),
  RATE_LIMIT_WINDOW_MS: z.coerce.number().int().positive().default(900000),
  RATE_LIMIT_MAX: z.coerce.number().int().positive().default(300), AUTH_RATE_LIMIT_MAX: z.coerce.number().int().positive().default(20),
  UPLOAD_MAX_MB: z.coerce.number().int().min(1).max(20).default(8),
  SEED_ADMIN_EMAIL: z.string().default(''), SEED_ADMIN_PASSWORD: z.string().default(''), SEED_ADMIN_PHONE: z.string().default('')
}).superRefine((v,ctx)=>{
  if(v.NODE_ENV==='production'&&!v.CLIENT_URL.trim())ctx.addIssue({code:'custom',path:['CLIENT_URL'],message:'CLIENT_URL is required in production.'});
  if(v.JWT_SECRET===v.JWT_REFRESH_SECRET)ctx.addIssue({code:'custom',path:['JWT_REFRESH_SECRET'],message:'Access and refresh secrets must be different.'});
});
const parsed=schema.safeParse(process.env);
if(!parsed.success) throw new Error('Invalid environment configuration. Check required variables.');
export const env=Object.freeze({
  ...parsed.data,
  allowedOrigins:parsed.data.CLIENT_URL.split(',').map((v)=>v.trim()).filter(Boolean),
  hasCloudinary:Boolean(parsed.data.CLOUDINARY_CLOUD_NAME&&parsed.data.CLOUDINARY_API_KEY&&parsed.data.CLOUDINARY_API_SECRET),
  hasRazorpay:Boolean(parsed.data.RAZORPAY_KEY_ID&&parsed.data.RAZORPAY_KEY_SECRET),
  hasSmtp:Boolean(parsed.data.SMTP_HOST&&parsed.data.SMTP_USER&&parsed.data.SMTP_PASSWORD)
});

