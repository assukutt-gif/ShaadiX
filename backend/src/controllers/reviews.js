import { Booking, Provider, Review } from '../models/index.js';
import { uploadImages } from '../services/images.js';
import { notifyUser } from '../services/notifications.js';
import { ApiError, asyncHandler, ok, pageOptions, pagination } from '../utils/http.js';

async function refreshRating(providerId) {
  const [summary] = await Review.aggregate([
    { $match: { providerId, isVisible: true } },
    { $group: { _id: '$providerId', rating: { $avg: '$rating' }, totalReviews: { $sum: 1 } } }
  ]);
  await Provider.updateOne({ _id: providerId }, { $set: { rating: summary?.rating ? Math.round(summary.rating * 10) / 10 : 0, totalReviews: summary?.totalReviews || 0 } });
}
export const createReview = asyncHandler(async (req, res) => {
  const booking = await Booking.findOne({ _id: req.body.bookingId, userId: req.user._id, bookingStatus: 'COMPLETED' });
  if (!booking) throw new ApiError(409, 'You can review a booking after the event is completed.');
  if (await Review.exists({ bookingId: booking._id })) throw new ApiError(409, 'A review already exists for this booking.');
  const images = await uploadImages(req.files, 'shaadix/reviews/' + req.user.id);
  const review = await Review.create({ userId: req.user._id, providerId: booking.providerId, bookingId: booking._id, rating: req.body.rating, comment: req.body.comment, images });
  await refreshRating(booking.providerId);
  const provider = await Provider.findById(booking.providerId).select('userId');
  await notifyUser(provider.userId, 'New review', 'A customer reviewed your service.', 'REVIEW', { reviewId: review.id });
  return ok(res, review, 'Review submitted.', 201);
});
export const listProviderReviews = asyncHandler(async (req, res) => {
  const provider = await Provider.findById(req.params.id).select('_id');
  if (!provider) throw new ApiError(404, 'Provider not found.');
  const { page, limit, skip } = pageOptions(req.query);
  const [rows, total] = await Promise.all([
    Review.find({ providerId: provider._id, isVisible: true }).populate('userId', 'name profileImage').sort({ createdAt: -1 }).skip(skip).limit(limit),
    Review.countDocuments({ providerId: provider._id, isVisible: true })
  ]);
  return ok(res, rows, 'Reviews retrieved.', 200, pagination(page, limit, total));
});
export const updateReview = asyncHandler(async (req, res) => {
  const review = await Review.findOne({ _id: req.params.id, userId: req.user._id });
  if (!review) throw new ApiError(404, 'Review not found.');
  review.rating = req.body.rating; review.comment = req.body.comment;
  if (req.files?.length) review.images.push(...await uploadImages(req.files, 'shaadix/reviews/' + req.user.id));
  await review.save(); await refreshRating(review.providerId);
  return ok(res, review, 'Review updated.');
});
export const deleteReview = asyncHandler(async (req, res) => {
  const review = await Review.findOne({ _id: req.params.id, userId: req.user._id });
  if (!review) throw new ApiError(404, 'Review not found.');
  await Review.deleteOne({ _id: review._id }); await refreshRating(review.providerId);
  return ok(res, {}, 'Review removed.');
});
export const reportReview = asyncHandler(async (req, res) => {
  const review = await Review.findOne({ _id: req.params.id, isVisible: true });
  if (!review) throw new ApiError(404, 'Review not found.');
  if (String(review.userId) === String(req.user._id)) throw new ApiError(400, 'You cannot report your own review.');
  review.isReported = true;
  review.reportReason = req.body.reason;
  await review.save();
  return ok(res, { id: review.id, isReported: review.isReported }, 'Review reported to the admin team.');
});

