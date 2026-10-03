import multer from 'multer';
import { env } from '../config/env.js';
const imageTypes = new Set(['image/jpeg', 'image/png', 'image/webp', 'image/avif']);
export const imageUpload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: env.UPLOAD_MAX_MB * 1024 * 1024, files: 10, fields: 30 },
  fileFilter: (_req, file, done) => done(null, imageTypes.has(file.mimetype))
});

