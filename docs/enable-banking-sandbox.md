# Enable Banking sandbox

Saldo's backend can start the Enable Banking account information flow for a **SANDBOX** application. It refuses the flow if Enable Banking reports that the configured application is not in the sandbox environment. This is a local emulator setup; the callback URL is not suitable for a public deployment or a physical phone.

## Register a sandbox application

1. Sign in to the Enable Banking Control Panel and register a new application with environment **Sandbox**.
2. Add this exact callback URL to the application's allowed redirect URLs:

   `http://10.0.2.2:5006/api/banking/callback`

3. Download the generated private key. Keep the `.pem` file outside the repository. The application ID is the file name without `.pem`.

## Configure the local backend

Set both values in .NET User Secrets from the repository root. Replace the placeholders with your application ID and the full path to the downloaded key:

```powershell
dotnet user-secrets set "EnableBanking:ApplicationId" "<application-id>" --project backend/Finance.Api/Finance.Api.csproj
dotnet user-secrets set "EnableBanking:PrivateKeyPath" "C:\Users\<your-user>\Downloads\<application-id>.pem" --project backend/Finance.Api/Finance.Api.csproj
```

The redirect URL defaults to the emulator callback above. User Secrets are stored outside Git; key and certificate extensions are also ignored by `.gitignore`. Never commit or send the private key.

Restart the backend using its `http` launch profile, then check:

`GET http://localhost:5006/api/banking/status`

The response must report `isConfigured: true` and `isSandbox: true` before continuing.

## Start the sandbox authorization

1. Get the test institutions available to your registered app:

   `GET http://localhost:5006/api/banking/institutions`

2. Choose an institution returned by that response. Start authorization with its exact name and country:

   `POST http://localhost:5006/api/banking/authorization`

   ```json
   {
     "institutionName": "<name-from-the-list>",
     "country": "<country-from-the-list>"
   }
   ```

3. Open the returned `url` in the Android emulator browser and complete the provider's sandbox flow. The provider redirects to Saldo's local callback; return to the app afterward.

The backend validates the one-use `state`, exchanges the authorization code for a sandbox session, and keeps the session reference in memory. Restarting the backend clears it. The dashboard continues to show sample data until the separate account-data step fetches sandbox balances and transactions.

## Backend routes

- `GET /api/banking/status` — local configuration, verified app environment, and sandbox connection status
- `GET /api/banking/institutions?country=AT` — sandbox institutions; omit `country` to list all returned institutions
- `POST /api/banking/authorization` — start authorization for one listed institution
- `GET /api/banking/callback` — provider redirect; accepts a one-use state for ten minutes
