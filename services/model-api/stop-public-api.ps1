$ErrorActionPreference = "SilentlyContinue"

Get-Process cloudflared | Stop-Process -Force

$listeners = Get-NetTCPConnection -LocalPort 8081 -State Listen
foreach ($listener in $listeners) {
    Stop-Process -Id $listener.OwningProcess -Force
}

Write-Host "Stopped local model API and Cloudflare Tunnel."
