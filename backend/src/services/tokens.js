import jwt from 'jsonwebtoken';
import { env } from '../config/env.js';
import { RefreshSession } from '../models/index.js';
import { sha256 } from '../utils/security.js';

export function publicUser(user) {
  return { id: user.id, name: user.name, email: user.email, phone: user.phone, role: user.role, profileImage: user.profileImage, location: user.location, isVerified: user.isVerified };
}
export async function issueTokens(user) {
  const accessToken = jwt.sign({ type: 'access' }, env.JWT_SECRET, {
    subject: user.id, issuer: 'shaadix-api', audience: 'shaadix-client', expiresIn: env.JWT_EXPIRES_IN
  });
  const refreshToken = jwt.sign({ type: 'refresh', version: user.tokenVersion }, env.JWT_REFRESH_SECRET, {
    subject: user.id, issuer: 'shaadix-api', audience: 'shaadix-client', expiresIn: env.JWT_REFRESH_EXPIRES_IN
  });
  const claims = jwt.decode(refreshToken);
  await RefreshSession.create({ userId: user._id, tokenHash: sha256(refreshToken), expiresAt: new Date(claims.exp * 1000) });
  return { accessToken, refreshToken, user: publicUser(user) };
}

