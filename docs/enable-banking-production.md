# Persönliches Bank-Austria-Konto verbinden

Saldo nutzt für echte Kontodaten eine **eigene Enable-Banking-Anwendung in der Production-Umgebung**. Die vorhandene Sandbox-Anwendung und ihr Testkonto bleiben getrennt. Der Zugriff in Saldo ist nur für Kontoinformationen vorgesehen; Saldo initiiert keine Zahlungen.

## 1. Production-Anwendung registrieren

Im [Enable-Banking-Control-Panel](https://enablebanking.com/cp/applications) eine **neue** Anwendung anlegen:

- Environment: **Production**
- Name: **Saldo**
- Service: **Account Information**
- Redirect URL: `https://localhost:7129/api/banking/callback`
- RSA-Schlüssel im Browser generieren und die heruntergeladene `.pem`-Datei außerhalb dieses Repositories aufbewahren.

In Production verlangt das Formular außerdem Beschreibung, Datenschutz-E-Mail sowie URLs für Datenschutz und Nutzungsbedingungen. Entwürfe passend zum aktuellen privaten Projekt liegen unter [`privacy.md`](privacy.md) und [`terms.md`](terms.md). Für die URL-Felder müssen diese Seiten unter einer erreichbaren Webadresse veröffentlicht sein. Nur wahrheitsgemäße, für den vorgesehenen Gebrauch passende Angaben eintragen. Die Production-Anwendung erhält eine **neue ID und einen neuen Schlüssel**; die Sandbox-ID funktioniert hier nicht.

Danach bei dieser neuen Anwendung **„Activate by linking accounts“** wählen, **Bank Austria** und das eigene Konto auswählen und die Verknüpfung bei der Bank bestätigen. Diese Verknüpfung schaltet die Anwendung im eingeschränkten Modus für genau dieses Konto frei. Sie autorisiert noch **nicht** den Datenabruf durch Saldo.

## 2. Lokales Backend umstellen

Die ID der Production-Anwendung und den Pfad zu ihrem neuen privaten Schlüssel ausschließlich in lokalen .NET User Secrets speichern:

```powershell
dotnet user-secrets set "EnableBanking:Production:ApplicationId" "<production-application-id>" --project backend/Finance.Api/Finance.Api.csproj
dotnet user-secrets set "EnableBanking:Production:PrivateKeyPath" "C:\Users\<user>\Downloads\<production-application-id>.pem" --project backend/Finance.Api/Finance.Api.csproj
dotnet user-secrets set "EnableBanking:Mode" "PRODUCTION" --project backend/Finance.Api/Finance.Api.csproj
```

Das Backend mit dem `https`-Profil starten: Es lauscht dabei auf `https://localhost:7129` für den Rücksprung im PC-Browser und auf `http://localhost:5006` für die App im Emulator. Ein auf dem Computer vertrauenswürdiges HTTPS-Entwicklungszertifikat für `localhost` ist nötig. `GET http://localhost:5006/api/banking/status` soll danach `environment: "PRODUCTION"`, `isConfigured: true` und `isActive: true` melden. Die Sandbox-Zugangsdaten und ihre verschlüsselte Sitzung bleiben separat erhalten. Zurück zur Sandbox geht es mit `EnableBanking:Mode` = `SANDBOX` und anschließendem Backend-Neustart.

## 3. In Saldo freigeben

Im Android-Emulator in Saldo **Konto → Bank Austria verbinden** antippen. Saldo öffnet den Enable-Banking-/Bank-Austria-Freigabeprozess im **Browser auf dem Computer**. Die Bestätigung bei Bank Austria kann die MobileBanking-App auf dem eigenen Telefon erfordern. Nach dem Rücksprung zur lokalen Callback-Seite in Saldo **Daten aktualisieren** antippen. Bei leerer Kontoliste prüfen, ob im Control Panel **genau dasselbe Konto** verknüpft wurde.

`10.0.2.2` ist die Adresse des Host-Computers aus Sicht des Android-Emulators und wird nur für die Verbindung der App zum Backend verwendet. Der Production-Callback nutzt stattdessen `https://localhost:7129` im PC-Browser. Der lokale Ablauf funktioniert nur, solange das Backend auf dem Computer läuft. Eine spätere Installation auf einem echten Telefon braucht eine andere Callback-/Backend-Lösung.

Saldo speichert den Sitzungsverweis verschlüsselt unter `%LOCALAPPDATA%\Saldo` und lädt Kontostand und Umsätze über das lokale Backend. Die privaten Schlüssel, Bankzugangsdaten und Kontodaten gehören nicht in GitHub. Wenn eine Bankfreigabe ausläuft, in Saldo erneut verbinden.

Erfolgreich geladene Bankdaten werden dort ebenfalls verschlüsselt zwischengespeichert, damit ein erneutes Öffnen der App nicht sofort wieder die Bank abfragt. Bei einem vorübergehenden Abruflimit zeigt Saldo den letzten gespeicherten Stand mit Zeitangabe. Falls noch kein erfolgreicher Abruf gespeichert wurde, zeigt Saldo die importierte Umsatzliste und den Zeitpunkt des nächsten möglichen Abrufs, aber keinen Kontostand. Während der lokalen Abrufpause werden keine weiteren Kontodaten bei der Bank angefragt. Nach Ablauf kann die App beim Öffnen oder über „Daten prüfen“ wieder abrufen.

Der angezeigte Zeitraum richtet sich nach den Umsätzen, die Bank Austria über Enable Banking für die Sitzung liefert. Ein weiter zurückliegendes Startdatum kann mit `WRONG_TRANSACTIONS_PERIOD` abgelehnt werden. Ältere Buchungen lassen sich aus einer lokal importierten 24You-Umsatzliste ergänzen; siehe [`statement-import.md`](statement-import.md). Ohne aktuellen Bankabruf zeigt Saldo diese Umsätze weiter an, aber keinen erfundenen Kontostand.

Enable Banking unterscheidet Datenabrufe mit und ohne Angaben zur aktiven Nutzersitzung (PSU-Header). Für einen Abruf aus der geöffneten Android-App sendet Saldo `Psu-User-Agent` mit der Kennung der App. Bank Austria meldete bei der Abfrage von `/aspsps` keine erforderlichen PSU-Header; andere Banken können weitere Header verlangen. Saldo nutzt erfolgreiche Abrufe bis zu 12 Stunden aus dem verschlüsselten Zwischenspeicher und fragt für Bank Austria nur die neueste Umsatzseite ab. Nach `ASPSP_RATE_LIMIT_EXCEEDED` pausiert es Bankabrufe lokal sechs Stunden. Diese sechs Stunden sind eine vorsichtige Empfehlung von Enable Banking und keine bestätigte spezifische Bank-Austria-Quote. Siehe [Enable-Banking-FAQ zu Abruflimits](https://enablebanking.com/docs/faq/#why-am-i-getting-429-response-code-are-there-rate-limits-for-the-api).

Quellen: [Enable Banking: eigene Konten verknüpfen](https://enablebanking.com/docs/api/linked-accounts), [Control Panel](https://enablebanking.com/docs/api/control-panel/), [Banken in Österreich](https://enablebanking.com/docs/markets/at).
