# Release signing

Aktualizacje APK działają poprawnie tylko wtedy, gdy każda kolejna wersja jest podpisana tym samym kluczem.

1. Uruchom `tools/create-release-keystore.bat` na Windowsie.
2. Zapisz `release.keystore`, hasło keystore i hasło klucza w bezpiecznym miejscu.
3. W GitHub repo dodaj sekrety w `Settings > Secrets and variables > Actions`:

- `SIGNING_KEYSTORE_BASE64`
- `SIGNING_STORE_PASSWORD`
- `SIGNING_KEY_ALIAS`
- `SIGNING_KEY_PASSWORD`

Po dodaniu sekretów GitHub Actions zbuduje podpisane release APK. Po utworzeniu taga, np. `v3.4`, workflow opublikuje APK w GitHub Releases.
