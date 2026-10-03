# ShaadiX backend

REST API and real-time notifications for the ShaadiX event booking app. The backend uses Node.js, Express 5, MongoDB/Mongoose, JWT access and rotating refresh tokens, Socket.IO, Cloudinary uploads, and optional Razorpay checkout.

## Run locally

Requirements: Node.js 22.12 or newer and MongoDB.

1. Copy `.env.example` to `.env` and replace both JWT secrets with different random values (at least 32 characters each).
2. Set `MONGODB_URI`. A local MongoDB server is fine; MongoDB Atlas works too.
3. Run `npm install`, then `npm run seed` to add the initial service/event categories.
4. Run `npm run dev`. The health endpoint is `http://localhost:5000/api/health`; interactive API docs are at `/api/docs`.

Set `CLIENT_URL` to a comma-separated allowlist of frontend origins. In production use HTTPS, a managed MongoDB deployment, strong unique secrets, and a durable secret store. Never commit `.env` or provider credentials.

## Optional integrations

- **Email OTP:** Configure `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASSWORD`, and `SMTP_FROM`. In development without SMTP, verification codes are printed to the backend process log; production requires SMTP.
- **Cloudinary:** Configure all three `CLOUDINARY_*` values for image uploads. Upload routes accept image files only.
- **Razorpay:** Configure `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, and Android/client checkout with the returned order details. The server verifies signatures and fetches payment status from Razorpay. Empty credentials disable online checkout; Pay Later remains available.
- **Admin:** Set `SEED_ADMIN_EMAIL`, `SEED_ADMIN_PASSWORD` (12+ characters), and `SEED_ADMIN_PHONE`, then run `npm run seed:admin`. The command will not promote an existing non-admin account.
- **Demo listings:** Set `SEED_DEMO_DATA=true` before `npm run seed` to add three example providers/services in Chennai, Bengaluru, and Hyderabad. They are public listing fixtures; the seed creates no shared demo login credentials.

No real payment is charged by this backend in its unconfigured demo setup. Do not pass raw card details to the API. SMS OTP, FCM device-token registration/delivery, and a refund workflow require credentials/provider configuration and are not included in this first backend release; notifications are stored in MongoDB and sent to connected clients over Socket.IO.

## API overview

All routes are prefixed with `/api`; successful responses use `{ "success": true, "message": "...", "data": ... }`. Paginated endpoints include a `pagination` object. Send access tokens as `Authorization: Bearer <token>`.

| Area | Endpoints |
| --- | --- |
| Health/docs | `GET /health`, `/docs`, `/openapi.yaml` |
| Authentication | `/auth/register`, `/login`, `/verify-otp`, `/forgot-password`, `/reset-password`, `/refresh`, `/logout` |
| Discovery | `GET /categories`, `/services`, `/services/search`, `/services/:id`, `/providers`, `/providers/:id` |
| Customer | `/users/me`, `/users/me/avatar`, `/favorites`, `/bookings`, `/reviews`, `/notifications` |
| Provider | `POST/PUT/DELETE /providers`, `/provider/dashboard`, `/provider/bookings`, `/provider/earnings`, `/provider/availability`, `/services` |
| Booking/payment | `/bookings`, `/bookings/:id/cancel`, `/bookings/:id/confirm`, `/bookings/:id/complete`, `/payments/create-order`, `/payments/verify` |
| Admin | `/admin/dashboard`, `/admin/users`, `/admin/providers`, `/admin/bookings`, `/admin/categories`, and management actions |

See the OpenAPI file and Swagger UI for request/response schemas. Booking creation calculates prices on the server and reserves provider availability using a unique 30-minute slot index. Provider opening hours and event dates are evaluated in UTC; clients should send dates/times consistently in that convention until provider-specific time zones are added.

## Security and operational notes

- Passwords are bcrypt-hashed; access tokens are short-lived and refresh tokens are rotated and stored as hashes.
- OTP records expire automatically and are stored as hashes. Auth endpoints have a separate rate limit.
- Routes validate request data and enforce customer/provider/admin roles and record ownership.
- MongoDB query operators are rejected from JSON request bodies. Uploaded images have size and MIME checks.
- Configure backups, monitoring, log redaction, email delivery, and payment webhooks before production launch. This repository is a production-oriented starting point, not a substitute for staging, security review, or operational setup.

