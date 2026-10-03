import { Router } from 'express';
import auth from './auth.js';
import users from './users.js';
import providers, { providerWorkspace } from './providers.js';
import services from './services.js';
import bookings from './bookings.js';
import payments from './payments.js';
import reviews from './reviews.js';
import favorites from './favorites.js';
import notifications from './notifications.js';
import admin from './admin.js';
import { listCategories } from '../controllers/admin.js';
import { asyncHandler, ok } from '../utils/http.js';
import mongoose from 'mongoose';

const router = Router();
router.get('/health', asyncHandler(async (_req, res) => {
  const connected = mongoose.connection.readyState === 1;
  return ok(res, { database: connected ? 'connected' : 'unavailable' }, 'ShaadiX backend is running', connected ? 200 : 503);
}));
router.get('/categories', listCategories);
router.use('/auth', auth);
router.use('/users', users);
router.use('/providers', providers);
router.use('/provider', providerWorkspace);
router.use('/services', services);
router.use('/bookings', bookings);
router.use('/payments', payments);
router.use('/reviews', reviews);
router.use('/favorites', favorites);
router.use('/notifications', notifications);
router.use('/admin', admin);
export default router;

