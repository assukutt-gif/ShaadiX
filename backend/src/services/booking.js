import mongoose from 'mongoose';
import { Booking, BookingSlot, Payment, Provider, Service } from '../models/index.js';
import { ApiError } from '../utils/http.js';
import { makeSlotKey } from '../utils/security.js';
import { notifyUser } from './notifications.js';
import { emitToUser } from '../sockets/index.js';

const minuteOfDay = (time) => Number(time.slice(0, 2)) * 60 + Number(time.slice(3, 5));
function intervals(startTime, endTime) {
  const start = minuteOfDay(startTime); const end = minuteOfDay(endTime);
  if (start % 30 !== 0 || end % 30 !== 0 || end <= start || end - start < 30) throw new ApiError(422, 'Choose a valid time range in 30-minute increments.');
  const slots = [];
  for (let minute = start; minute < end; minute += 30) slots.push(String(Math.floor(minute / 60)).padStart(2, '0') + ':' + String(minute % 60).padStart(2, '0'));
  return slots;
}
function validDate(dateText) {
  const value = new Date(dateText + 'T00:00:00.000Z');
  return !Number.isNaN(value.getTime()) && value.toISOString().slice(0, 10) === dateText && value >= new Date(new Date().toISOString().slice(0, 10) + 'T00:00:00.000Z');
}
function scheduleAllows(provider, eventDate, startTime, endTime) {
  const date = new Date(eventDate + 'T00:00:00.000Z');
  const schedule = provider.availability.filter((item) => item.dayOfWeek === date.getUTCDay() && item.isAvailable);
  return schedule.some((item) => minuteOfDay(startTime) >= minuteOfDay(item.startTime) && minuteOfDay(endTime) <= minuteOfDay(item.endTime));
}

export async function createBooking(user, input) {
  if (!validDate(input.eventDate)) throw new ApiError(422, 'Event date must be today or a future date in YYYY-MM-DD format.');
  const slots = intervals(input.startTime, input.endTime);
  const [provider, service] = await Promise.all([
    Provider.findById(input.providerId),
    Service.findById(input.serviceId)
  ]);
  if (!provider || !provider.isActive || !provider.isVerified) throw new ApiError(404, 'This verified service provider is not available.');
  if (!service || !service.isActive || String(service.providerId) !== String(provider._id)) throw new ApiError(404, 'This service is not available for the selected provider.');
  if (provider.blockedDates.includes(input.eventDate) || !scheduleAllows(provider, input.eventDate, input.startTime, input.endTime)) {
    throw new ApiError(409, 'The provider is not available at the requested date and time.');
  }
  if (service.availability.length && !service.availability.includes(input.eventDate)) throw new ApiError(409, 'This service is not available on the requested date.');

  const existing = await BookingSlot.findOne({ providerId: provider._id, eventDate: input.eventDate, slot: { $in: slots } }).lean();
  if (existing) throw new ApiError(409, 'The provider is already booked for part of this time.');
  const amount = Math.round(service.price * (service.pricingUnit === 'perGuest' ? input.guestCount : 1));
  const booking = new Booking({
    ...input, userId: user._id, providerId: provider._id, serviceId: service._id,
    totalAmount: amount, paymentStatus: 'PENDING', bookingStatus: 'PENDING'
  });
  const slotDocs = slots.map((slot) => ({ providerId: provider._id, eventDate: input.eventDate, slot, bookingId: booking._id }));
  try {
    await BookingSlot.insertMany(slotDocs, { ordered: true });
    await booking.save();
    await Payment.create({ userId: user._id, bookingId: booking._id, amount, paymentMethod: 'RAZORPAY', status: 'PENDING' });
  } catch (error) {
    await BookingSlot.deleteMany({ bookingId: booking._id }).catch(() => {});
    await Booking.deleteOne({ _id: booking._id }).catch(() => {});
    if (error?.code === 11000) throw new ApiError(409, 'The provider was just booked for part of this time. Please select another slot.');
    throw error;
  }
  await Promise.allSettled([
    notifyUser(provider.userId, 'New booking request', 'A customer requested ' + service.title + ' for ' + input.eventDate + '.', 'BOOKING', { bookingId: booking.id }),
    notifyUser(user._id, 'Booking request received', 'Your request for ' + service.title + ' is pending provider confirmation.', 'BOOKING', { bookingId: booking.id })
  ]);
  emitToUser(provider.userId, 'booking:new', { bookingId: booking.id, bookingStatus: booking.bookingStatus });
  return booking;
}

export async function cancelBooking(booking, reason = '') {
  booking.bookingStatus = 'CANCELLED';
  booking.cancellationReason = reason;
  await booking.save();
  await BookingSlot.deleteMany({ bookingId: booking._id });
  return booking;
}

export async function hydrateBooking(booking) {
  return booking.populate([
    { path: 'userId', select: 'name email phone' },
    { path: 'providerId', select: 'businessName userId city' },
    { path: 'serviceId', select: 'title category price' }
  ]);
}

