# Libre 2: połączenie 1.2.8 i diagnostyki main

Zmiana dotyczy Libre 2 na `main5`. Bazą jest callback z oryginalnego
1.2.8-Alpha (`a4fbdcce5c7312ca05fe0ef74a52848df0965ae8`).
Nie przeniesiono całego starszego callbacku. NFC i algorytmy pozostałych sensorów
pozostają z 1.2.8; wspólny `SuperGattCallback` też jest niezmieniony.

## Działanie trzech wariantów

| Mechanizm | Oryginał 1.2.8 | Osobisty main | Nowe main5 |
| --- | --- | --- | --- |
| Brak kompletnego pakietu po CONNECTED / ostatnim pakiecie | 150 s | 120 s | 150 s |
| Pierwszy krok odzyskiwania streamu | Zwykłe rozłączenie i nowy GATT | Od razu twarde odzyskiwanie | Zwykłe rozłączenie i nowy GATT z 1.2.8 |
| Mocniejszy krok | Brak dodatkowego etapu tego watchdoga | Zamknięcie GATT i reset natywny | Dopiero po kolejnych 150 s bez kompletnego pakietu, niezależnie od kolejnych błędów prób połączenia |
| Przerwa po twardym odzyskiwaniu | Nie dotyczy | 6 s | 6 s od resetu |
| Dodatkowy wake lock odzyskiwania | Brak | Do 15 s | Do 15 s; zwolnienie po pierwszym wyniku nowego połączenia, anulowaniu lub niewznowieniu |
| Limit twardego odzyskiwania | Nie dotyczy | Brak nowego limitu jednego resetu na cały epizod | Jeden dodatkowy twardy reset do czasu kolejnego kompletnego pakietu |
| Diagnostyka | Zwięzłe statystyki sesji | Rozbudowana | Rozbudowana, bez surowych identyfikatorów i sekretów |
| Ochrona logów | Obecne miejsca ujawniające dane | Sam LIBRE2_DIAG nie usuwa wszystkich ujawnień | Ochrona źródeł payloadów i wspólnych punktów zapisu Java/C++ |

Przykład: ostatni kompletny pakiet w chwili 0; zwykłe odzyskiwanie w 150 s;
jeśli nadal nie ma pakietu, jednorazowy reset natywny i zamknięcie transportu
w 300 s, a ponowienie połączenia po 6 s. Opóźnienia planisty mogą przesunąć
wykonanie; diagnoza zapisuje spóźnienie wznowienia. Same fragmenty, RSSI,
wykrycie usług i uwierzytelnianie nie odświeżają licznika.

Kompletny pakiet oznacza złożenie fragmentów 20+18+8 bajtów. Tak jak w 1.2.8,
zdrowie transportu jest oddzielone od przyjęcia odczytu przez parser i publikacji
glikemii. Pakiet odrzucony jako duplikat nadal dowodzi działania transportu.

Jeśli próba connectGatt od początku nie zgłasza wyniku, pozostaje oryginalny
limit próby 120 s i zwykłe ponawianie. Eskalacja opisana powyżej zaczyna się
od watchdoga po CONNECTED. Pierwotne resetowanie uwierzytelniania dla statusu 19
po włączeniu notyfikacji pozostaje z oryginału; limit dotyczy nowego,
dodatkowego twardego odzyskiwania.

Nowa eskalacja i jej dodatkowy wake lock działają na telefonie. Wear zachowuje
zwykły mechanizm 1.2.8. Pauza, zamknięcie, usunięcie sensora i zmiana właściciela
blokują wznowienie. Stare callbacki i zadania z anulowanych generacji nie mogą
modyfikować nowego połączenia. Ponowienia nie przesuwają terminu eskalacji.

## Diagnostyka i prywatność

`LIBRE2_DIAG` zachowuje etap, numer próby, generację resetu natywnego, fazę,
stan adaptera, pauzę, status GATT, RSSI, liczbę pakietów, flagi fragmentów,
wiek pakietu, połączenia, usług, zapisu charakterystyki i deskryptora,
powody odzyskiwania oraz wynik parsera. Nie zawiera wskaźników JNI/GATT.
Bufor w pamięci przechowuje do 1024 znaków na wiersz, aby nie ucinać tych pól.

Numery seryjne i zarejestrowane MAC otrzymują wspólny alias `sensor-N` w obrębie
procesu. Niezarejestrowane MAC/typowe numery Libre są maskowane. Mapowanie
jest wyłącznie w pamięci; nie jest eksportowane ani trwale zapisywane.
Nazwa eksportu również używa aliasu. Logi sprzed tej aktualizacji nie są
przepisywane na dysku; podgląd/kopiowanie/eksport dodatkowo filtruje ich treść.

Surowe klucze, UID i auth-data usunięto z konstrukcji komunikatów callbacku.
Wspólny helper zrzutów bajtów Java/JNI zapisuje tylko opis i długość.
Ochrona Java działa przed buforem, trace.log i awaryjnym Android Log.
Ochrona C++ działa przed zapisem formatowanym i wektorowym oraz w ścieżce
LOGCAT. Filtry maskują znane tożsamości, MAC, wskaźniki i zrzuty hex, a wiersze
z etykietami sekretów są pomijane. Wyjątki Java zachowują typ i ramki stosu,
bez komunikatu wyjątku i zagnieżdżonych przyczyn mogących zawierać dane.
Glukoza, czasy, statusy i RSSI pozostają.

Wspólna ochrona logowania wpływa też na tekst logów innych sensorów;
nie zmienia ich sterowników, protokołów, ustawień ani algorytmów.
Nie stanowi gwarancji anonimizacji dowolnej nieopisanej treści wypisywanej
bezpośrednio przez zewnętrzną bibliotekę z pominięciem tych punktów logowania.

## Sprawdzenie

- `tools/test-libre2-connect.py`: produkcyjne metody cyklu GATT, terminy,
  eskalacja mimo błędów, pojedynczy reset, nowy epizod po pakiecie, opóźnienie,
  wake lock, anulowanie, stare callbacki, własność i brak eskalacji na Wear.
- `tools/test-log-privacy.py`: kompilacja i wykonanie produkcyjnego kodu Java/C++,
  aliasy, sekrety, wskaźniki, fallback przed załadowaniem JNI, wyjątki,
  bufor i natywne zapisy formatowane/wektorowe oraz LOGCAT.
- 22 testy JUnit podglądu logów, nazw eksportów, bufora i jego podłączenia;
  kod produkcyjny, z atrapami zależności Android/JNI i enumu producenta.
- Regresje Libre3 i konfiguracji LibreView oraz 223 chronione pliki 1.2.8.

Nie wykonywano pełnej kompilacji APK ani testu na telefonie/sensorze.
Symulacja nie dowodzi większej skuteczności radiowej lub mniejszego zużycia
energii od wcześniejszych wariantów. Nie opieramy tej polityki na rzekomo
poznanym algorytmie LibreLink: pełna analiza jego metod BLE nie została ukończona.
