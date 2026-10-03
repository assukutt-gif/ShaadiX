import { Router } from 'express';
import * as bookings from '../controllers/bookings.js';
import { authenticate, authorize } from '../middleware/auth.js';
import { validate } from '../middleware/validate.js';
import { bookingActionSchema, bookingSchema, idParamSchema } from '../validators/index.js';

const router = Router();
router.use(authenticate);
router.post('/', authorize('customer'), validate(bookingSchema), bookings.createBooking);
router.get('/', authorize('customer', 'admin'), bookings.listBookings);
router.get('/:id', validate(idParamSchema, 'params'), bookings.getBooking);
router.put('/:id/cancel', authorize('customer'), validate(idParamSchema, 'params'), validate(bookingActionSchema), bookings.cancelBooking);
router.put('/:id/confirm', authorize('provider', 'admin'), validate(idParamSchema, 'params'), bookings.confirmBooking);
router.put('/:id/reject', authorize('provider', 'admin'), validate(idParamSchema, 'params'), validate(bookingActionSchema), bookings.rejectBooking);
router.put('/:id/complete', authorize('provider', 'admin'), validate(idParamSchema, 'params'), bookings.completeBooking);
export default router;

