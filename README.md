# Saldo

Private, Android-only personal finance app. The GitHub repository is named `finance-app`.

## Project structure

- `android/` — Kotlin and Jetpack Compose Android app
- `backend/` — ASP.NET Core API

## Current app state

The Android app has a dark interface with purple accents, green incoming amounts, and red outgoing amounts. Its four sections show the balance and monthly totals, searchable transactions with month and direction filters, monthly charts and top outflows, and account details with a refresh action. The overview also lists possible subscriptions and recurring incoming payments when the available history supports them.

Transactions receive local category suggestions. SPAR, BILLA, and PENNY are recognized as groceries; BIPA and dm as drugstores. Fast food, cafés, and food delivery are grouped under Restaurants. Known subscription services are suggested as subscriptions; matching monthly amounts alone are not treated as proof of a subscription. Tap a transaction to correct its category; corrections stay in the Android app's local preferences. An incoming sender can be marked as "Eltern" once, which applies to future incoming payments from the same sender. Recurring entries can be hidden and restored on the overview, and their latest booking date is shown.

The backend exposes `GET /api/health` and `GET /api/overview`. Without a connected sandbox account, the Android app displays the clearly marked sample data. OpenAPI is available in the Development environment.

## Open Banking direction

The provider is Enable Banking. Keep provider private keys, banking credentials, account data, and signing keys out of the Android app and Git; common key and certificate files are ignored locally.

The backend includes a sandbox-only Enable Banking authorization flow. When a sandbox account is connected, the Android app loads all available transaction pages before calculating monthly totals and charts. It labels the historical month shown, so synthetic data is not presented as current account activity. The connected Mock ASPSP data is synthetic. See [`docs/enable-banking-sandbox.md`](docs/enable-banking-sandbox.md) for local sandbox setup.

The app and backend support Bank Austria account authorization through a separate, restricted Enable Banking production application. Each user must register that production application, link their own account in the Control Panel, and then authorize the account in Saldo. The sandbox and production sessions are stored separately. The displayed transaction history is limited to the dates returned by the bank connection; older history may require a separate statement import. See [`docs/enable-banking-production.md`](docs/enable-banking-production.md) for the exact steps and emulator limitation.

Successful bank responses are encrypted in a local backend cache to reduce repeat requests. A temporary provider rate limit shows the last saved bank response if one exists, or a clear retry message; it never substitutes sample transactions for production bank data.

Older Bank Austria transactions can be imported once from a 24You CSV export with the local backend command described in [`docs/statement-import.md`](docs/statement-import.md). The import is encrypted outside the repository. When the bank temporarily rejects data requests, Saldo still shows imported transactions and clearly marks the current balance as unavailable. When bank data becomes available, transactions older than the oldest API transaction are added to the live view without duplicating the overlap. The app fetches only the latest bank transaction page and uses the imported statement for older history. A local pause blocks new bank requests after a provider rate limit.

## Run locally

- Start the backend project in `backend/Finance.Api/` with its `https` launch profile (ports `7129` for the desktop callback and `5006` for the emulator API). A locally trusted development certificate for `localhost` is required.
- Start the Android emulator, then run the `android/` app from Android Studio. The emulator reaches the computer's local API through `10.0.2.2`.
