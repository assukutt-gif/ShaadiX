import crypto from 'node:crypto';
import { Booking, Payment } from '../models/index.js';
import { env } from '../config/env.js';
import { razorpay } from '../config/razorpay.js';
import { ApiError, asyncHandler, ok } from '../utils/http.js';
import { secureCompare } from '../utils/security.js';
import { notifyUser } from '../services/notifications.js';

export const createOrder = asyncHandler(async (req, res) => {
  const booking = await Booking.findOne({ _id: req.body.bookingId, userId: req.user._id });
  if (!booking) throw new ApiError(404, 'Booking not found.');
  if (booking.bookingStatus !== 'PENDING' || booking.paymentStatus === 'PAID') throw new ApiError(409, 'A payment order cannot be created for this booking.');
  const payment = await Payment.findOne({ bookingId: booking._id, userId: req.user._id, status: 'PENDING' }).sort({ createdAt: -1 });
  if (!payment) throw new ApiError(404, 'Payment record not found.');
  if (req.body.paymentMethod === 'PAY_LATER') {
    payment.paymentMethod = 'PAY_LATER';
    await payment.save();
    return ok(res, { paymentId: payment.id, paymentStatus: 'PENDING', order: null }, 'Pay later selected.');
  }
  if (!razorpay) throw new ApiError(503, 'Razorpay credentials are not configured.');
  if (payment.razorpayOrderId) return ok(res, { paymentId: payment.id, orderId: payment.razorpayOrderId, amount: payment.amount * 100, currency: 'INR', keyId: env.RAZORPAY_KEY_ID });
  const order = await razorpay.orders.create({
    amount: Math.round(payment.amount * 100), currency: 'INR',
    receipt: String(booking._id).slice(0, 40), notes: { bookingId: booking.id, userId: req.user.id }
  });
  payment.razorpayOrderId = order.id; payment.paymentMethod = req.body.paymentMethod;
  await payment.save();
  return ok(res, { paymentId: payment.id, orderId: order.id, amount: order.amount, currency: order.currency, keyId: env.RAZORPAY_KEY_ID }, 'Payment order created.', 201);
});

export const verifyPayment = asyncHandler(async (req, res) => {
  const { razorpay_order_id: orderId, razorpay_payment_id: paymentId, razorpay_signature: signature } = req.body;
  const payment = await Payment.findOne({ razorpayOrderId: orderId, userId: req.user._id });
  if (!payment) throw new ApiError(404, 'Payment order not found.');
  if (payment.status === 'PAID') return ok(res, payment, 'Payment was already verified.');
  if (!env.RAZORPAY_KEY_SECRET || !razorpay) throw new ApiError(503, 'Razorpay credentials are not configured.');
  const expected = crypto.createHmac('sha256', env.RAZORPAY_KEY_SECRET).update(orderId + '|' + paymentId).digest('hex');
  if (!secureCompare(expected, signature)) throw new ApiError(400, 'Payment signature is invalid.');
  const verified = await razorpay.payments.fetch(paymentId);
  if (verified.order_id !== orderId || verified.status !== 'captured' || verified.amount !== Math.round(payment.amount * 100) || verified.currency !== 'INR') {
    throw new ApiError(409, 'Razorpay has not confirmed this payment as captured.');
  }
  payment.transactionId = paymentId; payment.status = 'PAID'; await payment.save();
  await Booking.updateOne({ _id: payment.bookingId, userId: req.user._id }, { $set: { paymentStatus: 'PAID' } });
  await notifyUser(req.user._id, 'Payment received', 'Payment for booking ' + payment.bookingId + ' was received.', 'PAYMENT', { bookingId: String(payment.bookingId) });
  return ok(res, payment, 'Payment verified.');
});

export const getPayment = asyncHandler(async (req, res) => {
  const payment = await Payment.findById(req.params.id).populate('bookingId', 'eventType eventDate bookingStatus');
  if (!payment) throw new ApiError(404, 'Payment not found.');
  if (String(payment.userId) !== String(req.user._id) && req.user.role !== 'admin') throw new ApiError(403, 'You can only view your own payments.');
  return ok(res, payment);
});

