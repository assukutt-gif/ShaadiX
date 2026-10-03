import crypto from 'node:crypto';
export const sha256 = (value) => crypto.createHash('sha256').update(String(value)).digest('hex');
export const randomNumericCode = (digits = 6) => Array.from({ length: digits }, () => crypto.randomInt(0, 10)).join('');
export const secureCompare = (left, right) => {
  const a = Buffer.from(String(left)); const b = Buffer.from(String(right));
  return a.length === b.length && crypto.timingSafeEqual(a, b);
};
export const makeSlotKey = (providerId, date, slot) => String(providerId) + ':' + date + ':' + slot;

