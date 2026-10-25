# Saldo

Private, Android-only personal finance app. The GitHub repository is named `finance-app`.

## Project structure

- `android/` — Kotlin and Jetpack Compose Android app
- `backend/` — ASP.NET Core API
- `cloudflare/` — separate sandbox and production Workers for PC-independent banking

## Current app state

The Android app has a dark interface with purple accents, green incoming amounts, and red outgoing amounts. The compact overview shows a centered balance, monthly totals, and matching cards for recurring incoming payments and subscriptions with their estimated monthly sums. The other sections show searchable transactions grouped by booking day with month, direction, and category filters plus totals for the matching directions, monthly charts and top outflows, and account details with a refresh action. Tapping a transaction opens its full booking text, original merchant or sender, date, category, source, and available history with that merchant. Returning to the transaction list preserves its filters and scroll position.

Transactions receive local category suggestions. SPAR, BILLA, and PENNY are recognized as groceries; BIPA and dm as drugstores. Fast food, cafés, and food delivery are grouped under Restaurants. Known subscription services are suggested as subscriptions; matching monthly amounts alone are not treated as proof of a subscription. Open an expense and choose "Kategorie ändern" to correct only that booking or save a merchant rule for future bookings; merchant rules can be removed on the account screen. An incoming sender can be marked as "Eltern" once, which applies to future incoming payments from the same sender. Possible subscriptions can be explicitly confirmed or rejected. Confirmed subscriptions show an estimated monthly total based on each merchant's latest charge; rejected suggestions can be reset on the account screen. These choices stay in the Android app's local preferences and require no extra bank requests. Recurring income entries can be hidden and restored on the overview, and their latest booking date is shown.

The backend exposes `GET /api/health` and `GET /api/overview`. Without a connected sandbox account, the Android app displays the clearly marked sample data. OpenAPI is available in the Development environment.

## Open Banking direction

The provider is Enable Banking. Keep provider private keys, banking credentials, account data, and signing keys out of the Android app and Git; common key and certificate files are ignored locally.

The backend includes a sandbox-only Enable Banking authorization flow. When a sandbox account is connected, the Android app loads all available transaction pages before calculating monthly totals and charts. It labels the historical month shown, so synthetic data is not presented as current account activity. The connected Mock ASPSP data is synthetic. See [`docs/enable-banking-sandbox.md`](docs/enable-banking-sandbox.md) for local sandbox setup.

The app and backend support Bank Austria account authorization through a separate, restricted Enable Banking production application. The application must link the owner's account in the Control Panel, and then authorize the account in Saldo. The sandbox and production sessions are stored separately. The displayed transaction history is limited to the dates returned by the bank connection; older history can be supplemented by the imported statement. See [`docs/enable-banking-production.md`](docs/enable-banking-production.md) for the local and cloud flows.

Separate Cloudflare Workers now serve the sandbox and Bank Austria production environments. The Android app can pair with either using a short-lived code and load data directly over HTTPS without a running PC. The production Worker has a connected Bank Austria account and an encrypted copy of the older imported statement. Its first live fetch returned balance entries and 134 recent transactions. Installation and pairing on the physical Android phone remain to be completed. Without cloud pairing, the app continues to use the local backend. See [`docs/cloud-backend.md`](docs/cloud-backend.md).

Successful bank responses are encrypted in a local backend cache to reduce repeat requests. A temporary provider rate limit shows the last saved bank response if one exists, or a clear retry message; it never substitutes sample transactions for production bank data.

Older Bank Austria transactions can be imported once from a 24You CSV export with the local backend command described in [`docs/statement-import.md`](docs/statement-import.md). The import is encrypted outside the repository. When the bank temporarily rejects data requests, Saldo still shows imported transactions and clearly marks the current balance as unavailable. When bank data becomes available, transactions older than the oldest API transaction are added to the live view without duplicating the overlap. The local backend fetches the newest bank transaction page and follows additional pages only until they overlap the imported statement. The production Cloudflare Worker refreshes bank data at 08:00, 17:00, and 21:00 Vienna time; opening the Android app reads its encrypted cache without contacting the bank. A six-hour pause blocks scheduled requests after a provider rate limit.

The Production Worker also records the outcome of each scheduled refresh without account data in its diagnostics. Saldo warns when the latest scheduled refresh failed and checks the cached status again when the app returns to the foreground.

## Run locally

- Start the backend project in `backend/Finance.Api/` with its `https` launch profile (ports `7129` for the desktop callback and `5006` for the emulator API). A locally trusted development certificate for `localhost` is required.
- Start the Android emulator, then run the `android/` app from Android Studio. The emulator reaches the computer's local API through `10.0.2.2`.
