@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul
title Fix Java escape - AdGuard TV DNS

set "REPO_DIR=C:\Users\DELL\Downloads\adguard-tv-dns-project\adguard-tv-dns"
set "JAVA_FILE=app\src\main\java\pl\abcit\adguardtvdns\MainActivity.java"

echo ===============================================
echo   Fix Java escape MainActivity.java
echo ===============================================
echo.

if not exist "%REPO_DIR%\.git" (
  echo [BLAD] Nie znaleziono repo Git:
  echo %REPO_DIR%
  pause
  exit /b 1
)

cd /d "%REPO_DIR%"

if not exist "%JAVA_FILE%" (
  echo [BLAD] Nie znaleziono pliku:
  echo %JAVA_FILE%
  pause
  exit /b 1
)

echo [1/4] Poprawiam value.split("\.") na value.split("\\.")...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$p='app/src/main/java/pl/abcit/adguardtvdns/MainActivity.java'; $s=Get-Content $p -Raw; $s=$s.Replace('value.split(' + [char]34 + '\.' + [char]34 + ')','value.split(' + [char]34 + '\\.' + [char]34 + ')'); Set-Content $p $s -NoNewline"

echo.
echo [2/4] Sprawdzam linie z value.split:
powershell -NoProfile -ExecutionPolicy Bypass -Command "Select-String -Path 'app/src/main/java/pl/abcit/adguardtvdns/MainActivity.java' -Pattern 'value.split' | ForEach-Object { $_.LineNumber.ToString() + ': ' + $_.Line }"

echo.
echo [3/4] Git status:
git status --short

echo.
set /p "DO_PUSH=Zrobic commit i push? [T/N]: "
if /I not "%DO_PUSH%"=="T" (
  echo Przerwano bez commita.
  pause
  exit /b 0
)

git add -A
git commit -m "Fix Java split escape"
if errorlevel 1 (
  echo.
  echo [INFO] Commit mogl sie nie wykonac, np. brak zmian.
)

echo.
echo [4/4] Push do GitHuba...
git push

echo.
echo Gotowe. Teraz sprawdz:
echo gh run list -R AbcITAndrzej/apk-android --limit 5
echo.
pause
