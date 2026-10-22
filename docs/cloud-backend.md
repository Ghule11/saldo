# Saldo unterwegs: erster Cloud-Schritt

Die Android-App spricht zurzeit mit dem lokalen .NET-Backend auf dem PC. Für ein Telefon unterwegs braucht Saldo eine erreichbare HTTPS-Adresse. Der private Enable-Banking-Schlüssel bleibt dabei auf dem Backend und darf nicht in die Android-App oder nach GitHub gelangen.

## Sandbox-Prototyp

`cloudflare/` enthält einen Cloudflare Worker ausschließlich für die Enable-Banking-Sandbox. `GET /api/health` zeigt, ob er erreichbar ist. `GET /api/probe` prüft die Sandbox-Anwendung; `GET /api/storage-probe` prüft die AES-GCM-verschlüsselte D1-Speicherung. Die Bank-Endpunkte sind `GET /api/banking/status`, `GET /api/banking/institutions`, `POST /api/banking/authorization` und `GET /api/banking/data`. Der private `PROBE_TOKEN` mit mindestens 32 Zeichen erlaubt administrative Aufrufe. Das Android-Gerät darf nach einmaliger Kopplung nur Status und Bankdaten lesen sowie seine Kopplung widerrufen. Nur der Rücksprung `GET /api/banking/callback` und die Einlösung eines einmaligen Kopplungscodes sind öffentlich; zufällige, zehn Minuten gültige Werte schützen beide Vorgänge und werden nur einmal akzeptiert. Der Sitzungszustand und der Daten-Cache liegen verschlüsselt in D1. Gerätetokens liegen serverseitig nur als SHA-256-Hash in D1 und auf Android mit dem Android Keystore verschlüsselt.

In Cloudflare sind vier **Secrets** gesetzt: `EB_APPLICATION_ID`, `EB_PRIVATE_KEY`, `PROBE_TOKEN` und `DATA_ENCRYPTION_KEY`. Die beiden ersten gehören ausschließlich zur Sandbox-Anwendung. Der private Schlüssel liegt als PKCS#8-PEM mit `BEGIN PRIVATE KEY` vor. Secrets nie in `wrangler.toml`, `.dev.vars`, GitHub-Issues oder Chatnachrichten kopieren. Die Cloudflare-Konfiguration erwartet ausdrücklich `SANDBOX` und zeigt bei einer anderen Umgebung `wrong_environment`. Die lokalen Kopien des Probe-Tokens und Verschlüsselungsschlüssels liegen mit Windows-DPAPI geschützt außerhalb des Repositories unter `%LOCALAPPDATA%\Saldo`.

Der Worker ist unter `https://saldo-bank-probe.saldo-cloud-probe.workers.dev` bereitgestellt. Am 22.10.2026 antworteten Health-Endpunkt, Enable-Banking-Probe und verschlüsselte D1-Probe mit HTTP 200; die Providerprobe ohne Token antwortete mit HTTP 401. Die Sandbox-Anwendung hat zusätzlich die HTTPS-Rücksprungadresse des Workers registriert. Die Freigabe des synthetischen Kontos „Ida Jensen“ gelang über den Worker. Anschließend meldete der Status genau ein verbundenes Testkonto. Der Datenabruf lieferte 611 Testbuchungen; der nächste Abruf kam aus dem verschlüsselten 12-Stunden-Cache. Bei einem Providerlimit von HTTP 429 darf ein vorhandener Cache bis 24 Stunden verwendet werden. Die CPU-Zeit des vollständigen Ablaufs im kostenlosen Tarif ist weiter zu beobachten.

Die Kopplung verwendet `POST /api/pairing/code` (nur Admin-Token), `POST /api/pairing/claim` (einmaliger 128-Bit-Code), `POST /api/pairing/self-revoke` (Gerätetoken) und `POST /api/pairing/revoke` (Admin-Token). Ein Gerätetoken konnte Status und die 611 zwischengespeicherten Testbuchungen lesen. Nach dem eigenen Widerruf antwortete der Status mit HTTP 401. Ein Kopplungscode wird erst erzeugt, wenn das Android-Gerät bereit ist, weil er nach zehn Minuten abläuft.

Für Änderungen im Ordner `cloudflare/` verwendet man `npm install` und `npm run deploy`. Die D1-Migrationen liegen in `cloudflare/migrations/` und werden mit `wrangler d1 migrations apply DB --remote` auf die Cloud-Datenbank angewendet.

## Danach

1. Den Emulator mit einem frischen Einmalcode über „Cloud-Testverbindung koppeln“ verbinden und die Anzeige dort prüfen. Die App schaltet nach der Kopplung von der lokalen auf die HTTPS-Adresse um; ein lokaler Zwischenspeicher für den letzten Bankstand fehlt noch.
2. Die Production-Anwendung und Bank Austria erst nach dem erfolgreichen Gerätetest auf eine getrennte Cloud-Instanz und Rücksprungadresse umstellen. Dafür sind eigene Cloud-Secrets und eine erneute Bankfreigabe erforderlich.

Die Cloud verarbeitet derzeit ausschließlich synthetische Sandbox-Daten. Die Android-App verwendet zunächst weiter das lokale Backend und wechselt nur nach der Kopplung zur Sandbox-Cloud. Das echte Bank-Austria-Konto verwendet weiter den PC. Das GitHub-Repository enthält keine Zugangsdaten.
