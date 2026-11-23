@echo off
rem Double-click to build and play. Pass a map file to play your own floor.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0run.ps1" %*
if errorlevel 1 pause
