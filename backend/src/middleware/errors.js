import mongoose from 'mongoose';
import { env } from '../config/env.js';
import { ApiError } from '../utils/http.js';
export const notFound = (_req, _res, next) => next(new ApiError(404, 'The requested resource was not found.'));
export function errorHandler(error, _req, res, _next) {
  if (res.headersSent) return;
  let status = error.statusCode || 500; let message = error.message || 'Something went wrong.'; let details = error.details;
  if (error instanceof mongoose.Error.ValidationError) { status = 422; message = 'Please check the submitted information.'; details = Object.values(error.errors).map((item) => ({ field: item.path, message: item.message })); }
  else if (error instanceof mongoose.Error.CastError) { status = 400; message = 'The supplied identifier or value is invalid.'; }
  else if (error?.code === 11000) { status = 409; message = 'A record with this information already exists.'; }
  else if (error?.name === 'MulterError') { status = 400; message = error.code === 'LIMIT_FILE_SIZE' ? 'The uploaded file exceeds the size limit.' : 'The uploaded file could not be accepted.'; }
  else if (status >= 500) { message = 'An unexpected server error occurred.'; console.error(error); }
  const body = { success: false, message }; if (details && env.NODE_ENV !== 'production') body.details = details;
  return res.status(status).json(body);
}

