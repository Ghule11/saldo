# Saldo unterwegs: erster Cloud-Schritt

Die Android-App spricht zurzeit mit dem lokalen .NET-Backend auf dem PC. Für ein Telefon unterwegs braucht Saldo eine erreichbare HTTPS-Adresse. Der private Enable-Banking-Schlüssel bleibt dabei auf dem Backend und darf nicht in die Android-App oder nach GitHub gelangen.

## Sandbox-Prototyp

`cloudflare/` enthält einen bewusst kleinen Cloudflare Worker. `GET /api/health` zeigt nur, ob er erreichbar ist. `GET /api/probe` ruft mit der **Sandbox-Anwendung** `GET /application` bei Enable Banking auf und meldet lediglich Umgebung und Aktivierungsstatus. Der Aufruf benötigt einen privaten `PROBE_TOKEN` mit mindestens 32 Zeichen. Er liest weder Konten noch Umsätze und startet keine Bankfreigabe.

Für die spätere Bereitstellung werden im Cloudflare-Dashboard drei **Secrets** gesetzt: `EB_APPLICATION_ID`, `EB_PRIVATE_KEY` und `PROBE_TOKEN`. Dafür ausschließlich die Sandbox-ID und den zugehörigen Sandbox-Schlüssel verwenden. Der Schlüssel muss als PKCS#8-PEM mit `BEGIN PRIVATE KEY` vorliegen. Secrets nie in `wrangler.toml`, `.dev.vars`, GitHub-Issues oder Chatnachrichten kopieren. Die Cloudflare-Konfiguration erwartet ausdrücklich `SANDBOX` und zeigt bei einer anderen Umgebung `wrong_environment`.

Der Worker wird nach `npm install` im Ordner `cloudflare/` mit `npm run deploy` bereitgestellt. Cloudflare weist dabei eine HTTPS-Adresse unter `workers.dev` zu. Anschließend kann der geschützte Probe-Endpunkt aufgerufen und seine CPU-Zeit in Cloudflare Analytics geprüft werden. Der kostenlose Workers-Tarif erlaubt derzeit 10 ms CPU-Zeit pro Anfrage. Erst wenn Signatur und Providerabruf dort funktionieren, wird der vollständige Bankablauf portiert.

## Danach

1. Die echten Daten-Endpunkte mit einer einmaligen Kopplung des eigenen Android-Geräts absichern.
2. Sitzungszustand und Cache dauerhaft und verschlüsselt speichern; ein Worker darf sich nicht auf lokalen Arbeitsspeicher verlassen.
3. Den Bankfreigabe-Link auf dem Android-Gerät öffnen und den Rücksprung unter einer registrierten HTTPS-Adresse abwickeln.
4. Bankdaten abrufen und den letzten Stand auf dem Gerät speichern. Die bisherige Kategorisierung und Auswertung sollen weiter funktionieren.
5. Erst nach einem erfolgreichen Sandbox-Durchlauf die Production-Anwendung auf den neuen Rücksprung umstellen und Bank Austria einmal neu autorisieren. Den alten PC-Aufbau bis dahin weiter nutzen.

Die Probe ist noch kein Cloud-Backend für die Android-App. Die App bleibt vorerst auf das lokale Backend eingestellt. Das GitHub-Repository enthält keine Zugangsdaten.
