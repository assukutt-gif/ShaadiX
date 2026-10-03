import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import compression from 'compression';
import morgan from 'morgan';
import rateLimit from 'express-rate-limit';
import swaggerUi from 'swagger-ui-express';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { env } from './config/env.js';
import apiRoutes from './routes/index.js';
import { errorHandler, notFound } from './middleware/errors.js';
import { mongoSanitize } from './middleware/mongoSanitize.js';
import { ApiError } from './utils/http.js';

const app = express();
const here = path.dirname(fileURLToPath(import.meta.url));
const openapi = readFileSync(path.resolve(here, '../openapi.yaml'), 'utf8');

app.disable('x-powered-by');
if (env.NODE_ENV === 'production') app.set('trust proxy', 1);
app.use(helmet());
app.use(cors({
  origin(origin, callback) {
    if (!origin || env.allowedOrigins.includes(origin)) return callback(null, true);
    return callback(new ApiError(403, 'Origin is not allowed by CORS.'));
  },
  credentials: true,
  methods: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS'],
  allowedHeaders: ['Authorization', 'Content-Type', 'X-Request-Id']
}));
app.use(compression());
app.use(express.json({ limit: '1mb', strict: true }));
app.use(express.urlencoded({ extended: false, limit: '1mb' }));
app.use(mongoSanitize);
if (env.NODE_ENV !== 'test') app.use(morgan(env.NODE_ENV === 'production' ? 'combined' : 'dev'));
app.use('/api', rateLimit({
  windowMs: env.RATE_LIMIT_WINDOW_MS, limit: env.RATE_LIMIT_MAX,
  standardHeaders: 'draft-8', legacyHeaders: false,
  message: { success: false, message: 'Too many requests. Please try again later.' }
}));
app.get('/api/openapi.yaml', (_req, res) => res.type('text/yaml').send(openapi));
app.use('/api/docs', swaggerUi.serve, swaggerUi.setup(undefined, { swaggerOptions: { url: '/api/openapi.yaml' }, customSiteTitle: 'ShaadiX API' }));
app.use('/api', apiRoutes);
app.use(notFound);
app.use(errorHandler);

export default app;

