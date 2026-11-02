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
