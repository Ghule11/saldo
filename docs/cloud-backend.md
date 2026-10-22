# Saldo unterwegs: erster Cloud-Schritt

Die Android-App spricht zurzeit mit dem lokalen .NET-Backend auf dem PC. Für ein Telefon unterwegs braucht Saldo eine erreichbare HTTPS-Adresse. Der private Enable-Banking-Schlüssel bleibt dabei auf dem Backend und darf nicht in die Android-App oder nach GitHub gelangen.

## Sandbox-Prototyp

`cloudflare/` enthält einen Cloudflare Worker ausschließlich für die Enable-Banking-Sandbox. `GET /api/health` zeigt, ob er erreichbar ist. `GET /api/probe` prüft die Sandbox-Anwendung; `GET /api/storage-probe` prüft die AES-GCM-verschlüsselte D1-Speicherung. Die geschützten Bank-Endpunkte sind `GET /api/banking/status`, `GET /api/banking/institutions`, `POST /api/banking/authorization` und `GET /api/banking/data`. Sie benötigen einen privaten `PROBE_TOKEN` mit mindestens 32 Zeichen. Nur der Rücksprung `GET /api/banking/callback` ist öffentlich; ein zufälliger, zehn Minuten gültiger Statuswert schützt ihn und wird nur einmal akzeptiert. Der Sitzungszustand und der Daten-Cache liegen verschlüsselt in D1.

In Cloudflare sind vier **Secrets** gesetzt: `EB_APPLICATION_ID`, `EB_PRIVATE_KEY`, `PROBE_TOKEN` und `DATA_ENCRYPTION_KEY`. Die beiden ersten gehören ausschließlich zur Sandbox-Anwendung. Der private Schlüssel liegt als PKCS#8-PEM mit `BEGIN PRIVATE KEY` vor. Secrets nie in `wrangler.toml`, `.dev.vars`, GitHub-Issues oder Chatnachrichten kopieren. Die Cloudflare-Konfiguration erwartet ausdrücklich `SANDBOX` und zeigt bei einer anderen Umgebung `wrong_environment`. Die lokalen Kopien des Probe-Tokens und Verschlüsselungsschlüssels liegen mit Windows-DPAPI geschützt außerhalb des Repositories unter `%LOCALAPPDATA%\Saldo`.

Der Worker ist unter `https://saldo-bank-probe.saldo-cloud-probe.workers.dev` bereitgestellt. Am 22.10.2026 antworteten Health-Endpunkt, Enable-Banking-Probe und verschlüsselte D1-Probe mit HTTP 200; die Providerprobe ohne Token antwortete mit HTTP 401. Die Sandbox-Anwendung hat zusätzlich die HTTPS-Rücksprungadresse des Workers registriert. Die Freigabe des synthetischen Kontos „Ida Jensen“ gelang über den Worker. Anschließend meldete der Status genau ein verbundenes Testkonto. Der Datenabruf lieferte 611 Testbuchungen; der nächste Abruf kam aus dem verschlüsselten 12-Stunden-Cache. Bei einem Providerlimit von HTTP 429 darf ein vorhandener Cache bis 24 Stunden verwendet werden. Die CPU-Zeit des vollständigen Ablaufs im kostenlosen Tarif ist weiter zu beobachten.

Für Änderungen im Ordner `cloudflare/` verwendet man `npm install` und `npm run deploy`. Die D1-Migrationen liegen in `cloudflare/migrations/` und werden mit `wrangler d1 migrations apply DB --remote` auf die Cloud-Datenbank angewendet.

## Danach

1. Das eigene Android-Gerät einmalig und widerrufbar koppeln, ohne den privaten Probe-Token in die App zu übernehmen.
2. Die Android-App auf die HTTPS-Adresse umstellen und den letzten Stand auf dem Gerät speichern. Die bisherige Kategorisierung und Auswertung sollen weiter funktionieren.
3. Die Production-Anwendung und Bank Austria erst nach dem erfolgreichen Gerätetest auf die neue Rücksprungadresse umstellen. Dafür sind eigene Cloud-Secrets und eine erneute Bankfreigabe erforderlich.

Die Cloud verarbeitet derzeit ausschließlich synthetische Sandbox-Daten. Die Android-App bleibt vorerst auf das lokale Backend eingestellt; das echte Bank-Austria-Konto verwendet weiter den PC. Das GitHub-Repository enthält keine Zugangsdaten.
