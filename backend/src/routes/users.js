import { Router } from 'express';
import * as resources from '../controllers/resources.js';
import { authenticate } from '../middleware/auth.js';
import { imageUpload } from '../middleware/upload.js';
import { validate } from '../middleware/validate.js';
import { profileSchema } from '../validators/index.js';

const router = Router();
router.use(authenticate);
router.get('/profile', resources.getProfile);
router.put('/profile', validate(profileSchema), resources.updateProfile);
router.post('/profile-image', imageUpload.single('image'), resources.profileImage);
export default router;

