@echo off
chcp 65001 >nul
setlocal EnableExtensions EnableDelayedExpansion

title AdGuard TV DNS - Git Helper

set "DEFAULT_DIR=C:\Users\DELL\Downloads\adguard-tv-dns-project\adguard-tv-dns"
set "REPO_URL=https://github.com/AbcITAndrzej/apk-android"
set "ACTIONS_URL=https://github.com/AbcITAndrzej/apk-android/actions"

:START
cls
echo ============================================================
echo   AdGuard TV DNS - Git Helper
echo ============================================================
echo.
echo Domyslny folder projektu:
echo %DEFAULT_DIR%
echo.
set "PROJECT_DIR="
set /p "PROJECT_DIR=Wpisz folder projektu albo nacisnij ENTER dla domyslnego: "
if "%PROJECT_DIR%"=="" set "PROJECT_DIR=%DEFAULT_DIR%"

if not exist "%PROJECT_DIR%" (
    echo.
    echo BLAD: Taki folder nie istnieje:
    echo %PROJECT_DIR%
    echo.
    pause
    goto START
)

pushd "%PROJECT_DIR%" >nul 2>nul
if errorlevel 1 (
    echo.
    echo BLAD: Nie moge wejsc do folderu:
    echo %PROJECT_DIR%
    echo.
    pause
    goto START
)

if not exist ".git" (
    echo.
    echo UWAGA: W tym folderze nie ma repozytorium Git .git
    echo Folder: %CD%
    echo.
    choice /C TN /N /M "Czy zrobic git init i ustawic origin? [T/N]: "
    if errorlevel 2 goto MENU
    git init
    git branch -M main
    git remote add origin %REPO_URL%.git
)

:MENU
cls
echo ============================================================
echo   AdGuard TV DNS - Git Helper
echo ============================================================
echo Folder:
echo %CD%
echo.
for /f "delims=" %%B in ('git branch --show-current 2^>nul') do set "BRANCH=%%B"
if "%BRANCH%"=="" set "BRANCH=main"
echo Branch: %BRANCH%
echo.
echo Opcje:
echo.
echo   1 - Pokaz status Git
echo   2 - Szybka aktualizacja: git add -A + commit + push
echo   3 - Tylko push
echo   4 - Pobierz zmiany z GitHuba: git pull origin main
echo   5 - Usun ZIP/APK z katalogu glownego repo i przygotuj commit
echo   6 - Pokaz ostatnie commity
echo   7 - Otworz repozytorium w przegladarce
echo   8 - Otworz GitHub Actions w przegladarce
echo   9 - Zmien folder projektu
echo   0 - Wyjscie
echo.
set "OP="
set /p "OP=Wybierz opcje: "

if "%OP%"=="1" goto STATUS
if "%OP%"=="2" goto QUICK_UPDATE
if "%OP%"=="3" goto PUSH_ONLY
if "%OP%"=="4" goto PULL
if "%OP%"=="5" goto CLEAN_ARTIFACTS
if "%OP%"=="6" goto LOGS
if "%OP%"=="7" goto OPEN_REPO
if "%OP%"=="8" goto OPEN_ACTIONS
if "%OP%"=="9" goto CHANGE_DIR
if "%OP%"=="0" goto END

echo.
echo Nieznana opcja.
pause
goto MENU

:STATUS
cls
echo ============================================================
echo   STATUS GIT
echo ============================================================
echo.
git status
echo.
echo Pliki zmienione/skrocone:
git status --short
echo.
pause
goto MENU

:QUICK_UPDATE
cls
echo ============================================================
echo   SZYBKA AKTUALIZACJA: ADD + COMMIT + PUSH
echo ============================================================
echo.
echo Aktualny status:
git status --short
echo.
set "MSG="
set /p "MSG=Opis commita. ENTER = Update project: "
if "%MSG%"=="" set "MSG=Update project"

echo.
echo Dodaje wszystkie zmiany...
git add -A

git diff --cached --quiet
if not errorlevel 1 (
    echo.
    echo Brak zmian do commita. Nic nie wysylam.
    echo.
    pause
    goto MENU
)

echo.
echo Tworze commit: "%MSG%"
git commit -m "%MSG%"
if errorlevel 1 (
    echo.
    echo BLAD przy commit. Sprawdz komunikat wyzej.
    echo.
    pause
    goto MENU
)

echo.
echo Wysylam na GitHub...
git push
if errorlevel 1 (
    echo.
    echo BLAD przy push. Mozliwe logowanie/token albo konflikt z GitHubem.
    echo.
    pause
    goto MENU
)

echo.
echo GOTOWE. Wejdz w GitHub Actions i sprawdz build APK.
echo %ACTIONS_URL%
echo.
pause
goto MENU

:PUSH_ONLY
cls
echo ============================================================
echo   TYLKO PUSH
echo ============================================================
echo.
git push
echo.
pause
goto MENU

:PULL
cls
echo ============================================================
echo   POBIERZ ZMIANY Z GITHUBA
echo ============================================================
echo.
echo Robie: git pull origin main
echo.
git pull origin main
echo.
pause
goto MENU

:CLEAN_ARTIFACTS
cls
echo ============================================================
echo   CZYSZCZENIE ZIP/APK Z KATALOGU GLOWNEGO
echo ============================================================
echo.
echo To usuwa tylko pliki *.zip i *.apk z GLOWNEGO folderu repo.
echo Nie usuwa kodu z app/ ani .github/
echo.
echo Znalezione pliki:
dir /b *.zip *.apk 2>nul
echo.
choice /C TN /N /M "Usunac te pliki z dysku i z Gita? [T/N]: "
if errorlevel 2 goto MENU

echo.
echo Usuwam z Git, jesli byly sledzone...
git rm -f -- *.zip *.apk 2>nul

echo Usuwam z dysku, jesli byly niesledzone...
del /f /q *.zip *.apk 2>nul

echo.
echo Status po czyszczeniu:
git status --short
echo.
echo Teraz mozesz uzyc opcji 2, zeby zrobic commit + push sprzatania.
echo.
pause
goto MENU

:LOGS
cls
echo ============================================================
echo   OSTATNIE COMMITY
echo ============================================================
echo.
git --no-pager log --oneline --decorate -10
echo.
pause
goto MENU

:OPEN_REPO
start "" "%REPO_URL%"
goto MENU

:OPEN_ACTIONS
start "" "%ACTIONS_URL%"
goto MENU

:CHANGE_DIR
popd >nul 2>nul
goto START

:END
popd >nul 2>nul
echo.
echo Koniec.
endlocal
exit /b 0
