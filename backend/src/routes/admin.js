import { Router } from 'express';
import * as admin from '../controllers/admin.js';
import { authenticate, authorize } from '../middleware/auth.js';
import { validate } from '../middleware/validate.js';
import { categorySchema, idParamSchema } from '../validators/index.js';

const router = Router();
router.use(authenticate, authorize('admin'));
router.get('/dashboard', admin.dashboard);
router.get('/users', admin.listUsers);
router.get('/providers', admin.listProviders);
router.get('/bookings', admin.listBookings);
router.put('/providers/:id/verify', validate(idParamSchema, 'params'), admin.verifyProvider);
router.delete('/users/:id', validate(idParamSchema, 'params'), admin.deleteUser);
router.delete('/providers/:id', validate(idParamSchema, 'params'), admin.deleteProvider);
router.post('/categories', validate(categorySchema), admin.createCategory);
router.put('/categories/:id', validate(idParamSchema, 'params'), validate(categorySchema.partial()), admin.updateCategory);
router.delete('/categories/:id', validate(idParamSchema, 'params'), admin.deleteCategory);
export default router;

