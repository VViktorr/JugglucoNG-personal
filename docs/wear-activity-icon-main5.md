# Wear OS: wybór ikony aktywności na tarczy

Na zegarku, w Ustawieniach, dodano sekcję „Ikona aktywności na tarczy”:
standardowa ikona (domyślna), wartość glukozy, strzałka trendu.
Wybór jest zapisywany lokalnie na zegarku i odświeża istniejącą aktywność.
Nie zmienia ikony w menu aplikacji ani ustawień telefonu.

Wartość korzysta z CurrentDisplaySource, w tym bieżących jednostek i ustawień
wyświetlania. Liczba i strzałka są białymi bitmapami na przezroczystym tle,
generowanymi przez istniejący renderer komplikacji. Rozmiar źródłowy 96 px
jest skalowany przez Wear OS; fizyczny rozmiar i czytelność zależą od zegarka.

OngoingActivity.update zmienia tylko tekst Status, dlatego aktualizacja ikony
ponownie dołącza dane OngoingActivity do istniejącego powiadomienia o tym samym
ID. Zachowane są kanał, powiązanie z usługą i otwieranie aplikacji po dotknięciu.
Aktualizacja nie tworzy aktywności, jeśli powiadomienia już nie ma.

Przy braku świeżego odczytu (wspólny limit Notify.glucosetimeout) tryby wartości
i trendu pokazują „—”. Brak znanego trendu również daje „—”. Jednorazowe zadanie
Handlera planuje odświeżenie na termin wygaśnięcia; nowy odczyt zastępuje to
zadanie. Nie dodano odpytywania ani wake locka. Podczas głębokiego uśpienia
wykonanie zadania może być opóźnione do wznowienia pracy systemu.

Nie dodano MetricStyle ani osobnej funkcji Live Update. Zmiana dotyczy wyłącznie
istniejącej aktywności Wear OS. Zgodnie z poleceniem użytkownika nie wykonywano
kompilacji ani testów; przejrzano powiązania źródeł, zasobów i aktualizacji.
Wyświetlanie bitmapy przez konkretny zegarek pozostaje do sprawdzenia na urządzeniu.

## Poprawka zgodności po sprawdzeniu na zegarku (2026-11-02)

Użytkownik potwierdził działanie standardowej ikony, ale brak wartości i strzałki.
Nie ma jeszcze logu z System UI potwierdzającego przyczynę. Poprzedni kod ustawiał
wyłącznie staticIcon, pozostawiając animatedIcon oraz małą ikonę powiadomienia
bez wybranego obrazu. Dodano ten sam obraz do obu pól OngoingActivity i do małej
ikony powiadomienia, żeby ścieżka zastępcza systemu również pokazywała wybór.
Pole animatedIcon może zawierać nieruchomą ikonę; nie dodano animacji.

Po odtworzeniu procesu zmiana ustawienia potrafi odszukać istniejącą OngoingActivity,
gdy nie ma jeszcze zapamiętanego ID. Nie tworzy zatrzymanego powiadomienia.
Log wyboru zawiera tryb i informację, czy znaleziono aktywność, bez danych sensora.

Bez kompilacji i testów. Na zegarku należy sprawdzić oba tryby, powrót do standardowej
ikony, nowy odczyt, wygaśnięcie oraz tryb aktywny i ambient. Poprawka zgodności
nie jest potwierdzeniem obsługi bitmap przez każdą tarczę/OEM.

## Mała ikona powiadomienia z odczytem (2026-11-02)

Po doprecyzowaniu zgłoszenia sprawdzono oddzielne ID powiadomień: usługa
foreground/OngoingActivity używa 81431, a odczyty i alarmy 81432.
Dotychczasowy wybór ikony obejmował tylko 81431. Dodano applySmallIcon
do mostu Wear oraz obu ścieżek publikujących powiadomienie glukozy 81432:
fornotify i bezpośredniej publikacji bogatego alarmu.

Zmiana wyboru odświeża także istniejące 81432 z onlyAlertOnce, bez tworzenia
nowego powiadomienia i bez ponownego wywoływania logiki alarmów.
Termin wygaśnięcia odczytu odświeża obie ikony. Powrót do trybu standardowego
przywraca ikonę novalue. Kanały, treść, akcje i ID pozostają zachowane przez
Notification.Builder.recoverBuilder. Telefon jest wyłączony z tej ścieżki
warunkiem isWearable. Nie dodano drugiej OngoingActivity.

Przejrzano wywołania i zachowanie dla zmiany trybu, braku odczytu oraz
nieobecnego powiadomienia. Bez kompilacji i testów zgodnie z poleceniem
użytkownika. Pominięcie ID 81432 jest potwierdzone w kodzie; nie potwierdza
jeszcze obsługi dynamicznej bitmapy przez System UI konkretnego zegarka.

## Resource-backed Wear icons after repeated device failure (2026-11-03)

The user still sees the standard icon but neither generated numeric nor trend
bitmap icons. This isolates an important difference (TYPE_RESOURCE vs TYPE_BITMAP)
but is not a proven System UI root cause without device logs.

Replace all three icon fields (notification, static and animated OngoingActivity)
with resource-backed Icons in every mode. Numeric artwork reuses 719 existing
mobileNosi drawable-xxxhdpi/glucose_*.png blobs byte for byte under unique Wear-only
drawable-nodpi names (2,170,881 bytes before APK compression). A representative
215 asset was visually inspected: white digits on transparent background.
Explicit R references retain the library under resource shrinking.

Value selection matches the formatted display string, normalising decimal comma
and point and a trailing decimal zero. It never clamps a reading to another
number. The existing artwork covers every integer 40..500 and every tenth
2.2..27.8; unsupported text or missing/stale data uses a packaged dash.
Trends use seven white vector resources selected with ExchangeTrend.fromRate,
matching the shared textual trend categories rather than continuous rotation.
Unknown rate also uses the dash. Unit/display/calibration processing is unchanged.

Both the foreground 81431 and reading/alarm 81432 paths already call the icon
provider and now receive resource Icons. Added attach diagnostics log mode,
outgoing small-icon type and reading presence only; no sensor identifiers,
glucose values or credentials. Retained selection persistence, expiry handling,
notification IDs, channel/actions and no-resurrection behaviour.

Static review checked all resource names against the branch tree, number coverage,
source-set availability and both publication paths. No compilation or tests run,
per user instruction. This is a compatibility change requiring device validation,
not a claim that every Wear OEM can display arbitrary ongoing-activity icons.
