# Saldo ohne laufenden PC

Die Android-App kann mit einem einmaligen Code direkt an einen Cloudflare Worker gekoppelt werden. Der private Enable-Banking-Schlüssel liegt ausschließlich als Worker-Secret vor. Das Gerätetoken wird auf Android mit dem Keystore verschlüsselt und in D1 nur als SHA-256-Hash gespeichert. Bank-Sitzung, Antwort-Cache und importierte ältere Umsätze liegen mit AES-GCM verschlüsselt in D1. Die Production-Datenbank wurde mit EU-Jurisdiktion angelegt. Das Repository enthält keine Schlüssel oder Bankdaten.

## Getrennte Umgebungen

| Umgebung | Worker | Konfiguration | Datenbank |
| --- | --- | --- | --- |
| Testdaten | `https://saldo-bank-probe.saldo-cloud-probe.workers.dev` | `cloudflare/wrangler.toml` | eigene Sandbox-D1 |
| Bank Austria | `https://saldo-bank.saldo-cloud-probe.workers.dev` | `cloudflare/wrangler.production.toml` | `saldo-production-state` |

Jede Instanz hat eine eigene Enable-Banking-Anwendung, eigene Secrets und eine eigene Rücksprungadresse. Der Worker prüft die erwartete Enable-Banking-Umgebung. `GET /api/health` ist öffentlich; die anderen Lese- und Verwaltungsendpunkte benötigen ein Admin- oder gekoppeltes Gerätetoken. Nur der Bank-Rücksprung und das Einlösen eines einmaligen Kopplungscodes sind öffentlich. Ein Kopplungscode läuft nach zehn Minuten ab und kann nur einmal eingelöst werden.

Vier Secrets sind je Instanz gesetzt: `EB_APPLICATION_ID`, `EB_PRIVATE_KEY`, `PROBE_TOKEN` und `DATA_ENCRYPTION_KEY`. Die lokalen Sicherungskopien der Admin- und Verschlüsselungsschlüssel liegen DPAPI-geschützt außerhalb des Repositories unter `%LOCALAPPDATA%\Saldo`. Secrets niemals in GitHub, Chatnachrichten oder Konfigurationsdateien eintragen.

Die Production-Anwendung ist aktiv und die HTTPS-Rücksprungadresse des Workers ist registriert. Eine ältere Bank-Austria-Umsatzliste mit 1118 Buchungen von Mai 2023 bis Oktober 2026 wurde in die Production-Datenbank übernommen. Am 22.10.2026 wurde das Bank-Austria-Konto über den Cloud-Worker freigegeben: Der Status meldete genau ein Konto, und ein neuer Abruf lieferte Kontostandsdaten und 134 aktuelle Buchungen. Damit ist der Cloud-Abruf unabhängig vom eingeschalteten PC. Die Anzeige auf dem echten Telefon muss noch nach Installation und Production-Kopplung geprüft werden. Der Sandbox-Worker hat ein verbundenes synthetisches Konto und 611 Testbuchungen; die Anzeige im Android-Emulator wurde geprüft.

## Bankabrufe

Erfolgreiche Bankantworten werden zwölf Stunden verschlüsselt zwischengespeichert. In Production lädt ein normaler Abruf die neueste Umsatzseite; ältere Buchungen kommen aus dem importierten Auszug. Bei einem Providerlimit (HTTP 429) pausiert der Worker sechs Stunden. Ein vorhandener Cache darf dann bis zu 24 Stunden alt sein. Beim aktiven Production-Abruf werden die vom Gerät gesendete App-Kennung und die Cloudflare-Client-IP als PSU-Header an Enable Banking weitergegeben. Eine Bankfreigabe muss bei Ablauf erneuert werden.

Die Android-App bietet beim Koppeln „Bank Austria“ und „Testdaten“ an. Nach Production-Kopplung öffnet „Bank Austria verbinden“ die Freigabe-URL im Browser des Geräts. Danach lädt „Daten aktualisieren“ die Bankdaten vom Cloud-Worker. „Cloud trennen“ widerruft nur das Gerätetoken; die verschlüsselten Cloud-Daten und die Bankfreigabe werden dadurch nicht automatisch gelöscht. Siehe auch [Datenschutz](privacy.md).

## Betrieb

Im Ordner `cloudflare/`:

```powershell
npm install
npm run deploy
npm run deploy:production
```

Die D1-Migrationen liegen in `cloudflare/migrations/` und müssen für beide Datenbanken mit der jeweils richtigen Konfiguration angewendet werden. Für `GET /api/probe`, `GET /api/storage-probe` und Verwaltungsendpunkte ist das jeweilige Admin-Token erforderlich. `POST /api/history/import` ist nur in Production und nur mit Admin-Token verfügbar. Der Worker speichert dort ausschließlich die bereinigten Felder der importierten Umsatzliste.
