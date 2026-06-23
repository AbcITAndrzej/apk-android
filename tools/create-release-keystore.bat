@echo off
setlocal EnableExtensions
chcp 65001 >nul

echo ===============================================
echo  AdGuard TV DNS Pro - release signing keystore
echo ===============================================
echo.
echo Ten plik tworzy staly klucz do podpisywania APK.
echo Zachowaj release.keystore i hasla. Bez tego Android nie przyjmie aktualizacji jako tej samej aplikacji.
echo.

set "OUTDIR=%USERPROFILE%\Desktop\adguard-release-signing"
if not exist "%OUTDIR%" mkdir "%OUTDIR%"
cd /d "%OUTDIR%"

set /p "STOREPASS=Haslo keystore: "
set /p "KEYPASS=Haslo klucza: "
set "ALIAS=adguardtv"

where keytool >nul 2>nul
if errorlevel 1 (
  echo.
  echo [BLAD] Nie znaleziono keytool. Zainstaluj JDK albo uzyj komputera z Java JDK.
  pause
  exit /b 1
)

if exist release.keystore (
  echo.
  echo [INFO] release.keystore juz istnieje w %OUTDIR%
  echo Nie nadpisuje klucza, bo wtedy aktualizacje przestana pasowac.
) else (
  keytool -genkeypair -v -keystore release.keystore -alias %ALIAS% -keyalg RSA -keysize 2048 -validity 10000 -storepass "%STOREPASS%" -keypass "%KEYPASS%" -dname "CN=AdGuard TV DNS Pro, OU=AbcIT, O=AbcIT, L=PL, S=PL, C=PL"
)

powershell -NoProfile -Command "[Convert]::ToBase64String([IO.File]::ReadAllBytes('release.keystore')) | Set-Content -Path 'release-keystore-base64.txt' -Encoding ascii"

echo.
echo Gotowe. Folder:
echo %OUTDIR%
echo.
echo W GitHub repo dodaj Secrets ^> Actions:
echo SIGNING_KEYSTORE_BASE64 = zawartosc release-keystore-base64.txt
echo SIGNING_STORE_PASSWORD  = %STOREPASS%
echo SIGNING_KEY_ALIAS       = %ALIAS%
echo SIGNING_KEY_PASSWORD    = %KEYPASS%
echo.
echo NIE wrzucaj release.keystore do repo.
echo.
pause
