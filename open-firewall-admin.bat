@echo off
setlocal

net session >nul 2>&1
if %errorlevel% neq 0 (
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
  exit /b
)

echo Opening Windows Firewall for LAN CopyPaste on TCP port 3000...
netsh advfirewall firewall add rule name="LAN CopyPaste 3000" dir=in action=allow protocol=TCP localport=3000 profile=private

echo.
echo Done. Try opening this on your phone:
echo http://192.168.1.101:3000
echo.
pause
