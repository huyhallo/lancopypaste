param(
  [switch]$NoAutoStart
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$dataDir = Join-Path $root ".data"
$pidFile = Join-Path $dataDir "launcher-server.pid"
$logFile = Join-Path $dataDir "launcher-server.log"
$errorLogFile = Join-Path $dataDir "launcher-server-error.log"
$iconFile = Join-Path $root "public\assets\lan-copypaste.ico"
$port = if ($env:PORT) { [int]$env:PORT } else { 3000 }
$localUrl = "http://localhost:$port"

New-Item -ItemType Directory -Force -Path $dataDir | Out-Null

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

function Get-LanAddresses {
  Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
    Where-Object { $_.IPAddress -notlike "127.*" -and $_.IPAddress -notlike "169.254.*" } |
    Select-Object -ExpandProperty IPAddress
}

function Get-PrimaryLanUrl {
  $address = Get-LanAddresses | Where-Object { $_ -like "192.168.*" } | Select-Object -First 1
  if (-not $address) {
    $address = Get-LanAddresses | Select-Object -First 1
  }
  if ($address) { "http://$address`:$port" } else { $localUrl }
}

function Test-Server {
  try {
    $response = Invoke-WebRequest -UseBasicParsing -Uri "$localUrl/api/discovery" -TimeoutSec 2
    return $response.StatusCode -eq 200
  } catch {
    return $false
  }
}

function Get-LauncherServerProcess {
  if (-not (Test-Path $pidFile)) { return $null }
  $serverPid = (Get-Content $pidFile -ErrorAction SilentlyContinue | Select-Object -First 1)
  if (-not $serverPid) { return $null }
  try {
    return Get-Process -Id ([int]$serverPid) -ErrorAction Stop
  } catch {
    return $null
  }
}

function Start-Server {
  if (Test-Server) { return }

  $node = Get-Command node -ErrorAction SilentlyContinue
  if (-not $node) {
    [System.Windows.Forms.MessageBox]::Show(
      "Không tìm thấy Node.js. Hãy cài Node.js hoặc chạy trong thư mục có Node.",
      "LAN CopyPaste",
      [System.Windows.Forms.MessageBoxButtons]::OK,
      [System.Windows.Forms.MessageBoxIcon]::Error
    ) | Out-Null
    return
  }

  $process = Start-Process -FilePath $node.Source `
    -ArgumentList "server.js" `
    -WorkingDirectory $root `
    -WindowStyle Hidden `
    -RedirectStandardOutput $logFile `
    -RedirectStandardError $errorLogFile `
    -PassThru
  Set-Content -Path $pidFile -Value $process.Id
  Start-Sleep -Milliseconds 800
}

function Stop-Server {
  $process = Get-LauncherServerProcess
  if ($process) {
    Stop-Process -Id $process.Id -Force
    Remove-Item $pidFile -ErrorAction SilentlyContinue
  }
}

function Restart-Server {
  Stop-Server
  Start-Sleep -Milliseconds 400
  Start-Server
}

function Open-Web {
  Start-Process $localUrl
}

function Copy-LanUrl {
  [System.Windows.Forms.Clipboard]::SetText((Get-PrimaryLanUrl))
}

function Get-StartupShortcutPath {
  Join-Path ([Environment]::GetFolderPath("Startup")) "LAN CopyPaste.lnk"
}

function Enable-Startup {
  $shortcutPath = Get-StartupShortcutPath
  $shell = New-Object -ComObject WScript.Shell
  $shortcut = $shell.CreateShortcut($shortcutPath)
  $shortcut.TargetPath = "powershell.exe"
  $shortcut.Arguments = "-NoProfile -ExecutionPolicy Bypass -STA -WindowStyle Hidden -File `"$root\windows-launcher.ps1`""
  $shortcut.WorkingDirectory = $root
  $shortcut.IconLocation = $iconFile
  $shortcut.Save()
}

function Disable-Startup {
  Remove-Item (Get-StartupShortcutPath) -ErrorAction SilentlyContinue
}

function New-TrayIcon {
  if (Test-Path $iconFile) {
    return New-Object System.Drawing.Icon $iconFile
  }

  $bitmap = New-Object System.Drawing.Bitmap 32, 32
  $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
  $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
  $graphics.Clear([System.Drawing.Color]::Transparent)
  $brush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(42, 168, 131))
  $darkBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(13, 130, 103))
  $font = New-Object System.Drawing.Font "Segoe UI", 14, ([System.Drawing.FontStyle]::Bold), ([System.Drawing.GraphicsUnit]::Pixel)
  $textBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::White)
  $graphics.FillEllipse($darkBrush, 1, 1, 30, 30)
  $graphics.FillEllipse($brush, 6, 6, 20, 20)
  $graphics.DrawString("L", $font, $textBrush, 10, 7)
  $handle = $bitmap.GetHicon()
  $icon = [System.Drawing.Icon]::FromHandle($handle)
  $graphics.Dispose()
  $bitmap.Dispose()
  return $icon
}

function Update-Ui {
  $running = Test-Server
  $notifyIcon.Text = if ($running) { "LAN CopyPaste đang chạy" } else { "LAN CopyPaste đã dừng" }
  $statusItem.Text = if ($running) { "Trạng thái: đang chạy" } else { "Trạng thái: đã dừng" }
  $startItem.Enabled = -not $running
  $stopItem.Enabled = $running
  $openItem.Enabled = $running
  $copyItem.Enabled = $running
  $lanItem.Text = "LAN: $(Get-PrimaryLanUrl)"
  $startupItem.Checked = Test-Path (Get-StartupShortcutPath)
}

if (-not $NoAutoStart) {
  Start-Server
}

$notifyIcon = New-Object System.Windows.Forms.NotifyIcon
$notifyIcon.Icon = New-TrayIcon
$notifyIcon.Visible = $true

$menu = New-Object System.Windows.Forms.ContextMenuStrip
$statusItem = $menu.Items.Add("Trạng thái")
$statusItem.Enabled = $false
$lanItem = $menu.Items.Add("LAN")
$lanItem.Enabled = $false
$menu.Items.Add("-") | Out-Null
$openItem = $menu.Items.Add("Mở web")
$copyItem = $menu.Items.Add("Copy địa chỉ LAN")
$menu.Items.Add("-") | Out-Null
$startItem = $menu.Items.Add("Khởi động server")
$stopItem = $menu.Items.Add("Dừng server")
$restartItem = $menu.Items.Add("Khởi động lại server")
$menu.Items.Add("-") | Out-Null
$startupItem = $menu.Items.Add("Chạy cùng Windows")
$startupItem.CheckOnClick = $false
$menu.Items.Add("-") | Out-Null
$logItem = $menu.Items.Add("Mở log server")
$exitItem = $menu.Items.Add("Thoát launcher")

$openItem.Add_Click({ Open-Web })
$copyItem.Add_Click({ Copy-LanUrl; $notifyIcon.ShowBalloonTip(1200, "LAN CopyPaste", "Đã copy địa chỉ LAN.", [System.Windows.Forms.ToolTipIcon]::Info) })
$startItem.Add_Click({ Start-Server; Update-Ui })
$stopItem.Add_Click({ Stop-Server; Update-Ui })
$restartItem.Add_Click({ Restart-Server; Update-Ui })
$startupItem.Add_Click({
  if (Test-Path (Get-StartupShortcutPath)) { Disable-Startup } else { Enable-Startup }
  Update-Ui
})
$logItem.Add_Click({
  if (Test-Path $logFile) { Start-Process notepad.exe $logFile }
})
$exitItem.Add_Click({
  $notifyIcon.Visible = $false
  [System.Windows.Forms.Application]::Exit()
})
$notifyIcon.Add_DoubleClick({ Open-Web })
$notifyIcon.ContextMenuStrip = $menu

$timer = New-Object System.Windows.Forms.Timer
$timer.Interval = 3000
$timer.Add_Tick({ Update-Ui })
$timer.Start()

Update-Ui
$notifyIcon.ShowBalloonTip(1600, "LAN CopyPaste", "Launcher đã sẵn sàng. Nhấp đúp icon để mở web.", [System.Windows.Forms.ToolTipIcon]::Info)
[System.Windows.Forms.Application]::Run()
