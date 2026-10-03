import { Notification } from '../models/index.js';
import { ApiError, asyncHandler, ok, pageOptions, pagination } from '../utils/http.js';

export const listNotifications = asyncHandler(async (req, res) => {
  const { page, limit, skip } = pageOptions(req.query);
  const [rows, total, unreadCount] = await Promise.all([
    Notification.find({ userId: req.user._id }).sort({ createdAt: -1 }).skip(skip).limit(limit).lean(),
    Notification.countDocuments({ userId: req.user._id }),
    Notification.countDocuments({ userId: req.user._id, isRead: false })
  ]);
  return ok(res, { items: rows, unreadCount }, 'Notifications retrieved.', 200, pagination(page, limit, total));
});
export const markNotificationRead = asyncHandler(async (req, res) => {
  const notification = await Notification.findOneAndUpdate({ _id: req.params.id, userId: req.user._id }, { $set: { isRead: true } }, { new: true });
  if (!notification) throw new ApiError(404, 'Notification not found.');
  return ok(res, notification, 'Notification marked as read.');
});

