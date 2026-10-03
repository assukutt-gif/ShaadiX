import { Router } from 'express';
import * as resources from '../controllers/resources.js';
import { authenticate, authorize, optionalAuth } from '../middleware/auth.js';
import { imageUpload } from '../middleware/upload.js';
import { validate } from '../middleware/validate.js';
import { idParamSchema, searchSchema, serviceSchema } from '../validators/index.js';

const router = Router();
router.get('/search', validate(searchSchema, 'query'), optionalAuth, resources.listServices);
router.get('/', validate(searchSchema, 'query'), optionalAuth, resources.listServices);
router.get('/:id', validate(idParamSchema, 'params'), optionalAuth, resources.getService);
router.post('/', authenticate, authorize('provider', 'admin'), imageUpload.array('images', 10), validate(serviceSchema), resources.createService);
router.put('/:id', authenticate, authorize('provider', 'admin'), validate(idParamSchema, 'params'), imageUpload.array('images', 10), validate(serviceSchema.partial()), resources.updateService);
router.delete('/:id', authenticate, authorize('provider', 'admin'), validate(idParamSchema, 'params'), resources.deleteService);
export default router;

