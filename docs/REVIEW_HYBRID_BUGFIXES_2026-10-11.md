# Przegląd błędów Libre 2 Hybrid — 2026-10-11

Punkt wyjścia: `45fd854678e0c43af88d0929f1463f0fbd823be7`, gałąź `libre2-hybridOK`.
Zakres tej serii: weryfikacja ośmiu zgłoszonych problemów, ich naprawa oraz dalszy przegląd własności wskaźników i współbieżności odzyskiwania BLE.
Przegląd statyczny; bez kompilacji, uruchamiania Gradle/JUnit ani testów na sensorze.

| Obszar | Wniosek i zmiana |
| --- | --- |
| WearSync2, początek synchronizacji | Błąd jednostek: JNI zwraca milisekundy, protokół sekundy. Przywrócono nakładanie 15 minut. |
| WearSync2, wysyłka bieżącej wartości | Pomijany był filtr sensora głównego. Dodano wybór zgodny z lokalną ścieżką BLE; magazynowanie historii pozostałych sensorów pozostaje aktywne. |
| Monitor listy callbacków | Zwalnianie/wstrzymywanie callbacków pod monitorem listy odwracało kolejność blokad. Wycofanie i znacznik stop pozostają atomowe; zamknięcie następuje poza monitorem listy. |
| Libre 3, stare callbacki | Kontrola GATT tylko logowała rozbieżność. Obecnie odrzuca stare transporty i zatrzymane callbacki przed zmianą stanu. |
| WearSync2, metadane sensora | Alokowany wrapper nie był zwalniany. Zastąpiono go odczytem istniejącego snapshotu metadanych. |
| Zmiana trybu wyświetlania | Własny tymczasowy wrapper natywny jest zwalniany w finally. Wskaźniki zarządzanych driverów nie są zwalniane przez UI. |
| lastglucose/lastglucosetime | getlaststream oczekuje bieżącego czasu, nie limitu wieku. Poprawiono oba wywołania; zachowano preferencję sensora głównego i próg 10 minut. |
| Libre 3, wakelock | Dodano limit 60 sekund i finally, także na ścieżce wyjątku. |

Dodatkowe potwierdzone problemy naprawione w tej serii:

- Niepełny pakiet Wear mógł utworzyć pusty rekord sensora przed błędem odczytu. Pełny rozmiar ramki jest sprawdzany przed mutacją.
- Ostatni element pakietu nie musi być najnowszym odczytem. Bieżąca wartość jest wybierana według czasu.
- Reset natywnego wskaźnika zwalniał go przed odłączeniem transportu. Reset jest serializowany i najpierw odłącza GATT.
- Odczyt statusu widżetu pozostawiał własny wrapper natywny, także przy powrocie z tekstem rozgrzewania. Dodano finally.
- Odczyt natywny przeglądał mutowalną globalną listę sensorów; korzysta teraz ze snapshotu i pomija brakujące historie.
- Odejmowanie przyszłego czasu od unsigned czasu bieżącego mogło uruchamiać niepotrzebną odbudowę sieci. Wiek jest ograniczony od dołu do zera.
- Inicjalizacja Libre 3 i zwalnianie kontekstu kryptograficznego mogły przebiegać równolegle. Są serializowane z callbackami.
- Odzyskiwanie innych sensorów iterowało po żywej liście i zakładało istniejący kontroler Bluetooth. Używa snapshotu, sprawdza zatrzymanie i pracuje poza monitorem callbacka źródłowego.

Celowe zachowania zachowane:

- Libre 2 na telefonie przechowuje import Wear w Room, bez modyfikacji natywnego kursora i stanu uwierzytelniania (#603).
- Historia obejmuje wszystkie synchronizowane sensory; powiadomienia i bieżąca wymiana wskazują sensor główny.
- Dane źródłowe oraz wspólne przeliczenia kalibracji nie zostały zastąpione nowym algorytmem.
- Ponowne przeliczenie tego samego znacznika czasu pozostaje dozwolone zgodnie z RealtimeReadingPolicy; blokowane są starsze wartości.
- Odczyty historyczne nadal mogą zasilać eksport danych. Nie dodano arbitralnego limitu wieku odcinającego zamierzone uzupełnianie historii.
- Nie zmieniono protokołu uwierzytelniania, priorytetów BLE, alarmów ani najnowszego układu kart Hybrid i limitów osi wykresu.

Pozostaje weryfikacja działania na telefonie i Wear: reconnect, ręczny reset, wyłączenie Clone podczas odbioru, dwa równoległe sensory, niepełne pakiety, uzupełnianie historii i zmiana kalibracji dla tej samej próbki.
Nie jest to dowód braku błędów w całym repozytorium. Pełny audyt pozostałych driverów i natywnej własności historii nadal wymaga osobnego pogłębienia.

## Kontynuacja: pozostałe drivery i JNI

Punkt wyjścia tej kontynuacji: `7bc9c125ffd6cf4b936bd1f102669e4a23491342`.
Osiem commitów kodu przed zapisaniem tego uzupełnienia; zmieniono sześć plików kodu.

| Obszar | Potwierdzony problem i naprawa |
| --- | --- |
| Dexcom: callbacki BLE | Callback starego GATT mógł zmieniać fazę uwierzytelniania lub używać wskaźnika po wycofaniu. Dodano sprawdzenie aktualnego transportu, stop i serializację głównych callbacków z free/reset. |
| Dexcom: certyfikaty | Kopiowanie fragmentu bez sprawdzenia pozostałego rozmiaru bufora mogło wywołać wyjątek. Nieprawidłowy fragment kończy bieżącą próbę; prawidłowe fragmenty zachowują format i kolejność. |
| Dexcom: AES JNI | Osiem bajtów mogło być czytane/zapisywane poza tablicą przy błędnym przesunięciu lub krótkim wyzwaniu. Sprawdzane są wskaźnik, długości, przesunięcia i wyniki szyfrowania. |
| Dexcom: podpisane wyzwanie | Kod pobierał 16 bajtów od przesunięcia 2 bez sprawdzenia długości. Obsługuje teraz niepełną wiadomość, błąd pinowania i błąd alokacji odpowiedzi. |
| Dexcom: podpis ECDSA | Wynik podpisywania nie był sprawdzany; błąd pozostawiał niezainicjalizowany bufor odczytywany jako DER. Sprawdzane są wynik i długości; błędny podpis nie jest wysyłany. Matematyka poprawnego podpisu pozostaje taka sama. |
| Dexcom: uzupełnianie historii | Ujemny czas/index lub odrzucony zapis mogły mimo to zmienić kursory synchronizacji. Kursory zmieniają się dopiero po poprawnym zapisie do dostępnej pozycji. |
| Dexcom: zadania opóźnione | Wysyłanie certyfikatu, ponawianie poleceń, backfill i odkrywanie usług mogły użyć nowej sesji po zamknięciu starej. Zadania są przypisane do transportu i, gdzie potrzeba, fazy oraz generacji wysyłania. |
| Dexcom: zamknięcie | Wakelock był zwalniany głównie po callbacku rozłączenia, który może nie nadejść. close zwalnia go w finally; getlock nie porzuca poprzedniego uchwytu. Zachowano nielimitowany czas wakelocka podczas aktywnej wymiany protokołu. |
| iCan: callbacki i reset | Powiadomienia, odpowiedzi odczytu/zapisu i odkrycie usług nie odrzucały starego GATT. Dodano kontrolę aktualnej sesji oraz odłączenie transportu przed zwolnieniem wskaźnika w resetdataptr. |
| Anytime/iCan: usuwanie | terminate zerowało owning dataptr przed późniejszym free w SensorBluetooth.sensorEnded. Uchwyt pozostaje dostępny do wspólnego zwolnienia, tak jak w driverze MQ. Nie jest zwalniany przed wycofaniem callbacka. |
| Wspólny wrapper JNI | Destruktor próbował zwolnić tablicę także po nieudanym GetPrimitiveArrayCritical. Zwalnia tylko skutecznie przypiętą tablicę. |

Celowe zachowania dodatkowo zachowane:

- Początkowe operacje Dexcom podczas parowania mogą rozpocząć się, zanim jego lokalna flaga connected zostanie ustawiona; zabezpieczenia opierają się na aktualnym GATT i stop.
- Fragmenty certyfikatów Dexcom nadal mają 20 bajtów i odstęp 40 ms; ponawianie zachowuje liczbę prób i odstępy.
- Akceptowanie części wariantów CRC Protocol02 w MQ jest udokumentowaną zgodnością z nadajnikiem, nie zostało zaostrzone.
- Zamykanie handlera MQ jest terminalne; softReconnect używa closeGattTransport i zachowuje handler. Nie zmieniono tego rozróżnienia.
- Ograniczanie kopiowania fragmentów parametrów AiDex do bufora pozostaje zachowane.
- Nie zmieniono Libre 2 Hybrid, UI, LibreView, kalibracji, jednostek ani sposobu przechowywania poprawnych odczytów.

Weryfikacja obejmuje odczyt zapisanych plików z końcowego commita, porównanie ograniczonych fragmentów, zgodność sygnatur JNI i istniejących helperów oraz kontrolę struktury zmienionych źródeł.
Nie uruchamiano kompilacji ani JUnit.

Pozostają rzeczywiste zadania:
1. Testy na Dexcom i iCan: parowanie, callback starego GATT, przerwanie i wznowienie wysyłania certyfikatu, reset oraz brak callbacka rozłączenia.
2. Test usunięcia Anytime/iCan i ponownego dodania sensora, z kontrolą zwolnienia wrappera.
3. Pogłębiony audyt zadań handlerów w MQ/Ottai/AiDex/Anytime i natywnej własności/mapowania historii przy równoległym usuwaniu sensorów.
4. Pełny audyt pozostałych modułów repozytorium. Powyższa seria nie stanowi zapewnienia, że cały kod jest wolny od błędów.
