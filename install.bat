@echo off
rem =========================================================
rem  TV Launcher : build + install + launch  (double click)
rem  Uses %~dp0 so the non-ASCII folder name never appears
rem  inside this script (cmd reads .bat in OEM codepage).
rem =========================================================
chcp 65001 >nul
setlocal

set PROJ=%~dp0
set JAVA_HOME=D:\android\jbr
set GRADLE_USER_HOME=%PROJ%..\.gradlehome
set GRADLE_BIN=D:\gradle_home\wrapper\dists\gradle-8.14.5-bin\690y85m0j9nfaub7xoiayko8a\gradle-8.14.5\bin\gradle.bat
set ADB=D:\android\sdk\platform-tools\adb.exe
set APK=%PROJ%app\build\outputs\apk\debug\app-debug.apk
set PATH=%JAVA_HOME%\bin;%PATH%

cd /d "%PROJ%" || goto fail

echo.
echo [1/4] cleaning stale gradle native locks ...
del /q /s "%GRADLE_USER_HOME%\native\*.lock" >nul 2>&1

echo [2/4] building debug apk ...
call "%GRADLE_BIN%" assembleDebug
if errorlevel 1 goto fail
if not exist "%APK%" goto fail

echo.
echo [3/4] installing to device ...
"%ADB%" devices
"%ADB%" install -r -t "%APK%"
if errorlevel 1 goto fail

echo.
echo [4/4] launching ...
"%ADB%" shell am force-stop com.liusheng.tvlauncher
"%ADB%" shell am start -n com.liusheng.tvlauncher/.MainActivity

echo.
echo ============================================
echo   DONE - the launcher should be on screen.
echo ============================================
pause
exit /b 0

:fail
echo.
echo ============================================
echo   FAILED - please send the text above back
echo   to the assistant so it can fix it.
echo ============================================
pause
exit /b 1
