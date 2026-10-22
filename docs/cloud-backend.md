# Saldo unterwegs: erster Cloud-Schritt

Die Android-App spricht zurzeit mit dem lokalen .NET-Backend auf dem PC. Für ein Telefon unterwegs braucht Saldo eine erreichbare HTTPS-Adresse. Der private Enable-Banking-Schlüssel bleibt dabei auf dem Backend und darf nicht in die Android-App oder nach GitHub gelangen.

## Sandbox-Prototyp

`cloudflare/` enthält einen bewusst kleinen Cloudflare Worker. `GET /api/health` zeigt nur, ob er erreichbar ist. `GET /api/probe` ruft mit der **Sandbox-Anwendung** `GET /application` bei Enable Banking auf und meldet lediglich Umgebung und Aktivierungsstatus. `GET /api/storage-probe` schreibt einen zufälligen Marker AES-GCM-verschlüsselt in D1, liest ihn zurück und löscht ihn wieder. Beide Proben benötigen einen privaten `PROBE_TOKEN` mit mindestens 32 Zeichen. Sie lesen weder Konten noch Umsätze und starten keine Bankfreigabe.

In Cloudflare sind vier **Secrets** gesetzt: `EB_APPLICATION_ID`, `EB_PRIVATE_KEY`, `PROBE_TOKEN` und `DATA_ENCRYPTION_KEY`. Die beiden ersten gehören ausschließlich zur Sandbox-Anwendung. Der private Schlüssel liegt als PKCS#8-PEM mit `BEGIN PRIVATE KEY` vor. Secrets nie in `wrangler.toml`, `.dev.vars`, GitHub-Issues oder Chatnachrichten kopieren. Die Cloudflare-Konfiguration erwartet ausdrücklich `SANDBOX` und zeigt bei einer anderen Umgebung `wrong_environment`. Die lokalen Kopien des Probe-Tokens und Verschlüsselungsschlüssels liegen mit Windows-DPAPI geschützt außerhalb des Repositories unter `%LOCALAPPDATA%\Saldo`.

Der Worker ist unter `https://saldo-bank-probe.saldo-cloud-probe.workers.dev` bereitgestellt. Am 22.10.2026 antworteten der öffentliche Health-Endpunkt mit HTTP 200, die Providerprobe ohne Token mit HTTP 401, die geschützte Enable-Banking-Probe mit `SANDBOX`, `active: true` und HTTP 200 und die verschlüsselte D1-Probe ebenfalls mit HTTP 200. Damit sind Signatur, Providerabruf und verschlüsselte Speicherung grundsätzlich möglich. Die CPU-Zeit des vollständigen Bankablaufs ist noch nicht geprüft. Der kostenlose Workers-Tarif erlaubt derzeit 10 ms CPU-Zeit pro Anfrage.

Für Änderungen im Ordner `cloudflare/` verwendet man `npm install` und `npm run deploy`. Die D1-Migrationen liegen in `cloudflare/migrations/` und werden mit `wrangler d1 migrations apply DB --remote` auf die Cloud-Datenbank angewendet.

## Danach

1. Die echten Daten-Endpunkte mit einer einmaligen Kopplung des eigenen Android-Geräts absichern.
2. Sitzungszustand und Cache dauerhaft und verschlüsselt speichern; ein Worker darf sich nicht auf lokalen Arbeitsspeicher verlassen.
3. Den Bankfreigabe-Link auf dem Android-Gerät öffnen und den Rücksprung unter einer registrierten HTTPS-Adresse abwickeln.
4. Bankdaten abrufen und den letzten Stand auf dem Gerät speichern. Die bisherige Kategorisierung und Auswertung sollen weiter funktionieren.
5. Erst nach einem erfolgreichen Sandbox-Durchlauf die Production-Anwendung auf den neuen Rücksprung umstellen und Bank Austria einmal neu autorisieren. Den alten PC-Aufbau bis dahin weiter nutzen.

Die Probe ist noch kein Cloud-Backend für die Android-App. Die App bleibt vorerst auf das lokale Backend eingestellt. Das GitHub-Repository enthält keine Zugangsdaten.
