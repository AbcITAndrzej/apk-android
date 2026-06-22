# AdGuard TV DNS Pro

Lekka aplikacja Android TV do ustawienia DNS przez lokalny `VpnService`.

## Funkcje

- START / STOP jako lokalny VPN tylko dla DNS.
- Domyślny AdGuard DNS: `94.140.14.14` i `94.140.15.15`.
- Możliwość wpisania własnych DNS IPv4.
- Tryb: wszystkie aplikacje.
- Tryb: tylko zaznaczone aplikacje.
- Lista aplikacji z wyszukiwarką.
- Przyciski: zaznacz widoczne, odznacz widoczne, zaznacz wszystkie, odznacz wszystkie.
- Panel debug: status, tryb, aktywny DNS, liczniki pakietów, zapytań, odpowiedzi, błędów, ostatnia domena, ostatni błąd.
- Log zdarzeń z opcją kopiowania i czyszczenia.
- Brak reklam, brak analityki, brak zewnętrznych bibliotek.

## Ważne

Aplikacja nie modyfikuje systemowego DNS w ustawieniach Androida. Działa profesjonalniej i bez roota: uruchamia lokalny `VpnService`, ustawia wirtualny DNS i przekazuje zapytania DNS do serwerów AdGuard albo własnych.

Jeśli jakaś aplikacja używa własnego DNS-over-HTTPS albo twardo wpisanych serwerów, może ominąć zwykły DNS systemowy. To ograniczenie Androida/aplikacji, nie błąd projektu.

## Build na GitHub Actions

Po wrzuceniu projektu do repozytorium GitHub:

1. Wejdź w **Actions**.
2. Otwórz **Build APK**.
3. Kliknij **Run workflow** albo zrób push na `main`.
4. Pobierz artifact `adguard-tv-dns-debug-apk`.
5. Rozpakuj ZIP i zainstaluj `.apk` na Android TV.
