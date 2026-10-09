# SchulPlan

> Inoffizielle Open-Source-App – kein Angebot der Schule oder der Untis GmbH.

Dein Stundenplan aus **WebUntis** als Android-App im Material-You-Design – mit Vertretungen,
Ausfällen, Hausaufgaben und Prüfungen. Voreingestellt ist die **Carlo-Schmid-Schule Karlsruhe**,
über „Andere Schule?“ funktioniert die App aber mit jeder Schule, die WebUntis nutzt.

## Installieren
1. Neueste APK unter **[Releases](../../releases/latest)** herunterladen (am besten direkt am Handy).
2. Datei öffnen und installieren – beim ersten Mal fragt Android, ob der Browser Apps
   installieren darf („Installation aus unbekannten Quellen“).
3. Mit deinem **WebUntis-Benutzernamen und Passwort** anmelden.
4. Updates genauso: neue APK laden und drüber installieren, du bleibst angemeldet.

Voraussetzung: Android 8.0 oder neuer.

## Funktionen
- **Tages- und Wochenansicht** – oben rechts umschalten, zwischen Tagen/Wochen wischen
- **Doppelstunden** werden automatisch zu einem Block zusammengefasst
- **Vertretungen & Ausfälle**: Entfall durchgestrichen, Vertretung/Raumwechsel farbig markiert
  („Vertretung für …“, „Raum 110 statt 105“) inkl. Infotexten aus WebUntis
- **„Als Nächstes“** mit Countdown; laufende Stunden mit Fortschrittsbalken
- **Hausaufgaben & Prüfungen** (Symbol oben rechts): offene Hausaufgaben zum Abhaken,
  Klassenarbeiten mit Countdown – fällige Aufgaben erscheinen auch direkt am jeweiligen Tag
- **Änderungs-Hinweise**: Benachrichtigung bei Entfall, Vertretung oder Raumänderung
- **Erinnerungen** 10, 15 oder 30 Minuten vor der Stunde
- **Tagesvorschau** am Vorabend oder morgens
- **Widget** für den Homescreen mit deinen nächsten Stunden
- **Fächer ausblenden**, die dich nicht betreffen, oder einzelne Stunden ausblenden
- Funktioniert auch **offline** mit dem zuletzt geladenen Stand; nach unten ziehen zum Aktualisieren

## Datenschutz
- Benutzername und Passwort werden nur auf deinem Handy gespeichert; das Passwort ist mit
  einem Schlüssel aus dem Android-Keystore verschlüsselt und wird nicht ins Backup übernommen.
- Die App spricht ausschließlich mit dem WebUntis-Server deiner Schule – keine Werbung,
  kein Tracking, keine eigenen Server.
- „Abmelden“ in den Einstellungen löscht Zugangsdaten und gespeicherten Stundenplan.

## Hinweise
- Was die App zeigen kann, hängt von den Rechten ab, die deine Schule in WebUntis vergibt
  (z. B. ob Lehrernamen, Hausaufgaben oder Prüfungen für Schüler sichtbar sind).
- Schulen mit Anmeldung nur über Microsoft/Office 365 (SSO) werden aktuell nicht unterstützt.
- Hausaufgaben abhaken speichert den Haken nur in der App, nicht in WebUntis.

## Lizenz
MIT – siehe [LICENSE](LICENSE). Basiert auf dem [HKA Stundenplan](https://github.com/Flexy06/HKA-Stundenplan-App).
