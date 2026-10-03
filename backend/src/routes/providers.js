import { Router } from 'express';
import * as resources from '../controllers/resources.js';
import { authenticate, authorize } from '../middleware/auth.js';
import { validate } from '../middleware/validate.js';
import { availabilitySchema, idParamSchema, providerSchema } from '../validators/index.js';
import { listProviderReviews } from '../controllers/reviews.js';

const router = Router();
router.get('/', resources.listProviders);
router.get('/:id/reviews', validate(idParamSchema, 'params'), listProviderReviews);
router.get('/:id', validate(idParamSchema, 'params'), resources.getProvider);
router.post('/', authenticate, authorize('provider', 'admin'), validate(providerSchema), resources.createProvider);
router.put('/:id', authenticate, authorize('provider', 'admin'), validate(idParamSchema, 'params'), validate(providerSchema.partial()), resources.updateProvider);
router.delete('/:id', authenticate, authorize('provider', 'admin'), validate(idParamSchema, 'params'), resources.deleteProvider);
export default router;

export const providerWorkspace = Router();
providerWorkspace.use(authenticate, authorize('provider'));
providerWorkspace.get('/dashboard', resources.providerDashboard);
providerWorkspace.get('/bookings', resources.providerBookings);
providerWorkspace.get('/earnings', resources.providerEarnings);
providerWorkspace.put('/availability', validate(availabilitySchema), resources.updateAvailability);

