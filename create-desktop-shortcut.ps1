$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$shortcutPath = Join-Path ([Environment]::GetFolderPath("Desktop")) "LAN CopyPaste.lnk"
$target = Join-Path $root "LAN CopyPaste Launcher.bat"
$iconFile = Join-Path $root "public\assets\lan-copypaste.ico"

$shell = New-Object -ComObject WScript.Shell
$shortcut = $shell.CreateShortcut($shortcutPath)
$shortcut.TargetPath = $target
$shortcut.WorkingDirectory = $root
$shortcut.IconLocation = $iconFile
$shortcut.Description = "Khởi chạy LAN CopyPaste và hiển thị icon ở khay hệ thống"
$shortcut.Save()

Write-Host "Created: $shortcutPath"
