import jwt from 'jsonwebtoken';
import { Server } from 'socket.io';
import { env } from '../config/env.js';
import { User } from '../models/index.js';

let io;
export function attachSockets(server) {
  io = new Server(server, {
    cors: {
      origin(origin, callback) {
        if (!origin || env.allowedOrigins.includes(origin)) return callback(null, true);
        return callback(new Error('Origin is not allowed.'));
      },
      methods: ['GET', 'POST']
    }
  });
  io.use(async (socket, next) => {
    try {
      const token = socket.handshake.auth?.token;
      if (!token) return next(new Error('Authentication required.'));
      const payload = jwt.verify(token, env.JWT_SECRET, { issuer: 'shaadix-api', audience: 'shaadix-client' });
      if (payload.type !== 'access') return next(new Error('Invalid socket token.'));
      const user = await User.findById(payload.sub).select('_id isActive');
      if (!user?.isActive) return next(new Error('Account unavailable.'));
      socket.data.userId = user.id;
      next();
    } catch { next(new Error('Socket authentication failed.')); }
  });
  io.on('connection', (socket) => socket.join('user:' + socket.data.userId));
  return io;
}
export const getIO = () => io;
export function emitToUser(userId, event, payload) {
  io?.to('user:' + userId).emit(event, payload);
}

