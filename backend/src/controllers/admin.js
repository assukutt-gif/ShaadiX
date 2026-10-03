import { Booking, Category, Favorite, Notification, Payment, Provider, Review, Service, User, RefreshSession } from '../models/index.js';
import { notifyUser } from '../services/notifications.js';
import { ApiError, asyncHandler, ok, pageOptions, pagination } from '../utils/http.js';

export const dashboard = asyncHandler(async (_req, res) => {
  const since = new Date(Date.now() - 30 * 86400000);
  const [totalUsers, totalProviders, totalBookings, confirmedBookings, pendingBookings, cancelledBookings, revenue, newRegistrations, recentBookings] = await Promise.all([
    User.countDocuments({ isActive: true }),
    Provider.countDocuments({ isActive: true }),
    Booking.countDocuments(),
    Booking.countDocuments({ bookingStatus: 'CONFIRMED' }),
    Booking.countDocuments({ bookingStatus: 'PENDING' }),
    Booking.countDocuments({ bookingStatus: 'CANCELLED' }),
    Payment.aggregate([{ $match: { status: 'PAID' } }, { $group: { _id: null, total: { $sum: '$amount' } } }]),
    User.countDocuments({ createdAt: { $gte: since } }),
    Booking.find().populate('userId', 'name email').populate('providerId', 'businessName city').populate('serviceId', 'title').sort({ createdAt: -1 }).limit(10)
  ]);
  return ok(res, {
    totalUsers, totalProviders, totalBookings, confirmedBookings, pendingBookings, cancelledBookings,
    totalRevenue: revenue[0]?.total || 0, newRegistrations, recentBookings
  });
});
export const listUsers = asyncHandler(async (req, res) => {
  const { page, limit, skip } = pageOptions(req.query);
  const filter = {};
  if (['customer', 'provider', 'admin'].includes(req.query.role)) filter.role = req.query.role;
  if (req.query.search) {
    const text = String(req.query.search).slice(0, 100).replace(/[.*+?^$()|[\]\\]/g, '\\$&');
    filter.$or = [{ name: new RegExp(text, 'i') }, { email: new RegExp(text, 'i') }, { phone: new RegExp(text, 'i') }];
  }
  const [rows, total] = await Promise.all([
    User.find(filter).select('_id name email phone role profileImage location isVerified isActive createdAt').sort({ createdAt: -1 }).skip(skip).limit(limit).lean(),
    User.countDocuments(filter)
  ]);
  return ok(res, rows, 'Users retrieved.', 200, pagination(page, limit, total));
});
export const listProviders = asyncHandler(async (req, res) => {
  const { page, limit, skip } = pageOptions(req.query);
  const filter = {};
  if (req.query.verified === 'true') filter.isVerified = true;
  if (req.query.verified === 'false') filter.isVerified = false;
  const [rows, total] = await Promise.all([
    Provider.find(filter).populate('userId', 'name email phone isActive').sort({ createdAt: -1 }).skip(skip).limit(limit),
    Provider.countDocuments(filter)
  ]);
  return ok(res, rows, 'Providers retrieved.', 200, pagination(page, limit, total));
});
export const listBookings = asyncHandler(async (req, res) => {
  const { page, limit, skip } = pageOptions(req.query);
  const filter = {};
  if (['PENDING', 'CONFIRMED', 'REJECTED', 'CANCELLED', 'COMPLETED'].includes(req.query.status)) filter.bookingStatus = req.query.status;
  const [rows, total] = await Promise.all([
    Booking.find(filter).populate('userId', 'name email').populate('providerId', 'businessName city').populate('serviceId', 'title').sort({ createdAt: -1 }).skip(skip).limit(limit),
    Booking.countDocuments(filter)
  ]);
  return ok(res, rows, 'Bookings retrieved.', 200, pagination(page, limit, total));
});
export const verifyProvider = asyncHandler(async (req, res) => {
  const provider = await Provider.findById(req.params.id);
  if (!provider) throw new ApiError(404, 'Provider not found.');
  provider.isVerified = true; await provider.save();
  await notifyUser(provider.userId, 'Provider profile approved', 'Your business profile is now visible to customers.', 'SYSTEM', { providerId: provider.id });
  return ok(res, provider, 'Provider approved.');
});
export const deleteUser = asyncHandler(async (req, res) => {
  if (String(req.params.id) === String(req.user._id)) throw new ApiError(400, 'You cannot deactivate your own administrator account.');
  const user = await User.findById(req.params.id);
  if (!user) throw new ApiError(404, 'User not found.');
  user.isActive = false; user.tokenVersion += 1; await user.save();
  await RefreshSession.updateMany({ userId: user._id, revokedAt: null }, { $set: { revokedAt: new Date() } });
  await Provider.updateOne({ userId: user._id }, { $set: { isActive: false } });
  await Service.updateMany({ providerId: { $in: await Provider.find({ userId: user._id }).distinct('_id') } }, { $set: { isActive: false } });
  return ok(res, {}, 'User deactivated.');
});
export const deleteProvider = asyncHandler(async (req, res) => {
  const provider = await Provider.findById(req.params.id);
  if (!provider) throw new ApiError(404, 'Provider not found.');
  provider.isActive = false; await provider.save();
  await Service.updateMany({ providerId: provider._id }, { $set: { isActive: false } });
  return ok(res, {}, 'Provider deactivated.');
});
export const createCategory = asyncHandler(async (req, res) => {
  const category = await Category.create(req.body);
  return ok(res, category, 'Category created.', 201);
});
export const updateCategory = asyncHandler(async (req, res) => {
  const category = await Category.findByIdAndUpdate(req.params.id, { $set: req.body }, { new: true, runValidators: true });
  if (!category) throw new ApiError(404, 'Category not found.');
  return ok(res, category, 'Category updated.');
});
export const deleteCategory = asyncHandler(async (req, res) => {
  const category = await Category.findByIdAndUpdate(req.params.id, { $set: { isActive: false } }, { new: true });
  if (!category) throw new ApiError(404, 'Category not found.');
  return ok(res, {}, 'Category deactivated.');
});
export const listCategories = asyncHandler(async (_req, res) => ok(res, await Category.find({ isActive: true }).sort({ name: 1 }).lean(), 'Categories retrieved.'));

