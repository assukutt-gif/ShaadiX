import jwt from 'jsonwebtoken';
import bcrypt from 'bcryptjs';
import { env } from '../config/env.js';
import { OtpChallenge, RefreshSession, User } from '../models/index.js';
import { sendOtpEmail } from '../services/mailer.js';
import { issueTokens, publicUser } from '../services/tokens.js';
import { ApiError, asyncHandler, ok } from '../utils/http.js';
import { randomNumericCode, secureCompare, sha256 } from '../utils/security.js';

async function createOtp(user, purpose) {
  const code = randomNumericCode();
  const minutes = purpose === 'reset' ? env.PASSWORD_RESET_TTL_MINUTES : env.OTP_TTL_MINUTES;
  const codeHash = sha256(user.email + ':' + code + ':' + env.JWT_REFRESH_SECRET);
  await OtpChallenge.deleteMany({ email: user.email, purpose });
  const challenge = await OtpChallenge.create({ userId: user._id, email: user.email, purpose, codeHash, expiresAt: new Date(Date.now() + minutes * 60000) });
  try { await sendOtpEmail(user.email, code, purpose); }
  catch (error) { await OtpChallenge.deleteOne({ _id: challenge._id }); throw error; }
}

export const register = asyncHandler(async (req, res) => {
  const user = await User.create({
    name: req.body.name, email: req.body.email.toLowerCase(), phone: req.body.phone,
    password: req.body.password, role: req.body.role
  });
  try { await createOtp(user, 'verify'); }
  catch (error) { await User.deleteOne({ _id: user._id }); throw error; }
  return ok(res, { user: publicUser(user), requiresVerification: true }, 'Account created. Check your email for a verification code.', 201);
});

export const login = asyncHandler(async (req, res) => {
  const identifier = req.body.identifier.trim();
  const user = await User.findOne(identifier.includes('@')
    ? { email: identifier.toLowerCase() }
    : { phone: identifier }).select('+password');
  if (!user || !user.isActive || !(await user.comparePassword(req.body.password))) throw new ApiError(401, 'Email/phone or password is incorrect.');
  if (!user.isVerified) throw new ApiError(403, 'Verify your email before signing in.');
  return ok(res, await issueTokens(user), 'Signed in successfully.');
});

export const verifyOtp = asyncHandler(async (req, res) => {
  const email = req.body.email.toLowerCase();
  const challenge = await OtpChallenge.findOne({ email, purpose: req.body.purpose, expiresAt: { $gt: new Date() } }).sort({ createdAt: -1 });
  if (!challenge || challenge.attempts >= 5) throw new ApiError(400, 'This verification code is invalid or expired.');
  const supplied = sha256(email + ':' + req.body.code + ':' + env.JWT_REFRESH_SECRET);
  if (!secureCompare(supplied, challenge.codeHash)) {
    challenge.attempts += 1; await challenge.save();
    throw new ApiError(400, 'This verification code is invalid or expired.');
  }
  await OtpChallenge.deleteMany({ email, purpose: req.body.purpose });
  if (req.body.purpose === 'reset') {
    const resetToken = jwt.sign({ type: 'password-reset', email }, env.JWT_REFRESH_SECRET, {
      issuer: 'shaadix-api', audience: 'shaadix-client', expiresIn: '10m'
    });
    return ok(res, { resetToken }, 'Code verified. You can now choose a new password.');
  }
  const user = await User.findOne({ email });
  if (!user || !user.isActive) throw new ApiError(400, 'This verification code is invalid or expired.');
  user.isVerified = true; await user.save();
  return ok(res, await issueTokens(user), 'Email verified successfully.');
});

export const forgotPassword = asyncHandler(async (req, res) => {
  const user = await User.findOne({ email: req.body.email.toLowerCase(), isActive: true });
  if (user) await createOtp(user, 'reset');
  return ok(res, {}, 'If an account matches that email, a reset code will be sent.');
});

export const resetPassword = asyncHandler(async (req, res) => {
  let payload;
  try { payload = jwt.verify(req.body.resetToken, env.JWT_REFRESH_SECRET, { issuer: 'shaadix-api', audience: 'shaadix-client' }); }
  catch { throw new ApiError(400, 'The password reset session is invalid or expired.'); }
  if (payload.type !== 'password-reset' || !payload.email) throw new ApiError(400, 'The password reset session is invalid or expired.');
  const user = await User.findOne({ email: payload.email, isActive: true });
  if (!user) throw new ApiError(400, 'The password reset session is invalid or expired.');
  user.password = req.body.password; user.tokenVersion += 1; await user.save();
  await RefreshSession.updateMany({ userId: user._id, revokedAt: null }, { $set: { revokedAt: new Date() } });
  return ok(res, {}, 'Password updated. Sign in with your new password.');
});

export const refresh = asyncHandler(async (req, res) => {
  let payload;
  try { payload = jwt.verify(req.body.refreshToken, env.JWT_REFRESH_SECRET, { issuer: 'shaadix-api', audience: 'shaadix-client' }); }
  catch { throw new ApiError(401, 'Refresh session is invalid or expired.'); }
  if (payload.type !== 'refresh') throw new ApiError(401, 'Refresh session is invalid or expired.');
  const hash = sha256(req.body.refreshToken);
  const session = await RefreshSession.findOneAndUpdate({ tokenHash: hash, userId: payload.sub, revokedAt: null, expiresAt: { $gt: new Date() } }, { $set: { revokedAt: new Date() } });
  if (!session) throw new ApiError(401, 'Refresh session has already been used or expired.');
  const user = await User.findById(payload.sub);
  if (!user?.isActive || user.tokenVersion !== payload.version) throw new ApiError(401, 'Refresh session is invalid.');
  return ok(res, await issueTokens(user), 'Session refreshed.');
});

export const logout = asyncHandler(async (req, res) => {
  const token = req.body.refreshToken;
  if (token) await RefreshSession.updateOne({ userId: req.user._id, tokenHash: sha256(token), revokedAt: null }, { $set: { revokedAt: new Date() } });
  return ok(res, {}, 'Signed out successfully.');
});

