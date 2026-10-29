@echo off
setlocal
cd /d "%~dp0"
set "GRADLE_VERSION=9.6.0"
set "BOOT=%~dp0.gradle-bootstrap"
set "GRADLE_HOME=%BOOT%\gradle-%GRADLE_VERSION%"

where java >nul 2>nul || (
  echo Java was not found. Minecraft 26.2 needs Java 25 or newer.
  exit /b 1
)

if not exist "%GRADLE_HOME%\bin\gradle.bat" (
  echo Downloading Gradle %GRADLE_VERSION%...
  if not exist "%BOOT%" mkdir "%BOOT%"
  curl.exe -L --fail --retry 3 -o "%BOOT%\gradle.zip" "https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip" || exit /b 1
  tar.exe -xf "%BOOT%\gradle.zip" -C "%BOOT%" || exit /b 1
)

call "%GRADLE_HOME%\bin\gradle.bat" --no-daemon clean build
if errorlevel 1 exit /b %errorlevel%
echo.
echo Build complete. Check build\libs\
endlocal
