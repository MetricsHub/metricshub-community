@echo off
setlocal EnableDelayedExpansion

rem ============================================================
rem  build-windows.cmd
rem  Local Windows build script (no signing, no uploads)
rem ============================================================

rem --- Setup paths ---
set BASE_DIR=%~dp0%
set BUILD_DIR=%BASE_DIR%\..
set PROJECT_DIR=%BUILD_DIR%\..\..

rem --- Setup ANSI colors (blue for steps, red for errors) ---
for /F "delims=" %%A in ('echo prompt $E^| cmd') do set "ESC=%%A"
set "BLUE=%ESC%[36m"
set "GREEN=%ESC%[32m"
set "RED=%ESC%[31m"
set "RESET=%ESC%[0m"

set "ROOT_DIR=%CD%"

call :info "Starting Windows installer build process"

rem ------------------------------------------------------------
rem Step 1: Check Java / JDK (analog to 'Set up JDK')
rem ------------------------------------------------------------
call :step "Checking Java / JDK"
java --version >"%TEMP%\java_version.txt" 2>&1
if errorlevel 1 (
    call :fail "Java (JDK) not found on PATH. Please install JDK @jdkBuildVersion@+ and ensure 'java' is on PATH."
    goto :enderror
)

set "JAVA_VERSION_LINE="
for /f "tokens=1,*" %%A in (%TEMP%\java_version.txt) do (
    if not defined JAVA_VERSION_LINE (
        set "JAVA_VERSION_LINE=%%B"
    )
)

rem Extract the major version number
rem Example version lines:
rem   openjdk version "21.0.2"  → extract 21
rem   openjdk version "23"      → extract 23
for /f "tokens=1 delims=." %%A in ("%JAVA_VERSION_LINE:"=%") do (
    set "JAVA_MAJOR=%%A"
)

rem Validate numeric and >= @jdkBuildVersion@
echo %JAVA_MAJOR% | findstr /R "[^0-9]" >nul
if errorlevel 1 (
    call :fail "Unable to parse Java version from: %JAVA_VERSION_LINE%"
    goto :enderror
)

if %JAVA_MAJOR% LSS @jdkBuildVersion@ (
    call :fail "Java version %JAVA_MAJOR% detected. JDK @jdkBuildVersion@ or newer is required."
    goto :enderror
)

call :info "Java version %JAVA_MAJOR% detected - OK."

rem ------------------------------------------------------------
rem Step 2: Prepare packaging-assets directory
rem (analog to 'Download Assets' + 'Get Windows Assets')
rem ------------------------------------------------------------
call :step "Preparing packaging assets"

if not exist "%BUILD_DIR%\assets-windows" (
    call :fail "Unable to find Windows assets in the Maven build directory."
    goto :enderror
)

call :info "Found asset directory: %BUILD_DIR%\assets-windows"

if exist "%ROOT_DIR%\packages" (
    del /Q /F /S "%ROOT_DIR%\packages\*.msi" >nul 2>&1
    rd /S /Q "%ROOT_DIR%\packages\MetricsHub" >nul 2>&1
    if errorlevel 1 (
        call :fail "Unable to remove existing '%ROOT_DIR%\packages' directory."
        goto :enderror
    )
    call :info "Removed existing packages directory: %ROOT_DIR%\packages"
)

rem ------------------------------------------------------------
rem Step 3: Create JRE for Windows (jlink)
rem ------------------------------------------------------------
call :step "Creating custom JRE for Windows via jlink"

call %BASE_DIR%\create-jre.cmd
if errorlevel 1 (
    call :fail "JRE creation script (create-jre.cmd) failed."
    goto :enderror
)

rem ------------------------------------------------------------
rem Step 4: Display WiX Toolset Version
rem ------------------------------------------------------------
call :step "Checking WiX Toolset (candle.exe / light.exe)"

candle.exe -? >nul 2>&1
if errorlevel 1 (
    call :fail "candle.exe not found. Install WiX Toolset and ensure it's on PATH."
    goto :enderror
)

light.exe -? >nul 2>&1
if errorlevel 1 (
    call :fail "light.exe not found. Install WiX Toolset and ensure it's on PATH."
    goto :enderror
)

rem ------------------------------------------------------------
rem Step 5: Run Windows Packaging Script (jpackage wrapper)
rem ------------------------------------------------------------
call :step "Running Windows packaging script"

if not exist "%ROOT_DIR%\packages" (
    mkdir "%ROOT_DIR%\packages"
    if errorlevel 1 (
        call :fail "Unable to create '%ROOT_DIR%\packages' directory."
        goto :enderror
    )
)

if not exist "%BUILD_DIR%\assets-windows\jpackage\package.cmd" (
    call :fail "Missing '%BUILD_DIR%\assets-windows\jpackage\package.cmd'."
    goto :enderror
)

pushd "%BUILD_DIR%\assets-windows\jpackage"
call package.cmd "%BUILD_DIR%\assets-local\jre-windows" "%ROOT_DIR%\packages"
set "PKG_EXIT=%ERRORLEVEL%"
popd

if not "%PKG_EXIT%"=="0" (
    call :fail "Packaging script (package.cmd) failed with exit code %PKG_EXIT%."
    goto :enderror
)

call :info "Resulting packages:"
dir "%ROOT_DIR%\packages\*.msi"

rem ------------------------------------------------------------
rem Done
rem ------------------------------------------------------------
call :info "Windows packaging completed successfully (no signing)."
endlocal
exit /b 0


rem ============================================================
rem Helper routines
rem ============================================================

:step
echo [%GREEN%STEP%RESET%]  %*
goto :eof

:info
echo [%BLUE%INFO%RESET%]  %*
goto :eof

:fail
echo [%RED%ERROR%RESET%] %*
goto :eof

:enderror
endlocal
exit /b 1
