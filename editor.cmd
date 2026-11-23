@echo off
rem Double-click to build and open the map editor. Pass a map file to open it (or to start a new one there).
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0run.ps1" -Editor %*
if errorlevel 1 pause
