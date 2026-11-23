@echo off
rem Runs the JUnit tests. The first run downloads JUnit (about 3 MB) from Maven Central.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0test.ps1" %*
pause
