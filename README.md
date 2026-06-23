# AdGuard TV DNS Pro v3.5 updater test

Wersja 3.5 poprawia mechanizm aktualizacji z GitHub Releases, czytelność jasnego motywu oraz diagnostykę updatera.

## Najważniejsze zmiany

- versionCode 35
- versionName 3.5-updater-test
- poprawiony build.gradle: lint nie blokuje release przez ExpiredTargetSdkVersion
- poprawiony updater: rozróżnia brak release, brak APK w release i brak nowszej wersji
- nowy przycisk: Ustawienia > O aplikacji > Test GitHub Releases
- poprawiony jasny motyw w logach i polach tekstowych
- języki beta są oznaczone w liście języków

## Aktualizacja i release

Instrukcję krok po kroku znajdziesz w:

`tools/PUBLISH-v3.5-README.txt`

