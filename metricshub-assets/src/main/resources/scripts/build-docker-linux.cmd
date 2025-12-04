@echo off
setlocal EnableDelayedExpansion

rem ============================================================
rem  build-docker-linux.cmd
rem  Local Windows script to build Linux packages using Docker
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

call :info "Starting Docker build process"

rem ------------------------------------------------------------
rem Step 1: Check Docker installation
rem ------------------------------------------------------------
call :step "Checking Docker"
docker --version >nul 2>&1
if errorlevel 1 (
    call :fail "Docker not installed. Please install Docker Desktop for Windows and ensure it's on PATH."
    goto :enderror
)

docker info >nul 2>&1
if errorlevel 1 (
    call :fail "Docker Engine not available. Please start Docker Desktop for Windows."
    goto :enderror
)

call :info "Docker detected - OK."

rem ------------------------------------------------------------
rem Step 2: Prepare packaging-assets directory
rem (analog to 'Download Assets' + 'Get Windows Assets')
rem ------------------------------------------------------------
call :step "Preparing Docker packaging assets"

if not exist "%BUILD_DIR%\assets-packaging" (
    call :fail "Unable to find Docker assets in the Maven build directory."
    goto :enderror
)

call :info "Found asset directory: %BUILD_DIR%\assets-packaging"

rem ------------------------------------------------------------
rem Step 3: Build Docker image for packaging
rem ------------------------------------------------------------
call :step "Creating Docker packaging images"

set DOCKER_BUILD_OPTIONS=

set HAS_ARM64=0

set DEBIAN_JPACKAGE_IMAGE=metricshub-community:debian-@debianTagName@-jpackage-@jdkBuildVersion@
set RHEL_JPACKAGE_IMAGE=metricshub-community:rhel-@fedoraTagName@-jpackage-@jdkBuildVersion@
set BUILD_IMAGE=metricshub-community:packages-latest

for /f "tokens=*" %%i in ('docker buildx inspect default 2^>nul ^| findstr /i "linux/arm64"') do (
    set HAS_ARM64=1
)

if %HAS_ARM64%==1 (
    call :info "Linux arm64 platform supported by Docker Buildx."
    set DOCKER_BUILD_OPTIONS=%DOCKER_BUILD_OPTIONS% --platform=linux/arm64,linux/amd64
) else (
    call :info "Linux arm64 platform NOT supported by Docker Buildx; building for linux/amd64 only."
    set DOCKER_BUILD_OPTIONS=%DOCKER_BUILD_OPTIONS% --platform=linux/amd64
)

docker build %DOCKER_BUILD_OPTIONS% -t %DEBIAN_JPACKAGE_IMAGE% -f "%BUILD_DIR%\assets-packaging\Dockerfile.debian" "%BUILD_DIR%\assets-packaging"
if errorlevel 1 (
    call :fail "Failed to create first Docker packaging image for Debian."
    goto :enderror
)

docker build %DOCKER_BUILD_OPTIONS% -t %RHEL_JPACKAGE_IMAGE% -f "%BUILD_DIR%\assets-packaging\Dockerfile.rhel" "%BUILD_DIR%\assets-packaging"
if errorlevel 1 (
    call :fail "Failed to create second Docker packaging image for RHEL."
    goto :enderror
)

docker build %DOCKER_BUILD_OPTIONS% ^
    -t %BUILD_IMAGE% ^
    --build-arg DEBIAN_JPACKAGE_IMAGE=%DEBIAN_JPACKAGE_IMAGE% ^
    --build-arg RHEL_JPACKAGE_IMAGE=%RHEL_JPACKAGE_IMAGE% ^
    -f "%BUILD_DIR%\assets-packaging\Dockerfile.build" ^
    "%BUILD_DIR%"
if errorlevel 1 (
    call :fail "Failed to create third Docker packaging image for JPackage."
    goto :enderror
)

rem ------------------------------------------------------------
rem Step 4: Build Docker Image
rem ------------------------------------------------------------
call :step "Create Docker Image for MetricsHub"

docker build %DOCKER_BUILD_OPTIONS% ^
    -t metricshub-community:latest ^
    --build-arg BUILD_IMAGE=%BUILD_IMAGE% ^
    -f "%BUILD_DIR%\assets-docker\Dockerfile" ^
    "%BUILD_DIR%\assets-docker"
if errorlevel 1 (
    call :fail "Failed to create Docker Image for MetricsHub."
    goto :enderror
)

rem ------------------------------------------------------------
rem Step 5: Extract Packages from Image
rem ------------------------------------------------------------
call :step "Extract Packages from Docker x86 Image"

if not exist "%ROOT_DIR%\packages" (
    mkdir "%ROOT_DIR%\packages"
    if errorlevel 1 (
        call :fail "Unable to create '%ROOT_DIR%\packages' directory."
        goto :enderror
    )
)

docker create --name temp_metricshub_packages %BUILD_IMAGE% >nul 2>&1
if errorlevel 1 (
    call :fail "Failed to create temporary container from packaging image."
    goto :enderror
)
docker cp "temp_metricshub_packages:/tmp/packages/." "%ROOT_DIR%\packages"
if errorlevel 1 (
    call :fail "Failed to copy packages from temporary container."
    docker rm -f temp_metricshub_packages >nul 2>&1
    goto :enderror
)
docker rm -f temp_metricshub_packages >nul 2>&1

if not %HAS_ARM64%==1 goto :skipArm64

call :step "Extract Packages from Docker arm64 Image"

docker create --platform linux/arm64 --name temp_metricshub_packages_arm64 %BUILD_IMAGE% >nul 2>&1
if errorlevel 1 (
    call :fail "Failed to create temporary container from packaging image (arm64)."
    goto :enderror
)
docker cp "temp_metricshub_packages_arm64:/tmp/packages/." "%ROOT_DIR%\packages"
if errorlevel 1 (
    call :fail "Failed to copy packages from temporary container (arm64)."
    docker rm -f temp_metricshub_packages_arm64 >nul 2>&1
    goto :enderror
)
docker rm -f temp_metricshub_packages_arm64 >nul 2>&1

:skipArm64

call :info "Resulting packages:"
dir "%ROOT_DIR%\packages\*"

rem ------------------------------------------------------------
rem Done
rem ------------------------------------------------------------
call :info "Docker packaging completed successfully."
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
