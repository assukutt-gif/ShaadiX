import nodemailer from 'nodemailer';
import { env } from '../config/env.js';
import { ApiError } from '../utils/http.js';

let transport;
function getTransport() {
  if (!env.hasSmtp) return null;
  if (!transport) transport = nodemailer.createTransport({
    host: env.SMTP_HOST, port: env.SMTP_PORT, secure: env.SMTP_SECURE === 'true',
    auth: { user: env.SMTP_USER, pass: env.SMTP_PASSWORD }
  });
  return transport;
}

export async function sendOtpEmail(email, code, purpose) {
  const mailer = getTransport();
  if (!mailer) {
    if (env.NODE_ENV === 'production') throw new ApiError(503, 'Email delivery is not configured.');
    console.info('Development OTP for', email, 'purpose:', purpose, 'code:', code);
    return;
  }
  const text = purpose === 'verify'
    ? 'Your ShaadiX verification code is ' + code + '. It expires in ' + env.OTP_TTL_MINUTES + ' minutes.'
    : 'Your ShaadiX password reset code is ' + code + '. It expires in ' + env.PASSWORD_RESET_TTL_MINUTES + ' minutes.';
  await mailer.sendMail({ from: env.SMTP_FROM, to: email, subject: 'Your ShaadiX verification code', text });
}

