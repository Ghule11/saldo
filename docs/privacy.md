# Datenschutz bei Saldo

Saldo ist eine nicht kommerzielle Android-App für die persönliche Nutzung durch ihren Betreiber. Es gibt keine öffentlichen Nutzerkonten und keine Werbung oder Analysedienste.

Nach einer ausdrücklichen Freigabe bei der Bank ruft das lokal auf dem eigenen Computer laufende Saldo-Backend über Enable Banking Kontostand und Umsätze ab. Die Android-App erhält diese Daten vom lokalen Backend und zeigt sie an. Saldo speichert den Enable-Banking-Sitzungsverweis verschlüsselt auf dem Computer. Die privaten API-Schlüssel liegen außerhalb des Git-Repositories. Umsätze werden derzeit nicht dauerhaft in einer Saldo-Datenbank gespeichert. Manuelle Kategorieänderungen speichert Android lokal auf dem Gerät; ihre Schlüssel sind aus Transaktionsmerkmalen abgeleitete Prüfsummen.

Bank Austria und Enable Banking verarbeiten Daten im Rahmen der vom Nutzer bestätigten Bankfreigabe nach ihren eigenen Datenschutzinformationen. Saldo initiiert keine Zahlungen und gibt keine Kontodaten an Werbe- oder Analysedienste weiter.

Zum Entfernen der lokalen Daten kann die Android-App deinstalliert und der Saldo-Ordner unter `%LOCALAPPDATA%` auf dem eigenen Computer gelöscht werden. Die Bankfreigabe kann zusätzlich bei Bank Austria oder Enable Banking widerrufen werden. Diese Seite beschreibt den aktuellen privaten Entwicklungsstand; vor einer öffentlichen Bereitstellung müsste sie geprüft und erweitert werden.
