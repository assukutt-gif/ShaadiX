import { Notification } from '../models/index.js';
import { emitToUser } from '../sockets/index.js';

export async function notifyUser(userId, title, message, type = 'SYSTEM', data = {}) {
  const notification = await Notification.create({ userId, title, message, type, data });
  const payload = notification.toObject();
  emitToUser(userId, 'notification:new', payload);
  return notification;
}

