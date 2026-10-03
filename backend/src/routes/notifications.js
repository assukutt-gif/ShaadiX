import { Router } from 'express';
import * as notifications from '../controllers/notifications.js';
import { authenticate } from '../middleware/auth.js';
import { validate } from '../middleware/validate.js';
import { idParamSchema } from '../validators/index.js';
const router = Router();
router.use(authenticate);
router.get('/', notifications.listNotifications);
router.put('/:id/read', validate(idParamSchema, 'params'), notifications.markNotificationRead);
export default router;

