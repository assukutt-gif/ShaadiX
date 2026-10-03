import { Router } from 'express';
import * as payments from '../controllers/payments.js';
import { authenticate } from '../middleware/auth.js';
import { validate } from '../middleware/validate.js';
import { idParamSchema, paymentOrderSchema, paymentVerifySchema } from '../validators/index.js';

const router = Router();
router.use(authenticate);
router.post('/create-order', validate(paymentOrderSchema), payments.createOrder);
router.post('/verify', validate(paymentVerifySchema), payments.verifyPayment);
router.get('/:id', validate(idParamSchema, 'params'), payments.getPayment);
export default router;

