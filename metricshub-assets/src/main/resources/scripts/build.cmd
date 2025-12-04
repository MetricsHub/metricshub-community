@echo off
setlocal EnableDelayedExpansion

rem ============================================================
rem  build.cmd
rem  Local Windows build script (no signing, no uploads)
rem ============================================================

rem --- Setup paths ---
set BASE_DIR=%~dp0%

call %BASE_DIR%\build-windows.cmd
call %BASE_DIR%\build-docker-linux.cmd
