import { Readable } from 'node:stream';
import cloudinary from '../config/cloudinary.js';
import { env } from '../config/env.js';
import { ApiError } from '../utils/http.js';

export async function uploadImages(files = [], folder) {
  if (!files.length) return [];
  if (!env.hasCloudinary) throw new ApiError(503, 'Image storage is not configured.');
  return Promise.all(files.map((file) => new Promise((resolve, reject) => {
    const stream = cloudinary.uploader.upload_stream({
      folder, resource_type: 'image', allowed_formats: ['jpg', 'jpeg', 'png', 'webp', 'avif'],
      transformation: [{ width: 2400, height: 2400, crop: 'limit' }]
    }, (error, result) => error ? reject(error) : resolve(result.secure_url));
    Readable.from(file.buffer).pipe(stream);
  })));
}

