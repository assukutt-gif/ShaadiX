import { Booking, BookingSlot, Payment, Provider } from '../models/index.js';
import { cancelBooking as cancelBookingRecord, createBooking as createBookingRecord } from '../services/booking.js';
import { notifyUser } from '../services/notifications.js';
import { ApiError, asyncHandler, ok, pageOptions, pagination } from '../utils/http.js';

export const createBooking = asyncHandler(async (req, res) => {
  const booking = await createBookingRecord(req.user, req.body);
  return ok(res, booking, 'Booking request created. Payment and provider confirmation are pending.', 201);
});

export const listBookings = asyncHandler(async (req, res) => {
  const { page, limit, skip } = pageOptions(req.query);
  const filter = { userId: req.user._id };
  if (['PENDING', 'CONFIRMED', 'REJECTED', 'CANCELLED', 'COMPLETED'].includes(req.query.status)) filter.bookingStatus = req.query.status;
  const [rows, total] = await Promise.all([
    Booking.find(filter).populate('providerId', 'businessName city phone').populate('serviceId', 'title category images').sort({ eventDate: -1, createdAt: -1 }).skip(skip).limit(limit),
    Booking.countDocuments(filter)
  ]);
  return ok(res, rows, 'Bookings retrieved.', 200, pagination(page, limit, total));
});

export const getBooking = asyncHandler(async (req, res) => {
  const booking = await Booking.findById(req.params.id).populate('providerId', 'businessName city phone email').populate('serviceId', 'title category images price').populate('userId', 'name email phone');
  if (!booking) throw new ApiError(404, 'Booking not found.');
  const isOwner = String(booking.userId._id) === String(req.user._id);
  const provider = await Provider.findById(booking.providerId._id).select('userId');
  const isProvider = String(provider?.userId) === String(req.user._id);
  if (!isOwner && !isProvider && req.user.role !== 'admin') throw new ApiError(403, 'You can only view your own bookings.');
  return ok(res, booking);
});

export const cancelBooking = asyncHandler(async (req, res) => {
  const booking = await Booking.findOne({ _id: req.params.id, userId: req.user._id });
  if (!booking) throw new ApiError(404, 'Booking not found.');
  if (!['PENDING', 'CONFIRMED'].includes(booking.bookingStatus)) throw new ApiError(409, 'This booking can no longer be cancelled.');
  const paidPayment = await Payment.findOne({ bookingId: booking._id, status: 'PAID' });
  if (paidPayment) throw new ApiError(409, 'This booking has a captured payment. Contact support to request a refund before cancelling.');
  const updated = await cancelBookingRecord(booking, req.body.reason || '');
  await Payment.updateMany({ bookingId: booking._id, status: 'PENDING' }, { $set: { status: 'FAILED' } });
  const provider = await Provider.findById(booking.providerId).select('userId');
  await notifyUser(provider.userId, 'Booking cancelled', 'A customer cancelled booking ' + booking.id + '.', 'BOOKING', { bookingId: booking.id });
  return ok(res, updated, 'Booking cancelled.');
});

async function providerOwns(user, booking) {
  if (user.role === 'admin') return true;
  if (user.role !== 'provider') return false;
  const provider = await Provider.findOne({ userId: user._id, isActive: true });
  return Boolean(provider && String(provider._id) === String(booking.providerId));
}
export const confirmBooking = asyncHandler(async (req, res) => {
  const booking = await Booking.findById(req.params.id);
  if (!booking) throw new ApiError(404, 'Booking not found.');
  if (!(await providerOwns(req.user, booking))) throw new ApiError(403, 'Only the assigned provider can confirm this booking.');
  if (booking.bookingStatus !== 'PENDING') throw new ApiError(409, 'Only pending bookings can be confirmed.');
  booking.bookingStatus = 'CONFIRMED'; await booking.save();
  await notifyUser(booking.userId, 'Booking confirmed', 'Your provider confirmed booking ' + booking.id + '.', 'BOOKING', { bookingId: booking.id });
  return ok(res, booking, 'Booking confirmed.');
});

export const rejectBooking = asyncHandler(async (req, res) => {
  const booking = await Booking.findById(req.params.id);
  if (!booking) throw new ApiError(404, 'Booking not found.');
  if (!(await providerOwns(req.user, booking))) throw new ApiError(403, 'Only the assigned provider can reject this booking.');
  if (booking.bookingStatus !== 'PENDING') throw new ApiError(409, 'Only pending bookings can be rejected.');
  booking.bookingStatus = 'REJECTED';
  booking.cancellationReason = req.body.reason || 'Provider declined booking request.';
  await booking.save();
  await BookingSlot.deleteMany({ bookingId: booking._id });
  await Payment.updateMany({ bookingId: booking._id, status: 'PENDING' }, { $set: { status: 'FAILED' } });
  await notifyUser(booking.userId, 'Booking request declined', 'The provider declined booking ' + booking.id + '. You can choose another provider.', 'BOOKING', { bookingId: booking.id });
  return ok(res, booking, 'Booking rejected.');
});

export const completeBooking = asyncHandler(async (req, res) => {
  const booking = await Booking.findById(req.params.id);
  if (!booking) throw new ApiError(404, 'Booking not found.');
  if (!(await providerOwns(req.user, booking))) throw new ApiError(403, 'Only the assigned provider can complete this booking.');
  if (booking.bookingStatus !== 'CONFIRMED') throw new ApiError(409, 'Only confirmed bookings can be completed.');
  booking.bookingStatus = 'COMPLETED'; await booking.save();
  await notifyUser(booking.userId, 'Event completed', 'Your booking ' + booking.id + ' is marked complete. You can now leave a review.', 'BOOKING', { bookingId: booking.id });
  return ok(res, booking, 'Booking marked completed.');
});

