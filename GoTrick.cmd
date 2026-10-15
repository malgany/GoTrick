@echo off
setlocal
where pwsh.exe >nul 2>nul
if errorlevel 1 (
  powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\launch.ps1" %*
) else (
  pwsh.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\launch.ps1" %*
)
if errorlevel 1 (
  echo GoTrick could not start. Review the error above.
  pause
)
