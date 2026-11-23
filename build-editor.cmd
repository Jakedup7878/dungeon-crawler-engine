@echo off
rem Builds the map editor as a program: dist\Dungeon Crawler Editor\Dungeon Crawler Editor.exe
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-editor.ps1" %*
pause
