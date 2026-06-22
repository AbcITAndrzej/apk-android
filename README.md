# AdGuard TV DNS

Prosta aplikacja na Android TV 10 do ustawiania DNS przez lokalny VPN.

## Co robi

- START / STOP.
- Domyślne DNS: AdGuard DNS `94.140.14.14` i `94.140.15.15`.
- Można wpisać własne DNS IPv4.
- Tryb dla wszystkich aplikacji.
- Tryb tylko dla zaznaczonych aplikacji.
- Brak reklam, brak analityki, brak zewnętrznych bibliotek.

## Ważne

Android zwykłej aplikacji nie pozwala zmienić globalnego DNS systemu bez roota albo uprawnień systemowych. Ten projekt robi to profesjonalną metodą dla zwykłej aplikacji: używa `VpnService` i przechwytuje tylko zapytania DNS przez lokalny VPN.

Po pierwszym kliknięciu START Android pokaże systemową zgodę na VPN. Trzeba ją zaakceptować.

## Budowanie APK na GitHubie

Projekt ma gotowy workflow:

`.github/workflows/build-apk.yml`

Po wrzuceniu projektu do repozytorium GitHub wejdź w:

`Actions -> Build APK -> Run workflow`

albo zrób zwykły push do gałęzi `main`.

Gotowy plik APK będzie w `Artifacts` jako:

`adguard-tv-dns-debug-apk`

## Ograniczenia wersji 1.0

- Obsługuje zwykłe DNS UDP/IPv4.
- Nie jest to DNS-over-HTTPS ani DNS-over-TLS.
- Aplikacje z własnym twardo wpisanym DNS/DoH mogą omijać systemowy DNS.
- Debug APK nadaje się do testowania i sideloadingu. Do publikacji potrzebny jest podpisany release build.
