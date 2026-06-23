# AdGuard TV DNS Pro v3.4

Lekka aplikacja Android TV / Android do ustawiania DNS przez lokalny `VpnService`.

## Wersja v3.4

Dodane:

- podpisywany release APK przez GitHub Actions, gdy ustawisz sekrety podpisu,
- updater z GitHub Releases: aplikacja sprawdza release, pobiera APK i otwiera instalator Androida,
- motyw: systemowy / ciemny / jasny,
- ekran `O aplikacji`, wersja i przycisk `Sprawdź aktualizację`,
- test DNS z poziomu aplikacji,
- eksport logów diagnostycznych do pliku,
- IPv6 virtual DNS jako opcja,
- TCP DNS fallback jako opcja,
- autostart po uruchomieniu Android TV jako opcja,
- ekran pomocniczy do ustawień VPN / Always-on VPN.

## Ważne o aktualizacjach

Android pozwoli zaktualizować APK tylko wtedy, gdy:

1. package name jest taki sam,
2. `versionCode` jest wyższy,
3. APK jest podpisane tym samym kluczem.

Dlatego dla prawdziwych aktualizacji używaj podpisanego `release APK`, nie losowego `debug APK`.

## Jak ustawić podpisywanie release APK

Uruchom:

```bat
tools\create-release-keystore.bat
```

Potem dodaj w GitHub repo w `Settings > Secrets and variables > Actions`:

```text
SIGNING_KEYSTORE_BASE64
SIGNING_STORE_PASSWORD
SIGNING_KEY_ALIAS
SIGNING_KEY_PASSWORD
```

Po tym GitHub Actions będzie budować podpisane release APK.

## Jak opublikować release do updatera

Po commicie v3.4 i ustawieniu sekretów:

```cmd
git tag v3.4
git push origin v3.4
```

Workflow utworzy GitHub Release z APK. Przycisk `Sprawdź aktualizację` w aplikacji będzie sprawdzał najnowszy release w repo:

```text
AbcITAndrzej/apk-android
```

Jeśli repo jest prywatne, updater nie pobierze release bez tokena. Do publicznego updatera repo albo release musi być publiczne.

## Autostart

Autostart działa tylko po włączeniu opcji w aplikacji. Po starcie TV aplikacja czeka około 15 sekund, potem próbuje uruchomić DNS VPN z obecnymi ustawieniami albo wybranym profilem. Pierwsza zgoda VPN musi być wcześniej zaakceptowana ręcznie.

## Always-on VPN

Always-on VPN ustawia użytkownik w systemowych ustawieniach Androida. Aplikacja ma przycisk do otwarcia ustawień VPN, ale nie wymusza tego po cichu.
