import { Router } from 'express';
import rateLimit from 'express-rate-limit';
import * as auth from '../controllers/auth.js';
import { validate } from '../middleware/validate.js';
import { env } from '../config/env.js';
import { authenticate } from '../middleware/auth.js';
import { loginSchema, registerSchema, verifyOtpSchema, forgotPasswordSchema, resetPasswordSchema, refreshSchema } from '../validators/index.js';

const router = Router();
const limiter = rateLimit({ windowMs: 15 * 60 * 1000, limit: env.AUTH_RATE_LIMIT_MAX, standardHeaders: 'draft-8', legacyHeaders: false, message: { success: false, message: 'Too many authentication attempts. Please try again later.' } });
router.use(limiter);
router.post('/register', validate(registerSchema), auth.register);
router.post('/login', validate(loginSchema), auth.login);
router.post('/verify-otp', validate(verifyOtpSchema), auth.verifyOtp);
router.post('/forgot-password', validate(forgotPasswordSchema), auth.forgotPassword);
router.post('/reset-password', validate(resetPasswordSchema), auth.resetPassword);
router.post('/refresh', validate(refreshSchema), auth.refresh);
router.post('/logout', authenticate, validate(refreshSchema), auth.logout);
export default router;

