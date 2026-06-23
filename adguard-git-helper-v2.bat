@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul
title AdGuard TV DNS - GitHub Helper v2

set "DEFAULT_REPO_DIR=C:\Users\DELL\Downloads\adguard-tv-dns-project\adguard-tv-dns"
set "REPO=AbcITAndrzej/apk-android"
set "BRANCH=main"
set "OUTDIR=%USERPROFILE%\Downloads\AdGuard-TV-DNS-APK"

echo.
echo ===============================================
echo   AdGuard TV DNS - GitHub Helper v2
echo ===============================================
echo.

set /p "REPO_DIR=Folder projektu [%DEFAULT_REPO_DIR%]: "
if "%REPO_DIR%"=="" set "REPO_DIR=%DEFAULT_REPO_DIR%"

if not exist "%REPO_DIR%\.git" (
  echo.
  echo [BLAD] To nie wyglada jak folder Git:
  echo %REPO_DIR%
  echo.
  pause
  exit /b 1
)

cd /d "%REPO_DIR%"

:MENU
cls
echo ===============================================
echo   AdGuard TV DNS - GitHub Helper v2
echo ===============================================
echo Repo lokalne: %CD%
echo Repo GitHub : %REPO%
echo APK folder  : %OUTDIR%
echo ===============================================
echo.
echo  1 - Status Git
echo  2 - Szybka aktualizacja: add + commit + push
echo  3 - Tylko push
echo  4 - Pull z GitHub
echo  5 - Sprzatanie ZIP/APK z glownego folderu repo
echo  6 - Ostatnie commity
echo  7 - Otworz repo w przegladarce
echo  8 - Otworz GitHub Actions
echo  9 - Sprawdz ostatni build Actions
echo 10 - Czekaj na ostatni build Actions
echo 11 - Pobierz najnowszy APK z Actions
echo 12 - Pobierz APK po zakonczeniu builda
echo 13 - Sprawdz logowanie GitHub CLI
echo 14 - Zainstaluj GitHub CLI przez winget
echo  0 - Wyjscie
echo.
set /p "OPT=Wybierz opcje: "

if "%OPT%"=="1" goto STATUS
if "%OPT%"=="2" goto UPDATE
if "%OPT%"=="3" goto PUSH
if "%OPT%"=="4" goto PULL
if "%OPT%"=="5" goto CLEAN
if "%OPT%"=="6" goto LOG
if "%OPT%"=="7" goto OPEN_REPO
if "%OPT%"=="8" goto OPEN_ACTIONS
if "%OPT%"=="9" goto CHECK_RUN
if "%OPT%"=="10" goto WATCH_RUN
if "%OPT%"=="11" goto DOWNLOAD_APK
if "%OPT%"=="12" goto WATCH_AND_DOWNLOAD
if "%OPT%"=="13" goto GH_AUTH
if "%OPT%"=="14" goto INSTALL_GH
if "%OPT%"=="0" exit /b 0
goto MENU

:STATUS
cls
git status
echo.
pause
goto MENU

:UPDATE
cls
echo Szybka aktualizacja: git add -A + commit + push
echo.
git status --short
echo.
set /p "MSG=Opis commita [Update app]: "
if "%MSG%"=="" set "MSG=Update app"
git add -A
git commit -m "%MSG%"
if errorlevel 1 (
  echo.
  echo [INFO] Commit mogl sie nie wykonac, np. brak zmian. Sprobuj push mimo to.
)
git push
echo.
pause
goto MENU

:PUSH
cls
git push
echo.
pause
goto MENU

:PULL
cls
git pull origin %BRANCH%
echo.
pause
goto MENU

:CLEAN
cls
echo Usuwanie przypadkowych ZIP/APK z glownego folderu repo...
echo Nie usuwa plikow z folderu app ani .github.
echo.
del /q "*.zip" 2>nul
del /q "*.apk" 2>nul
git add -A
git status --short
echo.
set /p "DO_COMMIT=Zrobic commit sprzatajacy? [T/N]: "
if /I "%DO_COMMIT%"=="T" (
  git commit -m "Clean generated APK ZIP files"
  git push
)
echo.
pause
goto MENU

:LOG
cls
git log --oneline -10
echo.
pause
goto MENU

:OPEN_REPO
start "" "https://github.com/%REPO%"
goto MENU

:OPEN_ACTIONS
start "" "https://github.com/%REPO%/actions"
goto MENU

:CHECK_GH
where gh >nul 2>nul
if errorlevel 1 (
  echo.
  echo [BLAD] Nie znaleziono GitHub CLI: gh
  echo Zainstaluj opcja 14 albo wpisz:
  echo winget install --id GitHub.cli
  echo.
  pause
  goto MENU
)
exit /b 0

:GH_AUTH
cls
call :CHECK_GH
gh auth status
echo.
echo Jesli nie jestes zalogowany, wpisz pozniej:
echo gh auth login
echo.
pause
goto MENU

:CHECK_RUN
cls
call :CHECK_GH
echo Ostatni workflow run:
echo.
gh run list -R %REPO% --branch %BRANCH% --limit 3
echo.
pause
goto MENU

:WATCH_RUN
cls
call :CHECK_GH
for /f "usebackq delims=" %%I in (`gh run list -R %REPO% --branch %BRANCH% --limit 1 --json databaseId --jq ".[0].databaseId"`) do set "RUN_ID=%%I"
if "%RUN_ID%"=="" (
  echo [BLAD] Nie udalo sie znalezc ostatniego builda.
  pause
  goto MENU
)
echo Czekam na build ID: %RUN_ID%
echo.
gh run watch %RUN_ID% -R %REPO% --exit-status
echo.
pause
goto MENU

:DOWNLOAD_APK
cls
call :CHECK_GH
if not exist "%OUTDIR%" mkdir "%OUTDIR%"
for /f "usebackq delims=" %%I in (`gh run list -R %REPO% --branch %BRANCH% --limit 1 --json databaseId --jq ".[0].databaseId"`) do set "RUN_ID=%%I"
if "%RUN_ID%"=="" (
  echo [BLAD] Nie udalo sie znalezc ostatniego builda.
  pause
  goto MENU
)

echo Pobieram artifact APK z builda ID: %RUN_ID%
echo Do folderu: %OUTDIR%
echo.
gh run download %RUN_ID% -R %REPO% -p "*apk*" -D "%OUTDIR%"
if errorlevel 1 (
  echo.
  echo [BLAD] Pobieranie nie wyszlo. Sprawdz czy build jest zielony i czy artifact istnieje.
  pause
  goto MENU
)

echo.
echo Gotowe. Szukam plikow APK:
dir /s /b "%OUTDIR%\*.apk"
echo.
start "" "%OUTDIR%"
pause
goto MENU

:WATCH_AND_DOWNLOAD
cls
call :CHECK_GH
for /f "usebackq delims=" %%I in (`gh run list -R %REPO% --branch %BRANCH% --limit 1 --json databaseId --jq ".[0].databaseId"`) do set "RUN_ID=%%I"
if "%RUN_ID%"=="" (
  echo [BLAD] Nie udalo sie znalezc ostatniego builda.
  pause
  goto MENU
)

echo Czekam na build ID: %RUN_ID%
echo.
gh run watch %RUN_ID% -R %REPO% --exit-status
if errorlevel 1 (
  echo.
  echo [BLAD] Build zakonczyl sie bledem. Nie pobieram APK.
  pause
  goto MENU
)

if not exist "%OUTDIR%" mkdir "%OUTDIR%"
echo.
echo Build OK. Pobieram APK...
gh run download %RUN_ID% -R %REPO% -p "*apk*" -D "%OUTDIR%"
echo.
echo Pliki APK:
dir /s /b "%OUTDIR%\*.apk"
start "" "%OUTDIR%"
pause
goto MENU

:INSTALL_GH
cls
echo Instalacja GitHub CLI przez winget...
echo.
winget install --id GitHub.cli
echo.
echo Po instalacji moze trzeba zamknac i otworzyc BAT ponownie.
echo Potem zaloguj sie komenda:
echo gh auth login
echo.
pause
goto MENU
