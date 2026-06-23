@echo off
setlocal EnableExtensions
chcp 65001 >nul
title SAFE FIX Java split escape - AdGuard TV DNS

set "REPO_DIR=C:\Users\DELL\Downloads\adguard-tv-dns-project\adguard-tv-dns"
set "JAVA_FILE=%REPO_DIR%\app\src\main\java\pl\abcit\adguardtvdns\MainActivity.java"
set "PS1=%TEMP%\fix_adguard_split_escape.ps1"

echo ===============================================
echo   SAFE FIX: value.split("\.") -> value.split("\\.")
echo ===============================================
echo.

if not exist "%REPO_DIR%\.git" (
  echo [BLAD] Nie znaleziono repo Git:
  echo %REPO_DIR%
  pause
  exit /b 1
)

if not exist "%JAVA_FILE%" (
  echo [BLAD] Nie znaleziono pliku:
  echo %JAVA_FILE%
  pause
  exit /b 1
)

cd /d "%REPO_DIR%"

echo [1] Linie PRZED poprawka:
findstr /n /c:"value.split" "%JAVA_FILE%"
echo.

echo [2] Tworze maly skrypt PowerShell...
> "%PS1%" echo $path = "C:\Users\DELL\Downloads\adguard-tv-dns-project\adguard-tv-dns\app\src\main\java\pl\abcit\adguardtvdns\MainActivity.java"
>> "%PS1%" echo $text = [System.IO.File]::ReadAllText($path)
>> "%PS1%" echo $text = $text.Replace('value.split("\.")', 'value.split("\\.")')
>> "%PS1%" echo $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
>> "%PS1%" echo [System.IO.File]::WriteAllText($path, $text, $utf8NoBom)

echo [3] Odpalam poprawke...
powershell -NoProfile -ExecutionPolicy Bypass -File "%PS1%"
if errorlevel 1 (
  echo [BLAD] PowerShell nie wykonal poprawki.
  pause
  exit /b 1
)

echo.
echo [4] Linie PO poprawce:
findstr /n /c:"value.split" "%JAVA_FILE%"
echo.

findstr /c:"value.split(\"\.\")" "%JAVA_FILE%" >nul
if not errorlevel 1 (
  echo.
  echo [BLAD] Zla linia nadal istnieje.
  echo Otworz recznie:
  echo notepad "%JAVA_FILE%"
  echo.
  echo Znajdz:
  echo value.split("\.")
  echo.
  echo Zamien na:
  echo value.split("\\.")
  echo.
  pause
  exit /b 1
)

echo OK: zla linia zostala poprawiona.
echo.
echo [5] Git diff:
git diff -- "app\src\main\java\pl\abcit\adguardtvdns\MainActivity.java"
echo.

set /p "DO_COMMIT=Zrobic commit i push? [T/N]: "
if /I not "%DO_COMMIT%"=="T" (
  echo Przerwano bez commita.
  pause
  exit /b 0
)

git add -A
git commit -m "Safe fix Java split regex escape"
if errorlevel 1 (
  echo.
  echo [INFO] Commit mogl sie nie wykonac, np. brak zmian.
)

git push

echo.
echo Gotowe. Teraz sprawdz:
echo gh run list -R AbcITAndrzej/apk-android --limit 5
echo.
pause
