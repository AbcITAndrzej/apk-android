# AdGuard TV DNS Pro v3

Lekka aplikacja Android / Android TV do ustawiania DNS przez lokalny `VpnService`.

## Co dodano w v3

- ekran Home podobny do aplikacji DNS Changer: aktualny serwer, skróty Apps / Servers / Logs, przełącznik OFF / ON,
- osobny ekran serwerów DNS z presetami i własnym DNS,
- osobny ekran aplikacji z ikonami, wyszukiwarką, trybem wszystkie aplikacje / wybrane aplikacje,
- widoczny focus pod pilota TV: jasna ramka i lekkie powiększenie elementu,
- przycisk Keyboard, żeby wymusić klawiaturę ekranową na Android TV,
- logi grupowane: SYSTEM, wszystkie aplikacje, grupa wybranych aplikacji, albo dokładna aplikacja gdy wybrana jest jedna,
- tryb oszczędny debug ograniczający logowanie i liczbę workerów,
- podstawowe tłumaczenia PL / EN / DE / FR / NL oraz szkielety locale dla 30 języków/europejskich regionów,
- brak reklam, brak analityki, brak zewnętrznych SDK.

## Ważne ograniczenie techniczne

Android `VpnService` nie podaje wprost pakietu aplikacji dla każdego pojedynczego pakietu DNS. Dlatego pełne, dokładne logowanie per aplikacja jest wiarygodne wtedy, gdy VPN działa tylko dla jednej wybranej aplikacji. Dla trybu wszystkich aplikacji logi DNS są grupowane jako wszystkie / nieznana aplikacja.

## Build przez GitHub Actions

Po wrzuceniu plików do repozytorium GitHub uruchom `Actions -> Build APK`. Artifact będzie miał nazwę:

`adguard-tv-dns-pro-v3-debug-apk`

## Domyślny DNS

- AdGuard DNS: `94.140.14.14`, `94.140.15.15`

## Test po instalacji

1. Uruchom aplikację.
2. Wybierz serwer DNS.
3. Ustaw tryb: wszystkie aplikacje albo wybrane aplikacje.
4. Kliknij ON / CONNECT.
5. Zaakceptuj zgodę Android VPN.
6. Wejdź w Logs i sprawdź, czy rosną `queries` i `responses`.


## v3.2

- sortowanie aplikacji: najpierw DNS ON, potem DNS OFF
- filtry: wszystkie / aplikacje z DNS / aplikacje bez DNS
- profile: zapis aktualnego DNS + zakresu aplikacji
- szybkie profile aplikacji z ekranu Start
- eksport/import ustawień do pliku JSON

Uwaga: Android pozwala mieć jeden aktywny profil VPN naraz. Różne DNS dla różnych aplikacji jednocześnie nie są niezawodne bez roota/systemowych uprawnień, bo zwykły pakiet DNS nie zawiera nazwy aplikacji.
