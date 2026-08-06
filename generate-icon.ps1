$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$assetDir = Join-Path $root "public\assets"
$icoPath = Join-Path $assetDir "lan-copypaste.ico"
$pngPath = Join-Path $assetDir "lan-copypaste-icon.png"

New-Item -ItemType Directory -Force -Path $assetDir | Out-Null
Add-Type -AssemblyName System.Drawing

$size = 256
$bitmap = New-Object System.Drawing.Bitmap $size, $size
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
$graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
$graphics.Clear([System.Drawing.Color]::Transparent)

$rect = New-Object System.Drawing.Rectangle 12, 12, 232, 232
$path = New-Object System.Drawing.Drawing2D.GraphicsPath
$radius = 48
$path.AddArc($rect.X, $rect.Y, $radius, $radius, 180, 90)
$path.AddArc($rect.Right - $radius, $rect.Y, $radius, $radius, 270, 90)
$path.AddArc($rect.Right - $radius, $rect.Bottom - $radius, $radius, $radius, 0, 90)
$path.AddArc($rect.X, $rect.Bottom - $radius, $radius, $radius, 90, 90)
$path.CloseFigure()

$bg = New-Object System.Drawing.Drawing2D.LinearGradientBrush $rect,
  ([System.Drawing.Color]::FromArgb(20, 125, 100)),
  ([System.Drawing.Color]::FromArgb(27, 44, 63)),
  45
$graphics.FillPath($bg, $path)

$glowBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(50, 125, 224, 210))
$graphics.FillEllipse($glowBrush, 116, 28, 118, 118)

$linePen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(238, 255, 255, 255)), 18
$linePen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
$linePen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
$accentPen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(126, 224, 218)), 16
$accentPen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
$accentPen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round

$graphics.DrawLine($linePen, 76, 94, 118, 94)
$graphics.DrawLine($linePen, 76, 94, 76, 162)
$graphics.DrawLine($linePen, 76, 162, 118, 162)
$graphics.DrawLine($accentPen, 138, 94, 180, 94)
$graphics.DrawLine($accentPen, 180, 94, 180, 162)
$graphics.DrawLine($accentPen, 138, 162, 180, 162)

$arrowPen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255, 245, 250, 252)), 12
$arrowPen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
$arrowPen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
$graphics.DrawLine($arrowPen, 114, 128, 145, 128)
$graphics.DrawLine($arrowPen, 145, 128, 132, 115)
$graphics.DrawLine($arrowPen, 145, 128, 132, 141)

$font = New-Object System.Drawing.Font "Segoe UI", 34, ([System.Drawing.FontStyle]::Bold), ([System.Drawing.GraphicsUnit]::Pixel)
$textBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(235, 255, 255, 255))
$format = New-Object System.Drawing.StringFormat
$format.Alignment = [System.Drawing.StringAlignment]::Center
$graphics.DrawString("LAN", $font, $textBrush, (New-Object System.Drawing.RectangleF 0, 182, 256, 44), $format)

$bitmap.Save($pngPath, [System.Drawing.Imaging.ImageFormat]::Png)

$iconStream = New-Object System.IO.MemoryStream
$bitmap.GetHicon() | ForEach-Object {
  $icon = [System.Drawing.Icon]::FromHandle($_)
  $file = [System.IO.File]::Create($icoPath)
  $icon.Save($file)
  $file.Close()
  $icon.Dispose()
}

$graphics.Dispose()
$bitmap.Dispose()

Write-Host "Created: $icoPath"
Write-Host "Created: $pngPath"
