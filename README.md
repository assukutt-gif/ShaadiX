# ShaadiX

**Plan. Book. Celebrate.** ShaadiX is a native Android event-service discovery and booking starter built with Kotlin, Jetpack Compose, Material 3 and Navigation Compose. The current version is a polished demo: it starts in a guest flow, includes sample listings, local search/favorites/booking state, mock checkout, bookings, and provider/admin dashboard surfaces.

## Open in Android Studio

Open this repository as a Gradle project. Use JDK 17 and Android SDK 36. The application ID is **com.assukutt.shaadix**; minimum Android version is API 24.

## Firebase setup

1. Create an Android app in Firebase with application ID com.assukutt.shaadix.
2. Download google-services.json into app/ (it is intentionally ignored by Git).
3. Enable Email/Password and Phone authentication as needed; add Google sign-in SHA fingerprints for your app.
4. Create Firestore and Storage, then deploy firestore.rules and storage.rules.
5. Add the Firebase project configuration before using email sign-in. Without it, guest mode, sample discovery and mock booking run locally.

The Firebase repository currently provides email sign-in and a booking write primitive. The demo UI uses local sample state for immediate exploration. Wire account creation, live Firestore streams, uploads, provider approval, and FCM token registration before using this as a live service.

## Demo behavior

- The OTP screen accepts 123456 for the local preview flow.
- Confirming a booking updates the on-device demo list; payment choices are UI only and no money is collected.
- Provider studio and admin overview use sample metrics. Server-enforced roles must be assigned through a trusted backend.
- Service photography loads from Unsplash URLs; replace with licensed production assets before publishing.

## Production follow-up

Payment intent creation, refunds, identity checks, rate limiting, audit logs, phone OTP delivery, Google credential exchange, notification channels, review photo uploads, and provider/admin workflows need trusted backend functions. Never put payment secrets or service-account keys in the Android client. Review the Firestore/Storage rules against the final data schema before deployment.
