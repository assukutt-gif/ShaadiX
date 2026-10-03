import jwt from 'jsonwebtoken';
import { User } from '../models/index.js';
import { env } from '../config/env.js';
import { ApiError, asyncHandler } from '../utils/http.js';
export const authenticate = asyncHandler(async (req, _res, next) => {
  const authorization = req.get('authorization') || '';
  if (!authorization.startsWith('Bearer ')) throw new ApiError(401, 'Authentication is required.');
  let payload;
  try { payload = jwt.verify(authorization.slice(7), env.JWT_SECRET, { issuer: 'shaadix-api', audience: 'shaadix-client' }); }
  catch { throw new ApiError(401, 'Your session is invalid or expired.'); }
  if (payload.type !== 'access' || !payload.sub) throw new ApiError(401, 'Invalid access token.');
  const user = await User.findById(payload.sub).select('_id name email phone role isActive isVerified');
  if (!user || !user.isActive) throw new ApiError(401, 'This account is unavailable.');
  req.user = user; next();
});
export const authorize = (...roles) => (req, _res, next) => {
  if (!req.user || !roles.includes(req.user.role)) return next(new ApiError(403, 'You do not have permission to perform this action.'));
  next();
};
export const optionalAuth = asyncHandler(async (req, _res, next) => {
  const authorization = req.get('authorization') || '';
  if (authorization.startsWith('Bearer ')) try {
    const payload = jwt.verify(authorization.slice(7), env.JWT_SECRET, { issuer: 'shaadix-api', audience: 'shaadix-client' });
    if (payload.type === 'access') req.user = await User.findById(payload.sub).select('_id name email phone role isActive isVerified');
  } catch { /* Keep public search available for stale optional tokens. */ }
  next();
});

