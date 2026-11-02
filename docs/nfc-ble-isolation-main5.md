# Libre 2: NFC bez przesuwania licznika BLE

Przeniesiono z osobistego `main` osobny magazyn `NfcRawHistory` oraz zapis
minutowego trendu NFC do `nfc-raw-history.dat`. Zmiana dotyczy sensorów Libre 2
rozpoznanych przez patch info i generację zabezpieczeń 1/2. Pozostałe przypadki
wywołują dotychczasowy zapis z 1.2.8, zachowany jako `saveScanTrendLegacy128`.

Dla Libre 2 skan zbiera do 15 starszych próbek trendu, sprawdza zakresy i zapisuje
je według minuty życia sensora. Powtórny skan nie powiela tej samej minuty.
Próbki są scalane z istniejącym plikiem pod blokadą i zapisywane przez plik
przejściowy oraz rename. Czas może zostać dopasowany do pobliskiej poprawnej
próbki BLE, ale jest to tylko odczyt magazynu BLE.

Ta ścieżka nie wywołuje `savepoll()`, nie zmienia `pollcount`, estymacji
interwału, identyfikatora ostatniego pakietu BLE ani stanu parsera Abbott.
Eliminuje więc przesuwanie licznika BLE przez te nowe, surowe próbki trendu NFC.
Nie zmienia standardowego przetwarzania skanu przez algorytm sensora.

## Wykres: zachowane 1.2.8

`saveSputnik_PG2`, `g.cpp`, JNI historii, Room i interfejs wykresu pozostają
bez zmian. Historia sensora co 15 minut nadal uzupełnia luki po braku BLE.
Tryb „ścieżka + Raw” zachowuje dane i sposób działania z 1.2.8.
Nie zastępujemy jego serii Raw minutowym, nieprzetworzonym trendem NFC.
Nowy plik trendu jest magazynem pomocniczym; nie dodaje dodatkowej serii
wykresu ani minutowych wyników algorytmu, alarmów czy bieżących odczytów.

Nie usuwamy ani nie naprawiamy wstecz wcześniejszych wpisów `polls.dat`.
Poprawka zapobiega nowemu przesuwaniu licznika przez trend NFC; już zapisane
błędne rekordy wymagają osobnej analizy zamiast automatycznego kasowania.

## Zakres i weryfikacja

Zmiany produkcyjne obejmują wyłącznie nowy `NfcRawHistory.hpp`, metodę zapisu
w `SensorGlucoseData.hpp` i wybór ścieżki w `share/savehistory.cpp`.
Nie kopiowano całych starszych plików `SensorGlucoseData.hpp` ani `g.cpp`.
Czytniki NFC Java, połączenie BLE oraz sterowniki innych sensorów nie są zmieniane.
Log `NFC_DIAG trend-history` podaje added/known/invalid/persisted, bez UID i kluczy;
korzysta z ochrony logów dodanej wcześniej.

Na polecenie użytkownika nie uruchomiono kompilacji ani testów. Wykonano
przegląd różnic i wywołań. Manifest zachowuje 221 plików zamrożonych na 1.2.8;
trzy pliki autoryzowanej izolacji NFC mają osobno zapisane skróty.
