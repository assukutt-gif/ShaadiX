# ShaadiX

**Plan. Book. Celebrate.** ShaadiX is a native Android event discovery and booking app built with Kotlin, Jetpack Compose, Material 3 and Navigation Compose. The app includes customer discovery and booking, authentication, provider tools, admin management, notifications, favorites and a sample-data mode.

## Android app setup

1. Open this repository in Android Studio with JDK 17 and Android SDK 36 installed.
2. Start the API described in [backend/README.md](backend/README.md). For an Android emulator, the default API address is `http://10.0.2.2:5000/api/`.
3. To use another API host, set the Gradle property `SHAADIX_API_BASE_URL` to an API root ending in `/api/`, for example `-PSHAADIX_API_BASE_URL=https://api.example.com/api/`. Use HTTPS for deployed services.
4. Start the app. If the API is unavailable, ShaadiX keeps sample listings available for exploration. Live sign-in, account, provider and admin actions require a running backend and the corresponding role.

## Connected features

- Email or international phone number sign-in with password, email OTP verification, password reset, and refresh-token rotation.
- Service discovery with search and filters, service details, favorites, booking creation and cancellation.
- Provider profile, service listing image uploads, availability and booking management.
- Admin overview and moderation/management screens, gated by backend admin permissions.
- Razorpay checkout. Configure the key ID and secret in the backend environment; the Android app receives only the public key ID and server-created order. Payment signatures are sent back to the backend for verification. No card data is stored by ShaadiX.
- Dark mode, onboarding preference and encrypted-at-rest access/refresh tokens using the Android Keystore.

## Firebase push notifications

For push notification display, create a Firebase Android app using application ID `com.assukutt.shaadix`, download `google-services.json` into `app/`, and configure Firebase Cloud Messaging. The Firebase Gradle plugin is applied only when this file exists. Notification permission is needed on Android 13 and later. The backend currently stores notifications and sends them over its real-time channel; FCM device-token registration/delivery requires an integration endpoint and credentials.

## Demo behavior and configuration

- Guest mode uses realistic local sample listings and local-only booking/favorite interactions. Demo checkout never charges money.
- Online booking and payment need a real signed-in customer, a live backend service listing, and configured Razorpay credentials on the backend.
- Cloud image uploads require Cloudinary settings on the backend. Never place Razorpay secrets, Firebase service account credentials, or other private keys in the Android client.
- Cleartext networking is permitted only for the Android emulator host `10.0.2.2`; deployed APIs should use HTTPS.

The companion Node.js/MongoDB API, environment configuration, sample data seeding and endpoint documentation are in [backend/README.md](backend/README.md).

