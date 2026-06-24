# AdGuard TV DNS Pro v3.5.2 updater + TV UI fix

Wersja 3.5.2 poprawia mechanizm aktualizacji z GitHub Releases oraz fokus/wygląd na Android TV.

## Najważniejsze zmiany

- versionCode 37
- versionName 3.5.2-updater-ui-fix
- updater porównuje przede wszystkim `versionCode`, a nie opisową nazwę wersji
- GitHub Actions wpisuje do Release Notes aktualny `versionCode` i `versionName` z `app/build.gradle`
- w teście GitHub Releases można pobrać najnowszy APK testowo nawet wtedy, gdy wersja nie jest nowsza
- po wejściu do aplikacji fokus domyślnie trafia na główny przycisk połączenia
- dolne menu dostało widoczne podświetlenie fokusu pod pilota TV
- delikatnie zmniejszone przyciski/czcionki i górny/dolny pasek
- ekran „O aplikacji” pokazuje versionName + versionCode

## Jak sprawdzić aktualizację

1. Wypchnij kod na `main` i sprawdź, czy build jest zielony.
2. Utwórz nowy tag, np. `v3.5.2`.
3. GitHub Actions opublikuje Release z podpisanym APK.
4. Na urządzeniu z wersją starszą niż versionCode 37 kliknij: Ustawienia → O aplikacji → Sprawdź aktualizację.

Jeżeli masz już zainstalowaną tę samą wersję, przycisk „Sprawdź aktualizację” uczciwie pokaże brak nowszej wersji. Do testu samego pobierania użyj: Ustawienia → O aplikacji → Test GitHub Releases → Pobierz najnowsze APK testowo.
