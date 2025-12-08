@echo off
setlocal EnableDelayedExpansion

rem ============================================================
rem  create-jre.cmd
rem  Local JRE creation script for Windows builds
rem ============================================================

set JAVA_DOCKER_VERSION=@metricshub-jre.version@
REM Replace Docker's "_" with "+" as used in JDK versioning
set "JAVA_EMBEDDING_VERSION=%JAVA_DOCKER_VERSION:_=+%"

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

rem ------------------------------------------------------------
rem Step 1: Check for existing JRE
rem ------------------------------------------------------------
if exist "%BUILD_DIR%\assets-local\jre-windows" (
    call :info "Custom JRE directory already exists (%BUILD_DIR%\assets-local\jre-windows); skipping jlink step."
    goto :jre-done
)

rem ------------------------------------------------------------
rem Step 2: Create Variables
rem ------------------------------------------------------------
set "JDK_URL=https://api.adoptium.net/v3/binary/version/jdk-%JAVA_EMBEDDING_VERSION%/windows/x64/jdk/hotspot/normal/eclipse?project=jdk"

call :info "Download JDK archive from URL: %JDK_URL%"

curl -sLo "%BUILD_DIR%\assets-local\jdk-windows.zip" "%JDK_URL%"
if errorlevel 1 (
    call :fail "Failed to download JDK from %JDK_URL%"
    goto :enderror
)

call :info "Extract JDK archive to %BUILD_DIR%\assets-local\jdk-windows"

if not exist "%BUILD_DIR%\assets-local\jdk-windows" (
    mkdir "%BUILD_DIR%\assets-local\jdk-windows"
)
tar -xf "%BUILD_DIR%\assets-local\jdk-windows.zip" -C "%BUILD_DIR%\assets-local\jdk-windows"
if errorlevel 1 (
    call :fail "Failed to extract JDK archive."
    goto :enderror
)

set "JDK_BIN_DIR="
for /d %%d in ("%BUILD_DIR%\assets-local\jdk-windows\jdk-*") do (
    if exist "%%d\bin" (
        set "JDK_BIN_DIR=%%d\bin"
        call :info "JDK bin directory found: !JDK_BIN_DIR!"
        goto :found_jdk
    )
)

call :fail "Failed to find JDK directory after extraction."
goto :enderror

:found_jdk

jlink --version >nul 2>&1
if errorlevel 1 (
    call :fail "jlink not found on PATH. Ensure you are using a JDK that provides jlink and it's on PATH."
    goto :enderror
)

if not exist "%BUILD_DIR%\assets-windows\jre-modules.txt" (
    call :fail "Missing '%BUILD_DIR%\assets-windows\jre-modules.txt'. This file should list the JDK modules."
    goto :enderror
)

rem Build comma-separated module list
set "MODS="
for /f "usebackq delims=" %%M in ("%BUILD_DIR%\assets-windows\jre-modules.txt") do (
    set "line=%%M"
    rem Skip lines starting with # (allow ; comments handled by FOR /F eol default)
    set "first=!line:~0,1!"
    if not "!first!"=="#" (
        if defined MODS (
            set "MODS=!MODS!,!line!"
        ) else (
            set "MODS=!line!"
        )
    )
)

if not defined MODS (
    call :fail "No modules found in %BUILD_DIR%\assets-windows\jre-modules.txt"
    goto :enderror
)

call :info "Using jlink modules: !MODS!"

jlink --strip-debug --no-header-files --no-man-pages --add-modules !MODS! --output "%BUILD_DIR%\assets-local\jre-windows"
if errorlevel 1 (
    call :fail "jlink failed to create the custom JRE."
    goto :enderror
)

:jre-done

rem ------------------------------------------------------------
rem Done
rem ------------------------------------------------------------
call :info "JRE creation completed successfully."
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
exit /b 1
