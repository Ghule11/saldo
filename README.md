# Saldo

Private, Android-only personal finance app. The GitHub repository is named `finance-app`.

## Project structure

- `android/` — Kotlin and Jetpack Compose Android app
- `backend/` — ASP.NET Core API

## Current app state

The Android app currently shows clearly marked sample balances and transactions, with a category-filtered transaction list. No bank accounts are connected yet.

The backend exposes `GET /api/health` to report that the API is responding. OpenAPI is available in the Development environment.

## Open Banking direction

The planned provider is Enable Banking for personal, non-commercial access to the owner's linked accounts. Real bank access has not been implemented. Keep provider private keys, banking credentials, account data, and signing keys out of the Android app and Git.

## Run locally

- Open `android/` in Android Studio to run the Android app.
- Run the backend project in `backend/Finance.Api/` to serve the API.
