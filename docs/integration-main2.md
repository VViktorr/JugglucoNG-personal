# Integracja main → main2

## Punkty odniesienia

- main: `13734f9ad38c4c47054c21666610f99b837247f6`
- main2 / pobrany upstream ctqvva/JugglucoNG: `d731a75ad3a64f305bd640a39db8f31f1bdb30ac`
- wspólny przodek: `95c56d00aadf63a843feb80bfc898e6def51d538`
- osobna gałąź: `integration/main-to-main2-20261016`

Integracja korzysta z obiektów i historii Git, bez wcześniej przygotowanego patcha.
Dokładny commit main, całe jego drzewo i brakujące bloby zweryfikowano przez SHA.
Scalenie trójstronne ze wskazanym przodkiem odtworzyło 90 konfliktujących plików.
Konflikty rozstrzygnięto na poziomie fragmentów kodu i kluczy zasobów.
Commit integracyjny zachowuje main2 jako pierwszego rodzica i main jako drugiego.

## Rozstrzygnięcia

| Obszar | Wynik |
| --- | --- |
| Libre 2 / BLE | Nowszy transport upstream z ograniczonymi próbami GATT oraz osobisty watchdog 120 s, odroczony reset 6 s, ograniczony wakelock i diagnostyka. |
| NFC / Raw | Oddzielna historia NFC Raw bez przesuwania kursora BLE; zachowanie obu kanałów przy nakładających się zapisach. |
| Usuwanie sensorów | Terminalne wycofanie callbacków, zwalnianie poza blokadą listy, bezpieczna iteracja natywnych sensorów. |
| Bieżąca wartość / powiadomienia | Odrzucanie starej historii, nowsza świeża historia tylko dla zwykłego powiadomienia, brak starej liczby na ekranie blokady, renderowana kierunkowo strzałka. |
| Dashboard / wykres | Osobiste belki, proporcje, koniec sensora, szare zużycie, narożniki i ograniczona skala; nowsze menu dziennika i kotwiczenie z upstream. |
| Widżety | Czterech dostawców, wspólna historia wielu sensorów, ustawienia wyglądu i pogoda. |
| Wear / synchronizacja | Komplikacje tekst/obraz, strzałka nad wartością, ograniczenie pracy w tle, Raw-only, walidacja ramek i ochrona kursora Libre 2; nowsze ponawianie preferencji i potwierdzeń alarmów pozostaje. |
| Ustawienia / LibreView | Kategorie i lokalizowane wyszukiwanie, region/sesja/rejestracja LibreView, przywrócony alarm dostępności wartości; zachowane nowsze kontrolki upstream. |
| Nowszy upstream | Dexcom 10/15 dni i Flex, korekta czasu Ottai i HTTP fallback, nowsze AiDex/Nightscout, strumieniowy eksport, Material 3 i dostępność. |
| Audio | Zasoby `Common/src/*/res/raw`, `tools/alert-sounds` i nowszy kod audio pozostają z main2; brak ponownego importu pakietu. |

Usunięto zdublowane `isAir()` powstałe przy automatycznej części scalenia.
Naprawiono nieprawidłowe bajty CP1250 w polskim HTML, wykryte przez osobistą
kontrolę zasobów. Nowe przyciski dostosowano do istniejących reguł UI bez
podnoszenia ich limitów. Testowe adaptery zaktualizowano do połączonych zależności.
Filtr istniejącego CI obejmuje również PR-y do main2 i push na main2.

## Weryfikacja lokalna

- 89 testów kontraktu dystrybucji: PASS.
- 31 testów JUnit w samodzielnym przebiegu: PASS — odświeżanie, kanały Wear,
  NFC Raw, historia zmierzona, zwykłe powiadomienia, preferencje Wear,
  polityka połączeń i konfiguracja czterech widżetów.
- 16 scenariuszy `tools/test-wear-sync2.py`: PASS.
- Regresje Libre 2, cyklu życia sensorów, LibreView, historii/synchronizacji
  i starego powiadomienia: PASS.
- CareSens Air (library + GATT), Dexcom (GATT + native), Libre 3 (GATT,
  końcowa synchronizacja historii do Room, NFC i odpowiedzi NFC): PASS.
- 26 natywnych kontroli ramek/ścieżek clone recovery: PASS.
- Testy natywne NFC i clone recovery zachowują ASan/UBSan; kontrolę wycieków
  LSan wyłączono przez adapter kompilatora, ponieważ sandbox blokuje `/proc`.
- Kontrola tłumaczeń: 67 XML, 0 błędów struktury/formatu, brak brakujących
  polskich tłumaczeń dla telefonu i Wear. Pozostałe języki używają istniejącego
  fallbacku języka domyślnego; ich niepełne tłumaczenia są raportowane.
- Reguły UI: PASS bez zwiększania baseline.
- `git diff --check`: PASS; brak znaczników konfliktów; audio bez różnic.
- Parser Kotlin/Java: brak nowych błędów względem upstream w zmienianych plikach.

## Pozostała weryfikacja

Pełne zadania Gradle `testMobileDebugUnitTest`, `testWearDebugUnitTest`,
`assembleMobileDebug` i `assembleWearDebug` próbowano uruchomić lokalnie.
Wrapper zatrzymał się przed testami na pobieraniu Gradle (`Network is unreachable`).
Środowisko ma Javę 17; brak lokalnego Android SDK/NDK i wymaganego JDK 21.
Samodzielne testy JVM nie zastępują kompletnych zestawów Android/Robolectric.
Pełne CI telefonu/Wear/debug/release oraz próby na fizycznym sensorze i zegarku
pozostają wymagane przed scaleniem. Żaden APK nie został tutaj zweryfikowany
na urządzeniu. main i main2 nie są aktualizowane przez tę pracę.
