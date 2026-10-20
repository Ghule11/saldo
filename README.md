# Saldo

Private, Android-only personal finance app. The GitHub repository is named `finance-app`.

## Project structure

- `android/` — Kotlin and Jetpack Compose Android app
- `backend/` — ASP.NET Core API

## Current app state

The Android app currently shows clearly marked sample balances and transactions, with a category-filtered transaction list. No bank accounts are connected yet.

The backend exposes `GET /api/health` and `GET /api/overview`. The Android app fetches its sample balance, monthly totals, spending categories, and transactions from the backend. OpenAPI is available in the Development environment.

## Open Banking direction

The planned provider is Enable Banking. Real bank access has not been implemented. Keep provider private keys, banking credentials, account data, and signing keys out of the Android app and Git; common key and certificate files are ignored locally.

## Run locally

- Start the backend project in `backend/Finance.Api/` with its `http` launch profile (port `5006`).
- Start the Android emulator, then run the `android/` app from Android Studio. The emulator reaches the computer's local API through `10.0.2.2`.
