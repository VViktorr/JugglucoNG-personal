# Modernizacja JugglucoNG na `allnew`

Punkt wyjścia to `main5` w commicie `277c5303eec6a0f1610a6ed63cf2c741cab2ad26`. Porównanie obejmuje konfigurację Androida, biblioteki, kod natywny i dystrybucję na dzień 26 listopada 2026.

## Zmiany wersji

| Składnik | main5 | allnew |
| --- | --- | --- |
| Gradle Wrapper | 9.8.0 | 9.9.2, przypięta suma SHA-256 dystrybucji |
| Android Gradle Plugin | 9.3.2 | 9.5.1 |
| Kotlin i wtyczka Compose Compiler | 2.4.10 zadeklarowane dla Compose Compiler | 2.5.0-RC2, jawny wybór Kotlin dla wbudowanego trybu AGP |
| KSP | 2.3.11 | 2.3.14 |
| JDK w CI | 21 | 27 (Temurin) |
| Poziom języka Java i bytecode Java/Kotlin | 17 | 17 |
| Android compile SDK | 37.1 | 37.2 |
| Android target SDK telefonu i zegarka | 36 / 35 | 36 / 35 |
| Android Build Tools | 37.0.0 | 37.0.0 |
| Android NDK | 29.0.14206865 | 30.0.16248370, Clang 21 |
| CMake | 4.1.2 z SDK Managera | 4.5.1 z wydania Kitware |
| Standard C++ Android | Gradle deklarował GNU C++26, CMake nadpisywał go na GNU C++23 | GNU C++26 ustawiany w CMake |
| Compose BOM | 2026.10.01 z kanału alpha | 2026.11.01 stable |
| Material 3 | 1.5.0 przypięte ręcznie | 1.5.0 z BOM |
| Compose Material 3, Foundation i Navigation dla Wear | 1.7.1 | 1.7.2 |
| Navigation Compose | 2.9.8 | 2.10.2 |
| Glance AppWidget i Material 3 | 1.1.1 | 1.2.0 |
| Room runtime, KTX, KSP, testy | 2.8.4 | 2.8.5 |
| CameraX Core, Camera2, Lifecycle i View | 1.6.1 | 1.7.0 |
| Activity | 1.13.0 | 1.13.0 |
| Lifecycle Compose | 2.11.0 | 2.11.1 |
| AndroidX Core KTX | 1.19.0 | 1.19.1 |
| WorkManager | 2.11.2 | 2.12.0 |
| AndroidX Concurrent Futures | 1.2.0 | 1.3.0 |
| Google Play Services Base / Wearable | 18.10.1 / 20.0.1 | 18.11.1 / 20.0.1 |
| Guava Android | 33.7.1-android | 33.8.0-android |
| zstd-jni w APK i testach | 1.5.7-12 | 1.6.0-1 |
| Robolectric | 4.14 | 4.16.1, ostatnia wersja z `callThroughByDefault` |
| AndroidX Test Core | 1.6.1 | 1.7.0 |
| SQLite JDBC w testach | 3.41.2.2 | 3.54.1.0 |

AndroidX AppCompat, coroutines, RecyclerView, Startup, Health Connect, Material Components, Garmin, ZXing, ML Kit, Play Services Wearable, biblioteki Wear Tiles i ProtoLayout, JUnit oraz JSON pozostają na wersjach bazowych. Artefakty Watch Face pozostają w dotychczasowym spójnym zestawie 1.2.1. Nowsze wersje poszczególnych artefaktów obsługi komplikacji wymagają osobnej migracji zegarka. Robolectric 4.17 usunął parametr `callThroughByDefault`, z którego korzystają testy BLE i JNI. Wersja 4.16.1 zachowuje odcięcie wywołań do rzeczywistych implementacji w tych testach. Gradle rozwiązuje także zależności przechodnie. Raport wersji dotyczy deklaracji bezpośrednich i nie zastępuje sprawdzenia grafu rozwiązanego dla obu wariantów.

Weryfikacja wersji korzystała z metadanych Google Maven, Maven Central, katalogu pakietów Android SDK i dokumentacji Gradle, Kotlin oraz Android Developers.

## Libre 2 i strony 16 KB

- Zmiana z `main5` w commicie `59ddf5165f35` pozostaje w produkcyjnym callbacku BLE. Test hosta wywołuje metody produkcyjne z symulowanym BLE i zegarem. Pokrywa serię rozłączeń, limit jednej eskalacji przed następnym pakietem, opóźnienie 6 s, obserwację ciszy, stare callbacki i anulowanie.
- Upstream [#937](https://github.com/ctqvva/JugglucoNG/pull/937) został scalony jako `d54709218` 25 listopada o 23.43 czasu polskiego. Przenosi logowanie BLE i NFC Gen2 do Kotlin, a przetwarzanie glukozy i historii do otwartego backendu C++. Jego logika ponawiania połączeń i obsługa kompletnego pakietu różnią się od `main5`. Upstream przewiduje dalszy odczyt bez skanowania przy zgodnym stanie algorytmu, a odtworzenie niezgodnego stanu przy następnym NFC. Na `allnew` pozostaje dotychczasowy sterownik do czasu potwierdzenia migracji aktywnego sensora, historii, BLE i odtworzenia stanu po NFC na osobnym APK.
- Backport [#1033](https://github.com/ctqvva/JugglucoNG/pull/1033), otwartego w upstreamie, przyjmuje stałą jednostkę 4096 bajtów dla formatów plików, ogranicza zapis trendu do pojemności pliku i uniezależnia limit komunikatu dla Wear od rozmiaru strony. Test hosta obejmuje nowe i stare rekordy, przenoszenie między symulowanymi stronami, pełny plik trendów i naprawę pustych plików. Symulacja nie zmienia rozmiaru strony jądra hosta.
- Z osobistego wydania odtworzono 34 przypięte pliki JNI i sprawdzono ich sumy. Siedem ELF ARM64 ma minimalne wyrównanie segmentów `LOAD` równe 4 KB: `libalgorithm-jni.so`, `libcgat-lib.so`, `libmmkv.so`, `libmodft2.so`, `libmodpdfium.so`, `libmodpng.so` i `libobjectbox-jni.so`. Audyt dotyczy wejść z wydania bazowego. Weryfikacja wszystkich bibliotek w nowym APK i uruchomienie na Androidzie ze stronami 16 KB są osobnymi zadaniami.

## Budowanie i dystrybucja

`scripts/dist/install-sdk.sh` pobiera compile SDK i Build Tools z tych samych ustawień co projekt, niezależnie od target SDK. CI uruchamia testy JVM telefonu i Wear OS oraz kompilacje debug i release na ARM64, ARMv7, x86 i x86_64. Dystrybucyjne APK nadal mają domyślnie dwa ABI ARM. Natywne pliki dostawców pochodzą z przypiętego `main5-v1.4.0` w osobistym repozytorium. Źródłowy submoduł libjuice 1.6.2 pozostaje na commicie `4625a1b` w forku `ctqvva/libjuice`. Głowa forka nie zawiera tego commita, a zmiana wskaźnika usunęłaby poprawkę obsługi tablicy poll. Kod LibAscon 1.1.1 i biblioteki kryptograficzne dobierane przez istniejący loader nie zostały zmienione. Workflow osobistych APK wyszukuje `apksigner` w faktycznie zainstalowanych stabilnych Build Tools. Zachowano podpisywanie, numerację telefonu i zegarka, repozytorium aktualizacji i warunek publikacji dla `main5`.

Lokalne budowanie z CMake 4.5.1 wymaga wersji Kitware i Ninja w `PATH`. Android Studio może też dostać ścieżkę katalogu instalacji CMake przez `cmake.dir` w `local.properties`. Installer dla GitHub Actions pobiera archiwum Kitware dla Linux x86-64 i sprawdza jego SHA-256.

## Weryfikacja i braki

- Lokalnie przeszły testy formatu plików dla stron 4/16 KB, symulacja odzyskiwania Libre 2, 98 testów dystrybucji, kontrola stylu Compose i 8 testów skryptu stylu. Gradle 9.9.2 wykonał zadanie `help` i skompilował kod Kotlin debug wariantów telefonu i Wear OS z SDK 37.2.
- CMake 4.1.2 i NDK r30 zbudowały telefoniczne `libg.so` dla arm64-v8a, armeabi-v7a, x86 i x86_64. Wariant Wear OS zbudowano dla tych samych czterech ABI. Segmenty `LOAD` wszystkich ośmiu plików mają wyrównanie 16 KB. `apksigner` sprawdził podpis v2 telefonu z przypiętego wydania bazowego.
- [GitHub Actions dla allnew](https://github.com/VViktorr/JugglucoNG-personal/actions?query=branch%3Aallnew) przechowuje wyniki JVM i kompilacji per ABI. Pierwszy przebieg wykrył zmianę kontraktu `BluetoothLeScanner.startScan` w SDK 37.2, poprawioną w `7adbb0c1`. Kolejny wykrył usunięcie `callThroughByDefault` z Robolectric, obsłużone w `9742140d`. Kontrolny APK z CI nie jest podpisanym wydaniem dystrybucyjnym.
- [Przebieg CI 45790199571](https://github.com/VViktorr/JugglucoNG-personal/actions/runs/45790199571) zakończył oba zestawy JVM wynikiem niezaliczonym. Telefon wykonał 5898 testów, z czego 84 nie przeszły i 1 pominięto. Wear OS wykonał 4245 testów, z czego 40 nie przeszło i 9 pominięto. Przebieg jest sprzed poprawki symulatora powiadomień. Oprócz tej poprawki do dalszej diagnozy pozostają m.in. testy widżetów i powiadomień, kalibracji, ustawień oraz architektury.
- Lokalny zestaw JVM telefonu wykonał 5213 testów, z czego 133 zakończyło się błędem, a 1 pominięto. W 89 przypadkach Robolectric nie pobrał artefaktów Androida w środowisku lokalnym. 36 przypadków zatrzymał błąd kompilacji symulatora przywracania powiadomień. Po uzupełnieniu atrap ponowny przebieg tego zestawu zaliczył 35 z 36 testów. W pozostałym teście po przejściu do stanu starych danych odnotowano jedną emisję aktualizacji glukozy zamiast oczekiwanego zera. W pozostałych przypadkach zawiodły m.in. asercje źródeł widżetu, test rejestracji bez kontekstu aplikacji i test architektury. Dwie asercje układu widżetu oczekują źródeł innych niż te już zapisane w `main5`. Jest to wynik niezaliczony, niezależnie od powodzenia kompilacji kodu produkcyjnego.
- Testy urządzeniowe pozostają do wykonania. Dotyczy to Androida 17, parowania i aktywacji NFC, aktywnego sensora, alarmów, widżetów, powiadomień, AOD, synchronizacji z Wear i Clone, importu/eksportu i aktualizacji istniejącej instalacji. Pomiar baterii, RAM, pracy przy wygaszonym ekranie, częstotliwości odświeżeń, logów i rozmiaru APK wymaga artefaktów oraz urządzeń. Testy hosta i kompilacja nie potwierdzają działania tych funkcji na sensorze ani zegarku.
