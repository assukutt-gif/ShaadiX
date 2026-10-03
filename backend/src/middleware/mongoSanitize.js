import { ApiError } from '../utils/http.js';
function safeValue(value, depth = 0) {
  if (depth > 12) throw new ApiError(400, 'Request nesting is too deep.');
  if (Array.isArray(value)) return value.map((item) => safeValue(item, depth + 1));
  if (!value || typeof value !== 'object') return value;
  const clean = {};
  for (const [key, child] of Object.entries(value)) {
    if (key.startsWith('$') || key.includes('.') || ['__proto__', 'constructor', 'prototype'].includes(key)) throw new ApiError(400, 'Request contains an unsupported field.');
    clean[key] = safeValue(child, depth + 1);
  }
  return clean;
}
export function mongoSanitize(req, _res, next) {
  try { if (req.body && typeof req.body === 'object') req.body = safeValue(req.body); next(); } catch (error) { next(error); }
}

