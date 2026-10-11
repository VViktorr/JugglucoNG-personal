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
