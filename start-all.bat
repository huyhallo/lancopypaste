@echo off
setlocal

cd /d "%~dp0"

start "LAN CopyPaste Server" cmd /k npm start
timeout /t 2 >nul
start "LAN CopyPaste Agent" cmd /k npm run agent

echo Server va desktop agent da duoc mo trong 2 cua so rieng.
echo Neu dung dien thoai, mo URL LAN ma cua so server in ra.
pause
