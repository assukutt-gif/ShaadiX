import { createServer } from 'node:http';
import app from './app.js';
import { env } from './config/env.js';
import { connectDatabase } from './config/database.js';
import { attachSockets } from './sockets/index.js';
import mongoose from 'mongoose';

let server;
async function start() {
  await connectDatabase();
  server = createServer(app);
  attachSockets(server);
  server.listen(env.PORT, '0.0.0.0', () => console.info('ShaadiX API listening on port ' + env.PORT));
}
async function shutdown(signal) {
  console.info(signal + ' received; shutting down.');
  if (server) await new Promise((resolve) => server.close(resolve));
  await mongoose.disconnect();
  process.exit(0);
}
process.once('SIGINT', () => shutdown('SIGINT'));
process.once('SIGTERM', () => shutdown('SIGTERM'));
process.on('unhandledRejection', (error) => { console.error('Unhandled rejection', error); shutdown('unhandledRejection'); });
start().catch((error) => { console.error('Unable to start ShaadiX API', error); process.exit(1); });

