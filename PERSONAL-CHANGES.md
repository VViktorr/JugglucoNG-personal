# JugglucoNG – zmiany w wersji personal

To repozytorium zawiera prywatną wersję JugglucoNG opartą na:

- upstream: `ctqvva/JugglucoNG`
- commit bazowy: `95c56d00aadf63a843feb80bfc898e6def51d538`
- gałąź własna: `personal`
- pierwszy commit zmian: `9822cca1d6924083be4a17b30d07f1055855cbb3`

Celem tej gałęzi jest zachowanie stabilnej, odtwarzalnej wersji z poprawkami Libre 2, LibreView, powiadomień, widżetów i interfejsu.

## 1. Libre 2 – poprawki reconnect BLE

Zmodyfikowano obsługę ponownego łączenia z sensorem Libre 2.

Najważniejsze zmiany:

- po błędzie lub zerwaniu połączenia stary obiekt GATT jest jawnie rozłączany,
- ponowne połączenie nie używa `autoConnect`,
- dodano kontrolę generacji połączeń, żeby opóźniony callback starego GATT nie wpływał na nowe połączenie,
- po rozłączeniu wykonywana jest szybka kolejna próba połączenia,
- dodano awaryjne przejście do kolejnej próby, jeżeli callback rozłączenia nie nadejdzie w oczekiwanym czasie.

Zmiana dotyczy głównie:
`Common/src/main/java/tk/glucodata/Libre2GattCallback.java`.

## 2. LibreView – region, jednostka i modelName

Dodano inicjalizację kraju LibreView na podstawie regionu telefonu przy pierwszym uruchomieniu, ale bez nadpisywania poprawnej wartości zapisanej wcześniej.

Obsługiwane mapowanie krajów:

- GB / UK
- FR
- NL
- PL
- RU

Po poprawnym logowaniu do konta LibreView kraj może zostać skorygowany na podstawie kraju zwróconego przez konto. Korekta jest wykonywana raz dla danego konta.

Dla Polski oczekiwane dane wysyłane do LibreView to m.in.:

- Region: `PL`
- jednostka zgodna z ustawieniem aplikacji,
- model: `com.freestylelibre.app.pl`

Kod uploadu korzysta z aktualnego kraju oraz jednostki aplikacji, zamiast utrwalać niepoprawną wartość z pierwszej próby wysyłki.

## 3. LibreView – ponowna rejestracja urządzenia Libre 2

Do ustawień LibreView dodano możliwość ręcznego wymuszenia ponownej rejestracji urządzenia Libre 2.

Nowy przycisk:

`Zarejestruj urządzenie Libre 2 ponownie`

Działanie:

- ustawia jednorazowe żądanie ponownej rejestracji,
- zeruje stan autoryzacji Libre 2 po stronie natywnej,
- przy następnej autoryzacji wymusza `SetDevice=true`,
- flaga jest czyszczona dopiero po udanej autoryzacji,
- nie kasuje historii wysłanych danych.

Jest to celowo oddzielone od istniejącego `Wyślij ponownie dane`, które resetuje również pozycje wysyłki.

W ekranie LibreView dodano także podgląd:

- regionu,
- wysyłanej jednostki,
- `modelName`.

## 4. Powiadomienie – brak starej wartości

Poprawiono zachowanie głównego powiadomienia z glukozą.

Po przekroczeniu limitu wieku odczytu stara liczba nie powinna być dalej prezentowana jako aktualna. Powiadomienie przechodzi do informacji w rodzaju:

`Brak nowej wartości od HH:mm`

Timeout używany przez aplikację wynosi obecnie około 5 min 30 s (`330 s`).

Dodano również odświeżenie czasowe, dzięki któremu powiadomienie może przejść w stan „brak nowej wartości” bez czekania na kolejny odczyt sensora.

## 5. Widżet – brak starej wartości

Widżet Expressive został dostosowany tak, aby po utracie świeżych danych nie utrzymywał starej liczby jako aktualnej.

Po przekroczeniu timeoutu widżet pokazuje stan braku nowej wartości zamiast ostatniej liczby.

## 6. Ekran blokady / AOD – brak starej wartości

Naprawiono osobną ścieżkę `AODOverlayService`.

Wcześniej ekran blokady/AOD mógł:

1. poprawnie odrzucić stary odczyt bieżący,
2. a następnie ponownie pobrać tę samą starą wartość z historii wykresu.

Efektem było to, że zwykłe powiadomienie i widżety zgłaszały brak nowej wartości, ale ekran blokady nadal pokazywał starą glukozę.

Po zmianie:

- stara liczba nie jest używana jako fallback bieżącej glukozy,
- strzałka znika, gdy nie ma świeżego odczytu,
- wyświetlana jest informacja `Brak nowej wartości od HH:mm`,
- historia wykresu może pozostać widoczna.

## 7. Najnowszy odczyt NFC ma pierwszeństwo przed starszym BLE

Naprawiono przypadek, w którym:

- starszy callback BLE nadal mieścił się w dozwolonym timeoutcie,
- wykonano późniejszy skan NFC,
- historia i strzałka znały już nową wartość,
- ale liczba w powiadomieniu i widżetach nadal pochodziła ze starszego BLE.

`CurrentDisplaySource` porównuje teraz czas odczytu bieżącego z najnowszym punktem historii. Jeżeli historia tego samego sensora zawiera nowszy odczyt, wyświetlana jest nowsza wartość.

Dzięki temu liczba i strzałka korzystają z tego samego najnowszego stanu.

## 8. Telefon jako stały właściciel BLE sensora przy wyłączonym handoffie

Naprawiono konflikt telefonu i zegarka, który mógł prowadzić do równoległych prób połączenia BLE z tym samym sensorem, zrywania połączenia i serii reconnectów (m.in. obserwowane wcześniej statusy `147 / 0x93` i `19 / 0x13`).

W upstreamowym mechanizmie `SensorOwnershipRuntime` zegarek otrzymywał wcześniej zamiar `TAKE` nawet wtedy, gdy jednocześnie były wyłączone:

- `Automatyczne przełączanie czujnika`,
- `Bezpośredni sensor na zegarku`.

Oznaczało to, że sam fakt działania aplikacji na zegarku nadal pozwalał zegarkowi próbować wejść na BLE sensora. Dla sensorów obsługujących praktycznie jednego klienta naraz mogło to powodować walkę telefonu i zegarka o GATT.

Po poprawce tryby własności są jednoznaczne:

- oba przełączniki wyłączone: **telefon jest stałym właścicielem sensora**, a zegarek działa wyłącznie jako companion i odbiera odczyty z telefonu,
- `Automatyczne przełączanie czujnika` włączone: telefon i zegarek mogą uczestniczyć w istniejącym arbitrażu i przejmować sensor zależnie od dostępności,
- `Bezpośredni sensor na zegarku` włączony: zegarek jest preferowanym właścicielem, a telefon wykonuje istniejący handoff i ustępuje.

Dodano również twardą blokadę przed `connectGatt()` po stronie zegarka. Gdy zegarek jest w trybie companion-only, blokada działa natychmiast, również zanim okresowy `reconcile()` zdąży ustawić stan ownership. Dzięki temu zamknięte jest krótkie okno startowe, w którym zegarek mógł wcześniej rozpocząć połączenie z sensorem.

Zmiana jest centralna w `SensorOwnershipRuntime.kt`, więc nie wymaga specjalnej poprawki wyłącznie dla Libre 2 i zachowuje istniejący mechanizm bezpośredniego sensora na zegarku oraz automatycznego handoffu.

Dodano testy dla:

- trybu phone-only,
- ponownego dopuszczenia obu urządzeń po włączeniu auto-switch,
- twardej blokady GATT zegarka przed pierwszym reconciliation,
- zachowania istniejącego bezpośredniego przypisania sensora do zegarka.

## 9. Dashboard

Wprowadzono zmiany w układzie dashboardu, przede wszystkim w celu uzyskania bardziej zwartego widoku i ograniczenia niepotrzebnie zajmowanego miejsca.

Zmiany znajdują się głównie w:

`Common/src/mobile/java/tk/glucodata/ui/DashboardComponents.kt`.

## 10. Ustawienia alertów

Zmodyfikowano ekrany ustawień alertów oraz wspólne komponenty UI.

Dotyczy m.in.:

- układu kontrolek alertów,
- grup przycisków,
- spójniejszego zachowania wspólnych ustawień alertów,
- tekstów interfejsu i tłumaczeń.

Najważniejsze pliki:

- `AlertSettingsScreen.kt`
- `CommonAlertSettings.kt`
- `ConnectedButtonGroup.kt`
- pliki `strings.xml`.

## 11. Skrypt BUILD.ps1

Do repo dodano `BUILD.ps1` dla Windows / PowerShell.

Skrypt:

- uruchamia build wersji Mobile i Wear,
- tworzy osobny katalog archiwum dla każdego buildu,
- nie nadpisuje poprzednich buildów,
- kopiuje wynikowe APK,
- zapisuje sumy SHA256,
- zapisuje informacje o commicie, branchu i stanie repo.

Archiwa trafiają do:

`Builds/<timestamp>/`

Uwaga: katalog `Builds/` jest lokalnym wynikiem kompilacji i nie powinien być dodawany do Git.

## 12. Pliki celowo nieprzechowywane w repo

Do prywatnego repo nie są dodawane:

- `Builds/`
- `_original-libs-11.3.0/`
- lokalne pliki `*.patch`
- `local.properties`
- lokalne notatki builda.

Szczególnie biblioteki wyciągnięte z oryginalnego APK pozostają wyłącznie lokalnie.

## 13. Stan zmian względem upstream

Gałąź `personal` jest oparta bezpośrednio na:

`95c56d00aadf63a843feb80bfc898e6def51d538`

Pierwszy commit wersji personal zmienia 31 plików i dodaje `BUILD.ps1`.

Repo zachowuje osobne źródła zdalne:

- `origin` – oficjalne `ctqvva/JugglucoNG`
- `personal` – prywatne `VViktorr/JugglucoNG-personal`

Dzięki temu można nadal porównywać własne zmiany z upstreamem bez przepisywania historii oficjalnego projektu.

---

## Historia

### 2026-10-03

Dodano tryb stabilnego właściciela sensora dla konfiguracji telefon + Wear OS:

- gdy auto-switch i direct-sensor są wyłączone, tylko telefon może łączyć się z sensorem po BLE,
- zegarek nadal odbiera dane z telefonu,
- twarda blokada zapobiega `connectGatt()` na zegarku także przed pierwszym przebiegiem arbitrażu,
- automatyczne przełączanie i jawny direct-sensor na zegarku zachowują swoje dotychczasowe funkcje.

### 2026-10-02

Pierwszy zapis kompletnego zestawu zmian personal:

`9822cca1d6924083be4a17b30d07f1055855cbb3`

Zakres:

- Libre 2 reconnect,
- LibreView region / jednostka / model,
- ponowna rejestracja Libre 2 w LibreView,
- poprawki powiadomień i stale reading,
- widżety,
- lockscreen / AOD,
- wybór najnowszego odczytu NFC vs BLE,
- dashboard,
- ustawienia alertów,
- skrypt builda dla Windows.
