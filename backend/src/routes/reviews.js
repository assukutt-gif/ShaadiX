import { Router } from 'express';
import * as reviews from '../controllers/reviews.js';
import { authenticate, authorize } from '../middleware/auth.js';
import { imageUpload } from '../middleware/upload.js';
import { validate } from '../middleware/validate.js';
import { idParamSchema, reviewSchema } from '../validators/index.js';
import { z } from 'zod';

const updateSchema = reviewSchema.omit({ bookingId: true }).partial().strict().refine((value) => Object.keys(value).length > 0);
const router = Router();
router.post('/', authenticate, authorize('customer'), imageUpload.array('images', 8), validate(reviewSchema), reviews.createReview);
router.put('/:id', authenticate, authorize('customer'), validate(idParamSchema, 'params'), imageUpload.array('images', 8), validate(updateSchema), reviews.updateReview);
router.delete('/:id', authenticate, authorize('customer'), validate(idParamSchema, 'params'), reviews.deleteReview);
export default router;

