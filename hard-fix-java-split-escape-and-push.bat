@echo off
setlocal EnableExtensions
chcp 65001 >nul
title HARD FIX Java split escape - AdGuard TV DNS

set "REPO_DIR=C:\Users\DELL\Downloads\adguard-tv-dns-project\adguard-tv-dns"
set "JAVA_FILE=app\src\main\java\pl\abcit\adguardtvdns\MainActivity.java"

echo ===============================================
echo   HARD FIX: value.split("\.") -> value.split("\\.")
echo ===============================================
echo.

cd /d "%REPO_DIR%" || (
  echo [BLAD] Nie moge wejsc do folderu repo.
  pause
  exit /b 1
)

if not exist "%JAVA_FILE%" (
  echo [BLAD] Nie znaleziono %JAVA_FILE%
  pause
  exit /b 1
)

echo [1] Linie PRZED poprawka:
findstr /n /c:"value.split" "%JAVA_FILE%"
echo.

echo [2] Poprawiam plik przez PowerShell...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$p='app/src/main/java/pl/abcit/adguardtvdns/MainActivity.java'; $s=[System.IO.File]::ReadAllText($p); $s=$s.Replace('split(\" + [char]34 + '\.' + [char]34 + \")','split(\" + [char]34 + '\\.' + [char]34 + \")'); $s=$s -replace 'split\(\"\\\.\"\)', 'split(\"\\\\.\"\)'; [System.IO.File]::WriteAllText($p,$s,[System.Text.UTF8Encoding]::new($false))"

echo.
echo [3] Linie PO poprawce:
findstr /n /c:"value.split" "%JAVA_FILE%"
echo.

echo [4] Test: czy nadal jest zla linia?
findstr /c:"value.split(\"\.\")" "%JAVA_FILE%" >nul
if not errorlevel 1 (
  echo.
  echo [BLAD] Zla linia nadal istnieje. Otworz plik recznie:
  echo notepad "%CD%\%JAVA_FILE%"
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

echo OK: zla linia nie jest juz wykrywana.
echo.
git diff -- "%JAVA_FILE%"
echo.

set /p "DO_COMMIT=Zrobic commit i push? [T/N]: "
if /I not "%DO_COMMIT%"=="T" (
  echo Przerwano bez commita.
  pause
  exit /b 0
)

git add -A
git commit -m "Hard fix Java split regex escape"
git push

echo.
echo Gotowe. Sprawdz build:
echo gh run list -R AbcITAndrzej/apk-android --limit 5
echo.
pause
