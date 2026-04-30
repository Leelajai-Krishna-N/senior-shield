$ErrorActionPreference = "Stop"

$workspace = "C:\sjbit hackathon"
$python = "C:\Users\adith\miniconda3\python.exe"
$cloudflared = "C:\Program Files (x86)\cloudflared\cloudflared.exe"
$modelLog = Join-Path $workspace "model-api.log"
$modelErr = Join-Path $workspace "model-api.err.log"
$tunnelLog = Join-Path $workspace "cloudflared.stdout.log"
$tunnelErr = Join-Path $workspace "cloudflared.stderr.log"
$publicUrlFile = Join-Path $workspace "public-model-api-url.txt"

function Wait-ForHealth {
    param([string]$Url, [int]$TimeoutSeconds = 60)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-RestMethod -Uri $Url -Method Get -TimeoutSec 5
            if ($response.ok -eq $true) { return $true }
        } catch {}
        Start-Sleep -Seconds 2
    }
    return $false
}

function Wait-ForCloudflareUrl {
    param([string]$LogPath, [int]$TimeoutSeconds = 60)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-Path $LogPath) {
            $match = Select-String -Path $LogPath -Pattern 'https://[a-zA-Z0-9-]+\.trycloudflare\.com' -AllMatches -ErrorAction SilentlyContinue
            if ($match) { return $match.Matches[0].Value }
        }
        Start-Sleep -Seconds 2
    }
    return $null
}

Write-Host "Starting Senior Shield local model API..." -ForegroundColor Cyan

if (-not (Wait-ForHealth -Url "http://127.0.0.1:8081/health" -TimeoutSeconds 2)) {
    Remove-Item $modelLog, $modelErr -Force -ErrorAction SilentlyContinue
    Start-Process -FilePath $python `
        -ArgumentList "services\model-api\server.py" `
        -WorkingDirectory $workspace `
        -RedirectStandardOutput $modelLog `
        -RedirectStandardError $modelErr `
        -WindowStyle Hidden | Out-Null
}

if (-not (Wait-ForHealth -Url "http://127.0.0.1:8081/health" -TimeoutSeconds 60)) {
    Write-Host "Model API failed to start. Check logs:" -ForegroundColor Red
    Write-Host "  $modelLog"
    Write-Host "  $modelErr"
    exit 1
}

Write-Host "Model API is live at http://127.0.0.1:8081" -ForegroundColor Green
Write-Host "Starting Cloudflare Tunnel..." -ForegroundColor Cyan

Get-Process cloudflared -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Remove-Item $tunnelLog, $tunnelErr -Force -ErrorAction SilentlyContinue

Start-Process -FilePath $cloudflared `
    -ArgumentList @("tunnel", "--url", "http://127.0.0.1:8081") `
    -WorkingDirectory $workspace `
    -RedirectStandardOutput $tunnelLog `
    -RedirectStandardError $tunnelErr `
    -WindowStyle Hidden | Out-Null

$publicUrl = Wait-ForCloudflareUrl -LogPath $tunnelLog -TimeoutSeconds 20
if (-not $publicUrl) {
    $publicUrl = Wait-ForCloudflareUrl -LogPath $tunnelErr -TimeoutSeconds 20
}
if (-not $publicUrl) {
    Write-Host "Tunnel did not produce a public URL. Check logs:" -ForegroundColor Red
    Write-Host "  $tunnelLog"
    Write-Host "  $tunnelErr"
    exit 1
}

Set-Content -Path $publicUrlFile -Value $publicUrl -Encoding ascii

Write-Host ""
Write-Host "Public API is ready:" -ForegroundColor Green
Write-Host "  Health:  $publicUrl/health"
Write-Host "  Predict: $publicUrl/predict"
Write-Host ""
Write-Host "Saved URL to: $publicUrlFile" -ForegroundColor Yellow
