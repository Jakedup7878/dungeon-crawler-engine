@echo off
rem Publishes a project made with the editor. Usage: publish.cmd "C:\Games\My Game"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0publish.ps1" %*
pause
