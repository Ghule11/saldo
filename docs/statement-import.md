# Bank-Austria-Umsatzliste lokal übernehmen

Saldo kann eine im 24You Internetbanking exportierte CSV-Umsatzliste einmalig in den lokalen Backend-Speicher übernehmen. Die CSV wird weder in die Android-App eingebaut noch in GitHub gespeichert. Der Backend-Speicher unter `%LOCALAPPDATA%\Saldo` ist auf Windows mit ASP.NET Data Protection und DPAPI verschlüsselt.

1. Im 24You Internetbanking unter **Girokonten bzw. Sparprodukte → Umsatzsuche** den gewünschten Zeitraum wählen und am Ende der Liste **Export** öffnen.
2. Die CSV außerhalb des Repositories speichern.
3. Das Backend kurz beenden und im Repository ausführen:

   ```powershell
   dotnet run --project backend/Finance.Api/Finance.Api.csproj -- --import-csv "C:\Pfad\zur\Umsatzliste.csv"
   ```

4. Das Backend wieder starten und Saldo auf dem Emulator öffnen.

Der Import ersetzt die bisherige importierte Umsatzliste vollständig. Er übernimmt Buchungsdatum, Betrag, Buchungstext und, falls vorhanden, den Namen der Gegenpartei. Kontonummern und IBAN-Spalten werden nicht importiert. Bei einem Bankabruf ergänzt Saldo nur Buchungen, die älter sind als die älteste über die API erhaltene Buchung. Bei einem vorübergehend gesperrten Bankabruf zeigt Saldo die komplette importierte Historie, aber keinen aktuellen Kontostand.

Die Original-CSV bleibt am gewählten Speicherort, bis sie dort manuell entfernt wird.
