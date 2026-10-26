# Analyse in Saldo

Die Analyse wird auf dem Android-Gerät aus den bereits geladenen und kategorisierten Umsätzen berechnet. Sie löst keinen zusätzlichen Enable-Banking-Abruf aus. Eigene Kategorieänderungen wirken sofort. Einzelne Buchungen lassen sich in der Detailansicht aus der Analyse ausschließen; sie bleiben in der Umsatzliste und im Kontostand erhalten. Klar bezeichnete Eigenüberträge und Bareinzahlungen am Automaten werden automatisch aus den passenden Berechnungen ausgeschlossen. Bei nicht eindeutig beschrifteten Überträgen ist die manuelle Ausnahme nötig.

## Monatsprognose

Die Prognose erscheint nur mit mindestens drei aufeinanderfolgenden vollständigen Vormonaten und ausreichend variablen Buchungen. Für den variablen Tageswert werden bis zu sechs vollständige Vormonate betrachtet. Bestätigte Abos zählen nicht als variable Ausgaben. Die Ausgaben werden zunächst pro Tag und dann in Siebentageblöcken summiert; die obersten Wochenwerte werden beim 95. Perzentil begrenzt. Große bereits gebuchte Ausgaben bleiben vollständig in „Bereits ausgegeben“ enthalten.

Offene Fixkosten sind bestätigte Abos mit einer Buchung in den letzten 62 Tagen, sofern im aktuellen Monat noch keine Belastung für diesen Händler vorliegt. Die Einnahmenbasis ist der höhere Wert aus bereits erhaltenen Einnahmen im aktuellen Monat und der Summe der erkannten regelmäßigen Eingänge. In der Übersicht ausgeblendete regelmäßige Eingänge zählen für diese Summe nicht. Bareinzahlungen und erkannte Eigenüberträge zählen dabei nicht als Einkommen.

Prognose = bisherige Ausgaben + offene Fixkosten + variabler Tageswert × verbleibende Kalendertage. Die Differenz vergleicht diese Prognose mit der Einnahmenbasis. „Knapp“ bedeutet eine positive Differenz unter zehn Prozent der Einnahmenbasis oder unter 20 Euro; ein negativer Wert ist „Über den Einnahmen“. Das tägliche Restbudget teilt die Einnahmenbasis abzüglich bisheriger Ausgaben, offener Fixkosten und optionaler Rücklagen durch die verbleibenden Tage. Am letzten Tag wird kein Tageswert berechnet. Die Einnahmenbasis wird nicht als eigene Zeile in der App angezeigt.

## Monatsvergleich

Die Karte vergleicht die Ausgaben des laufenden Monats bis zum angezeigten Kalendertag mit dem Durchschnitt der drei vorangehenden Monate bis zu demselben Tag. Wenn einer dieser Monate kürzer ist, wird für alle vier Monate der letzte gemeinsame Tag verwendet. Erforderlich sind Buchungen in allen drei Vergleichsmonaten. Aus der Analyse ausgeschlossene Buchungen und erkannte Eigenüberträge zählen nicht mit.

## Rücklagen

Saldo schlägt vierteljährliche, halbjährliche und jährliche Zahlungen bei wiederholtem, ungefähr gleich hohem Händlerbetrag vor. Für viertel- und halbjährliche Muster sind mindestens drei Buchungsmonate nötig; für jährliche mindestens zwei. Kleinbeträge unter 30 Euro pro Jahr werden nicht vorgeschlagen. Die Monatsrücklage entspricht der geschätzten Jahressumme geteilt durch zwölf.

Für die Kategorien „Geschenke“ und „Urlaub“ wird die Summe der letzten zwölf vollständigen Monate durch zwölf geteilt. Diese Kategorien können in der Buchungsdetailansicht vergeben werden. Der Schalter „Im täglichen Restbudget abziehen“ ist zunächst aus. Er löst keine Überweisung aus. Beträge, die im aktuellen Monat bereits für denselben Posten bezahlt wurden, werden beim optionalen Abzug angerechnet. Die Rücklagenansicht ist standardmäßig eingeklappt.

## Saison

Die Saisonansicht bildet für jeden Kalendermonat den Durchschnitt seiner variablen Ausgaben über die verfügbaren Jahre und vergleicht ihn mit dem Durchschnitt aller betrachteten vollständigen Monate. Einnahmen werden nicht verrechnet. Bestätigte Abos, Bargeldbehebungen und erkannte Eigenüberträge zählen hier nicht als variable Ausgaben. Bei weniger als zwölf Monaten erscheinen keine Prozentwerte. Zwischen zwölf und 23 Monaten sind die Werte ausdrücklich vorläufig. „Sparen“, „Neutral“ und „Spielraum“ werden erst bei mindestens 24 Monaten mit jeweils zwei Beobachtungen pro Kalendermonat angezeigt. Die Kategorien, die den Unterschied am stärksten erklären, lassen sich pro Monat aufklappen. Ein hoher Anteil „Sonstiges“ wird als Einschränkung angezeigt. Die Saisonansicht ist standardmäßig eingeklappt.
