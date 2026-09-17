@echo off
set SCRIPT_DIR=%~dp0
node "%SCRIPT_DIR%context-mode\cli.bundle.mjs" %*
