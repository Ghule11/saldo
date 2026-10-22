# Android-Version für das eigene Telefon

Die signierte APK liegt nach dem Build in `%USERPROFILE%\Downloads\Saldo-1.0.apk`. Sie kann auf das Android-Telefon kopiert und dort installiert werden. Beim ersten Start „Cloud koppeln“ und „Bank Austria“ wählen; dafür muss unmittelbar vorher ein einmaliger Production-Kopplungscode am Cloud-Backend erzeugt werden. Anschließend kommen Bankdaten über HTTPS direkt vom Worker, auch wenn der PC ausgeschaltet ist.

Der private Signierschlüssel liegt außerhalb des Repositories unter `%LOCALAPPDATA%\Saldo\release-signing\saldo.jks`; das Passwort liegt dort DPAPI-geschützt in `password.dpapi`. **Diesen Schlüssel sichern und bei späteren Updates wiederverwenden.** Eine APK mit einem anderen Schlüssel kann die installierte App nicht aktualisieren. Schlüssel und Passwort gehören nicht nach GitHub.

Für spätere Versionen zuerst `versionCode` und `versionName` in `android/app/build.gradle.kts` erhöhen. Dann unter Windows `android/build-signed-release.ps1` ausführen und als `OutputPath` einen zum Versionsnamen passenden APK-Pfad angeben. Das Skript baut, signiert und prüft die APK mit dem vorhandenen Schlüssel.
