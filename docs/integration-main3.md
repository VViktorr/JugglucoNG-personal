# Integracja personal do main3

## Źródła

- Baza main3: `507022ab46626334ae25a5eb7d0df6a00a3c8a2e`, dokładna zawartość upstream `ctqvva/JugglucoNG` z `cd2e990d9110fdbe7667f5b308080e3a0c0216c2` (1.2.5-Alpha).
- main: `47eb76ce311d473ff6e281a9837403619c1b8599`; poprawki są już zawarte lub rozwinięte w main2.
- main2: `962bf69abc5cf3232ff404d5478f0442cfa73003`.
- PR #8: `3f87b128a874ccf76b9cf3b1eb045a25fe10a37a`, usunięcie 13 ostrzeżeń Kotlina w ośmiu plikach.

## Sposób integracji

Odtworzono lokalnie pełne drzewo main2 i potwierdzono SHA `47254daa996b0c50e91152f7a43a6c39e51b23da`.
Scalono jego zmiany z nowym upstreamem trójstronnie, względem poprzedniej bazy upstream `d731a75ad3a64f305bd640a39db8f31f1bdb30ac`.
Scalenie nie miało konfliktów tekstowych. Następnie zastosowano cały patch PR #8.

Zachowano poprawki personalne opisane w `integration-main2.md` oraz późniejsze zmiany: zawijanie komunikatów dashboardu i widżetów, ochronę odświeżania przed wyścigiem, styl przycisków oraz osobiste workflow APK.
Pozostawiono nowsze sterowniki, interfejs, wersję 1.2.5 i audio upstreamu. Wycofany dostawca IMGW nie wraca.

Workflow CI obejmuje main3. Osobiste workflow telefonu i Wear publikują artefakty nazwane main3 i reagują na zmianę swoich plików na tej gałęzi.
Poprawiono fixture testu niezmienionych linków README: symulowany przycisk pobierania ma teraz tę samą wersję co symulowany README, niezależnie od wersji pliku w repozytorium.

## Sprawdzone lokalnie

- 89 testów kontraktu dystrybucji: PASS.
- Reguły UI: PASS, bez podnoszenia baseline.
- Tłumaczenia: 67 plików XML, zero błędów struktury i formatowania, zero braków PL dla telefonu i Wear.
- XML zmienionych plików, brak znaczników konfliktów i `git diff --check`: PASS.
- Numer wersji 1.2.5 i brak zmian w audio względem upstreamu: potwierdzone.

Zgodnie z poleceniem użytkownika nie uruchamiano lokalnej pełnej kompilacji APK ani nie oczekiwano na GitHub Actions. Wynik pełnych testów Android/JVM i kompilacji telefonu/Wear pozostaje niepotwierdzony.
