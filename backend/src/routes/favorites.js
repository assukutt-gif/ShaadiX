import { Router } from 'express';
import * as resources from '../controllers/resources.js';
import { authenticate, authorize } from '../middleware/auth.js';
import { validate } from '../middleware/validate.js';
import { z } from 'zod';

const router = Router();
const providerParam = z.object({ providerId: z.string().regex(/^[a-f0-9]{24}$/i) }).strict();
router.use(authenticate, authorize('customer'));
router.get('/', resources.listFavorites);
router.post('/:providerId', validate(providerParam, 'params'), resources.addFavorite);
router.delete('/:providerId', validate(providerParam, 'params'), resources.removeFavorite);
export default router;

