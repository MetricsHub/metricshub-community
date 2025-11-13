@echo off

REM Start a new local scope for environment variables
setlocal

REM -----------------------------------------------------------------------------
REM package.cmd
REM -----------------------------------------------------------------------------
REM This script packages the MetricsHub application using jpackage.
REM It reproduces the two packages under <DEST_DIR>:
REM   1. installable (e.g., .msi or .exe)
REM   2. app-image   (Directory with the application image, e.g. metricshub\)
REM
REM To include a custom asset, place it in the assets\ directory defined by --app-content
REM in jpackage.txt. For example, to include the OpenTelemetry Collector Contrib binary,
REM place it in assets\otel\ (otelcol-contrib.exe) and it will be included in the package.
REM
REM Usage:
REM   package.cmd <JRE_DIR> <DEST_DIR>
REM
REM Example:
REM   package.cmd C:\temp\jre C:\temp\builds
REM -----------------------------------------------------------------------------

if "%JAVA_HOME%"=="" (
    echo JAVA_HOME is not set. Please set it before running this script.
    exit /b 1
)

REM Check if jpackage exists in JAVA_HOME
if not exist "%JAVA_HOME%" (
    echo JAVA_HOME directory "%JAVA_HOME%" does not exist.
    exit /b 1
)

REM Ensure JRE directory is provided
if "%~1"=="" (
    echo Missing JRE directory argument.
    echo Usage: %~nx0 ^<JRE_DIR^> ^<DEST_DIR^>
    exit /b 1
)

REM Ensure destination directory is provided
if "%~2"=="" (
    echo Missing destination directory argument.
    echo Usage: %~nx0 ^<JRE_DIR^> ^<DEST_DIR^>
    exit /b 1
)

set "JRE_DIR=%~1"
if not exist "%JRE_DIR%" (
    echo JRE directory "%JRE_DIR%" does not exist.
    exit /b 1
)

set "DEST_DIR=%~2"
if not exist "%DEST_DIR%" mkdir "%DEST_DIR%"

set "JPACKAGE_BIN=%JAVA_HOME%\bin\jpackage.exe"

REM -----------------------------------------------------------------------------
REM Shared launcher arguments
REM -----------------------------------------------------------------------------
set COMMON_ARGS=^
 --runtime-image "%JRE_DIR%" ^
 --add-launcher MetricsHub-Encrypt=metricshub-encrypt.properties^
 --add-launcher apikey=metricshub-apikey.properties^
 --add-launcher httpcli=metricshub-httpcli.properties^
 --add-launcher ipmicli=metricshub-ipmicli.properties^
 --add-launcher jawk=metricshub-jawk.properties^
 --add-launcher jdbccli=metricshub-jdbccli.properties^
 --add-launcher jmxcli=metricshub-jmxcli.properties^
 --add-launcher pingcli=metricshub-pingcli.properties^
 --add-launcher snmpcli=metricshub-snmpcli.properties^
 --add-launcher snmpv3cli=metricshub-snmpv3cli.properties^
 --add-launcher sshcli=metricshub-sshcli.properties^
 --add-launcher user=metricshub-user.properties^
 --add-launcher wbemcli=metricshub-wbemcli.properties^
 --add-launcher winrmcli=metricshub-winrmcli.properties^
 --add-launcher wmicli=metricshub-wmicli.properties^
 --dest "%DEST_DIR%"

REM -----------------------------------------------------------------------------
REM installable
REM -----------------------------------------------------------------------------
echo Running jpackage for installable...
"%JPACKAGE_BIN%" --verbose ^
 --add-launcher MetricsHubServiceManager=metricshub-agent.properties ^
 --license-file assets/LICENSE ^
 --win-dir-chooser ^
 %COMMON_ARGS% ^
 @jpackage.txt
if errorlevel 1 (
    echo jpackage failed for installable.
    exit /b 1
)
echo installable packaging completed.
echo.

REM -----------------------------------------------------------------------------
REM app-image
REM -----------------------------------------------------------------------------
echo Running jpackage for app-image...
"%JPACKAGE_BIN%" --verbose ^
 --add-launcher MetricsHubServiceManager=metricshub-agent.properties ^
 %COMMON_ARGS% ^
 @jpackage.txt ^
 --type app-image
if errorlevel 1 (
    echo jpackage failed for app-image.
    exit /b 1
)
echo app-image packaging completed.
echo.

echo All packaging steps completed successfully! Output in: %DEST_DIR%

echo %DEST_DIR% content:
echo ------------------------------------------------------------
dir "%DEST_DIR%"
echo ------------------------------------------------------------
echo.

endlocal
