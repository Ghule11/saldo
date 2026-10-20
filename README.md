# Saldo

Private, Android-only personal finance app. The GitHub repository is named `finance-app`.

## Project structure

- `android/` — Kotlin and Jetpack Compose Android app
- `backend/` — ASP.NET Core API

## Current app state

The Android app has a dark interface with purple accents, green incoming amounts, and red outgoing amounts. Its four sections show the balance and monthly totals, searchable transactions with month and direction filters, monthly charts and top outflows, and account details with a refresh action.

Transactions receive local category suggestions. Savings transfers appear separately and are excluded from income, expense, and spending charts. Tap a transaction to correct its category; corrections stay in the Android app's local preferences. The analysis also shows spending by category and possible recurring housing or subscription charges. Suggestions may be wrong and can be reset per transaction.

The backend exposes `GET /api/health` and `GET /api/overview`. Without a connected sandbox account, the Android app displays the clearly marked sample data. OpenAPI is available in the Development environment.

## Open Banking direction

The planned provider is Enable Banking. Real bank access has not been implemented. Keep provider private keys, banking credentials, account data, and signing keys out of the Android app and Git; common key and certificate files are ignored locally.

The backend includes a sandbox-only Enable Banking authorization flow. When a sandbox account is connected, the Android app loads all available transaction pages before calculating monthly totals and charts. It labels the historical month shown, so synthetic data is not presented as current account activity. The connected Mock ASPSP data is synthetic. See [`docs/enable-banking-sandbox.md`](docs/enable-banking-sandbox.md) for local sandbox setup.

Bank Austria is not connected. Access to a personal account would require a separate Enable Banking production application with the user's own account linked in restricted mode, plus a production authorization flow; the current backend intentionally accepts only sandbox applications.

## Run locally

- Start the backend project in `backend/Finance.Api/` with its `http` launch profile (port `5006`).
- Start the Android emulator, then run the `android/` app from Android Studio. The emulator reaches the computer's local API through `10.0.2.2`.
