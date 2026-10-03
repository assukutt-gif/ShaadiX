import mongoose from 'mongoose';
import { Booking, Favorite, Provider, Service, User } from '../models/index.js';
import { uploadImages } from '../services/images.js';
import { ApiError, asyncHandler, escapeRegex, ok, pageOptions, pagination } from '../utils/http.js';
import { notifyUser } from '../services/notifications.js';

async function ownProvider(user, allowUnverified = false) {
  const provider = await Provider.findOne({ userId: user._id, isActive: true });
  if (!provider) throw new ApiError(404, 'Create a provider profile first.');
  if (!allowUnverified && !provider.isVerified) throw new ApiError(403, 'Your provider account is awaiting approval.');
  return provider;
}
function pickSort(sortBy, order, allowed) {
  const field = allowed.includes(sortBy) ? sortBy : 'createdAt';
  return { [field]: order, _id: 1 };
}

export const getProfile = asyncHandler(async (req, res) => {
  const user = await User.findById(req.user._id).select('-password -tokenVersion');
  return ok(res, user);
});
export const updateProfile = asyncHandler(async (req, res) => {
  const user = await User.findByIdAndUpdate(req.user._id, { $set: req.body }, { new: true, runValidators: true }).select('-password -tokenVersion');
  return ok(res, user, 'Profile updated.');
});
export const profileImage = asyncHandler(async (req, res) => {
  if (!req.file) throw new ApiError(400, 'Choose an image to upload.');
  const [url] = await uploadImages([req.file], 'shaadix/profiles/' + req.user.id);
  const user = await User.findByIdAndUpdate(req.user._id, { profileImage: url }, { new: true }).select('-password -tokenVersion');
  return ok(res, user, 'Profile image updated.');
});

export const listProviders = asyncHandler(async (req, res) => {
  const { page, limit, skip, order, sortBy } = pageOptions(req.query);
  const filter = { isActive: true, isVerified: true };
  if (req.query.city) filter.city = new RegExp(escapeRegex(req.query.city), 'i');
  if (req.query.category) filter.category = new RegExp(escapeRegex(req.query.category), 'i');
  if (req.query.rating) filter.rating = { $gte: Number(req.query.rating) };
  if (req.query.keyword) filter.$or = ['businessName', 'description', 'category', 'city'].map((field) => ({ [field]: new RegExp(escapeRegex(req.query.keyword), 'i') }));
  const [rows, total] = await Promise.all([
    Provider.find(filter).select('-availability -blockedDates').sort(pickSort(sortBy, order, ['createdAt', 'rating', 'businessName'])).skip(skip).limit(limit).lean(),
    Provider.countDocuments(filter)
  ]);
  return ok(res, rows, 'Providers retrieved.', 200, pagination(page, limit, total));
});

export const getProvider = asyncHandler(async (req, res) => {
  const provider = await Provider.findOne({ _id: req.params.id, isActive: true, isVerified: true }).select('-availability -blockedDates').lean();
  if (!provider) throw new ApiError(404, 'Provider not found.');
  const services = await Service.find({ providerId: provider._id, isActive: true }).sort({ createdAt: -1 }).lean();
  return ok(res, { ...provider, services });
});

export const createProvider = asyncHandler(async (req, res) => {
  if (req.user.role !== 'provider' && req.user.role !== 'admin') throw new ApiError(403, 'Register as a service provider to create a business profile.');
  if (await Provider.exists({ userId: req.user._id })) throw new ApiError(409, 'A provider profile already exists for this account.');
  const provider = await Provider.create({ ...req.body, userId: req.user._id, isVerified: false, images: [] });
  return ok(res, provider, 'Provider profile submitted for review.', 201);
});

export const updateProvider = asyncHandler(async (req, res) => {
  const provider = await Provider.findOne({ _id: req.params.id, isActive: true });
  if (!provider) throw new ApiError(404, 'Provider not found.');
  if (req.user.role !== 'admin' && String(provider.userId) !== String(req.user._id)) throw new ApiError(403, 'You can only edit your own provider profile.');
  const changes = { ...req.body };
  delete changes.userId; delete changes.isVerified; delete changes.rating; delete changes.totalReviews;
  Object.assign(provider, changes);
  await provider.save();
  return ok(res, provider, 'Provider profile updated.');
});

export const deleteProvider = asyncHandler(async (req, res) => {
  const provider = await Provider.findById(req.params.id);
  if (!provider) throw new ApiError(404, 'Provider not found.');
  if (req.user.role !== 'admin' && String(provider.userId) !== String(req.user._id)) throw new ApiError(403, 'You can only close your own provider profile.');
  provider.isActive = false; await provider.save();
  await Service.updateMany({ providerId: provider._id }, { $set: { isActive: false } });
  return ok(res, {}, 'Provider profile closed.');
});

export const providerDashboard = asyncHandler(async (req, res) => {
  const provider = await ownProvider(req.user, true);
  const [totalBookings, pendingBookings, confirmedBookings, completedBookings, earnings] = await Promise.all([
    Booking.countDocuments({ providerId: provider._id }),
    Booking.countDocuments({ providerId: provider._id, bookingStatus: 'PENDING' }),
    Booking.countDocuments({ providerId: provider._id, bookingStatus: 'CONFIRMED' }),
    Booking.countDocuments({ providerId: provider._id, bookingStatus: 'COMPLETED' }),
    Booking.aggregate([{ $match: { providerId: provider._id, paymentStatus: 'PAID' } }, { $group: { _id: null, total: { $sum: '$totalAmount' } } }])
  ]);
  return ok(res, { provider, totalBookings, pendingBookings, confirmedBookings, completedBookings, earnings: earnings[0]?.total || 0 });
});

export const providerBookings = asyncHandler(async (req, res) => {
  const provider = await ownProvider(req.user, true);
  const { page, limit, skip } = pageOptions(req.query);
  const filter = { providerId: provider._id };
  if (['PENDING', 'CONFIRMED', 'REJECTED', 'CANCELLED', 'COMPLETED'].includes(req.query.status)) filter.bookingStatus = req.query.status;
  const [rows, total] = await Promise.all([
    Booking.find(filter).populate('userId', 'name email phone').populate('serviceId', 'title category').sort({ eventDate: 1, startTime: 1 }).skip(skip).limit(limit),
    Booking.countDocuments(filter)
  ]);
  return ok(res, rows, 'Provider bookings retrieved.', 200, pagination(page, limit, total));
});

export const providerEarnings = asyncHandler(async (req, res) => {
  const provider = await ownProvider(req.user, true);
  const rows = await Booking.aggregate([
    { $match: { providerId: provider._id, paymentStatus: 'PAID' } },
    { $addFields: { eventDateValue: { $dateFromString: { dateString: '$eventDate' } } } },
    { $group: { _id: { year: { $year: '$eventDateValue' }, month: { $month: '$eventDateValue' } }, gross: { $sum: '$totalAmount' }, bookings: { $sum: 1 } } },
    { $sort: { '_id.year': -1, '_id.month': -1 } }, { $limit: 24 }
  ]);
  return ok(res, { total: rows.reduce((sum, row) => sum + row.gross, 0), monthly: rows });
});

export const updateAvailability = asyncHandler(async (req, res) => {
  const provider = await ownProvider(req.user, true);
  provider.availability = req.body.availability;
  if (req.body.blockedDates) provider.blockedDates = [...new Set(req.body.blockedDates)];
  await provider.save();
  return ok(res, { availability: provider.availability, blockedDates: provider.blockedDates }, 'Availability updated.');
});

async function serviceFilter(query) {
  const filter = { isActive: true };
  const providerFilter = { isActive: true, isVerified: true };
  if (query.city) providerFilter.city = new RegExp(escapeRegex(query.city), 'i');
  if (query.rating) providerFilter.rating = { $gte: Number(query.rating) };
  if (query.category) filter.category = new RegExp(escapeRegex(query.category), 'i');
  if (query.keyword) {
    const regex = new RegExp(escapeRegex(query.keyword), 'i');
    filter.$or = [{ title: regex }, { description: regex }, { category: regex }, { location: regex }];
  }
  const minimum = Number(query.minimumPrice ?? query.minPrice);
  const maximum = Number(query.maximumPrice ?? query.maxPrice);
  if (Number.isFinite(minimum) || Number.isFinite(maximum)) {
    filter.price = {};
    if (Number.isFinite(minimum)) filter.price.$gte = minimum;
    if (Number.isFinite(maximum)) filter.price.$lte = maximum;
  }
  const providers = await Provider.find(providerFilter).select('_id blockedDates availability').lean();
  let providerIds = providers.map((provider) => provider._id);
  if (query.eventDate) {
    const weekday = new Date(query.eventDate + 'T00:00:00.000Z').getUTCDay();
    providerIds = providers.filter((p) => !p.blockedDates.includes(query.eventDate) && (!p.availability.length || p.availability.some((a) => a.dayOfWeek === weekday && a.isAvailable))).map((p) => p._id);
  }
  filter.providerId = { $in: providerIds };
  return filter;
}

export const listServices = asyncHandler(async (req, res) => {
  const { page, limit, skip, order } = pageOptions(req.query);
  const filter = await serviceFilter(req.query);
  const sortField = ({ price: 'price', createdAt: 'createdAt', title: 'title', rating: 'createdAt' })[req.query.sortBy] || 'createdAt';
  const [rows, total] = await Promise.all([
    Service.find(filter).populate({ path: 'providerId', select: 'businessName city rating totalReviews isVerified', match: { isVerified: true, isActive: true } }).sort({ [sortField]: order, _id: 1 }).skip(skip).limit(limit).lean(),
    Service.countDocuments(filter)
  ]);
  const visible = rows.filter((row) => row.providerId);
  return ok(res, visible, 'Services retrieved.', 200, pagination(page, limit, total));
});

export const getService = asyncHandler(async (req, res) => {
  const service = await Service.findOne({ _id: req.params.id, isActive: true }).populate({ path: 'providerId', select: 'businessName description phone email address city images rating totalReviews availability isVerified', match: { isVerified: true, isActive: true } }).lean();
  if (!service?.providerId) throw new ApiError(404, 'Service not found.');
  return ok(res, service);
});

export const createService = asyncHandler(async (req, res) => {
  const provider = await ownProvider(req.user);
  const images = await uploadImages(req.files, 'shaadix/services/' + provider.id);
  const { providerId: ignored, ...input } = req.body;
  const service = await Service.create({ ...input, providerId: provider._id, images });
  return ok(res, service, 'Service created.', 201);
});

export const updateService = asyncHandler(async (req, res) => {
  const service = await Service.findById(req.params.id);
  if (!service) throw new ApiError(404, 'Service not found.');
  const provider = await Provider.findById(service.providerId);
  if (req.user.role !== 'admin' && String(provider?.userId) !== String(req.user._id)) throw new ApiError(403, 'You can only update your own services.');
  if (req.user.role !== 'admin' && !provider?.isVerified) throw new ApiError(403, 'Your provider profile is awaiting approval.');
  const additions = await uploadImages(req.files, 'shaadix/services/' + service.providerId);
  const { providerId: ignored, ...changes } = req.body;
  Object.assign(service, changes);
  if (additions.length) service.images.push(...additions);
  await service.save();
  return ok(res, service, 'Service updated.');
});

export const deleteService = asyncHandler(async (req, res) => {
  const service = await Service.findById(req.params.id);
  if (!service) throw new ApiError(404, 'Service not found.');
  const provider = await Provider.findById(service.providerId);
  if (req.user.role !== 'admin' && String(provider?.userId) !== String(req.user._id)) throw new ApiError(403, 'You can only remove your own services.');
  service.isActive = false; await service.save();
  return ok(res, {}, 'Service removed.');
});

export const listFavorites = asyncHandler(async (req, res) => {
  const rows = await Favorite.find({ userId: req.user._id }).populate({ path: 'providerId', match: { isActive: true, isVerified: true } }).sort({ createdAt: -1 }).lean();
  return ok(res, rows.filter((row) => row.providerId));
});
export const addFavorite = asyncHandler(async (req, res) => {
  const provider = await Provider.findOne({ _id: req.params.providerId, isActive: true, isVerified: true });
  if (!provider) throw new ApiError(404, 'Verified provider not found.');
  const favorite = await Favorite.findOneAndUpdate({ userId: req.user._id, providerId: provider._id }, { $setOnInsert: { userId: req.user._id, providerId: provider._id } }, { upsert: true, new: true });
  return ok(res, favorite, 'Provider added to favorites.', 201);
});
export const removeFavorite = asyncHandler(async (req, res) => {
  const result = await Favorite.deleteOne({ userId: req.user._id, providerId: req.params.providerId });
  if (!result.deletedCount) throw new ApiError(404, 'Favorite not found.');
  return ok(res, {}, 'Provider removed from favorites.');
});

export { ownProvider };

