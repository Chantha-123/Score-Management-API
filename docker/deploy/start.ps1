# Starts the test stack on this laptop (Windows / Docker Desktop).
#   powershell -ExecutionPolicy Bypass -File start.ps1 [-Action up|down|ps|logs|pull|load]
param([ValidateSet('up', 'down', 'ps', 'logs', 'pull', 'load')][string]$Action = 'up')
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

function Invoke-Docker {
    & docker @args
    if ($LASTEXITCODE -ne 0) { throw "docker $($args -join ' ') failed with exit code $LASTEXITCODE" }
}

function Get-EnvValue([string]$name, [string]$default) {
    $line = Get-Content .env | Where-Object { $_ -match "^$name=" } | Select-Object -First 1
    if ($line) { $value = $line.Substring($name.Length + 1).Trim(); if ($value) { return $value } }
    return $default
}

# `docker images -q` prints nothing (and no stderr) for a missing image. `docker image inspect`
# writes "No such image" to stderr, which Windows PowerShell 5.1 turns into a terminating error here.
function Test-Image([string]$image) { [bool](& docker images -q $image) }

function New-Secret { -join ((48..57) + (97..122) | Get-Random -Count 24 | ForEach-Object { [char]$_ }) }

if (-not (Test-Path .env)) {
    (Get-Content .env.example) `
        -replace '^DB_ROOT_PASSWORD=$', "DB_ROOT_PASSWORD=$(New-Secret)" `
        -replace '^DB_PASSWORD=$', "DB_PASSWORD=$(New-Secret)" |
        Set-Content -Encoding ascii .env
    Write-Host 'Created .env with generated database passwords (keep this file).'
}

$repo = Get-EnvValue 'IMAGE_REPO' 'pichoudam/mini_pawnshop_v1'
$tag = Get-EnvValue 'IMAGE_TAG' ''
if (-not $tag) { throw 'Set IMAGE_TAG in .env to the pushed version (e.g. IMAGE_TAG=1.0.0).' }
function Get-MissingImages { foreach ($svc in 'db', 'api', 'frontend') { if (-not (Test-Image "${repo}:$svc-$tag")) { "${repo}:$svc-$tag" } } }

# Offline laptops: images\score-images.tar (build-images.ps1 -Export) instead of Docker Hub.
if ($Action -eq 'load' -or ($Action -eq 'up' -and (Test-Path images\score-images.tar) -and (Get-MissingImages))) {
    Write-Host 'Loading images from images\score-images.tar ...'
    Invoke-Docker load -i images\score-images.tar
}

if ($Action -eq 'pull' -or ($Action -eq 'up' -and (Get-MissingImages))) {
    Write-Host "Pulling ${repo}:*-$tag from Docker Hub ..."
    Invoke-Docker compose pull
}

if ($Action -in 'load', 'pull') {
    $missing = Get-MissingImages
    if ($missing) { throw "Image(s) not found: $($missing -join ', ')" }
    Write-Host 'All images present.'; return
}

switch ($Action) {
    'down' { Invoke-Docker compose down; return }
    'ps' { Invoke-Docker compose ps; return }
    'logs' { Invoke-Docker compose logs -f --tail 100; return }
}

Invoke-Docker compose up -d
Write-Host 'Waiting for the API to become healthy (first start runs DB migrations, several minutes) ...'
$apiId = (& docker compose ps -q api).Trim()
$deadline = (Get-Date).AddMinutes(15)
do {
    Start-Sleep -Seconds 10
    $state = (& docker inspect -f '{{.State.Health.Status}}' $apiId).Trim()
    Write-Host "  api: $state"
} until ($state -eq 'healthy' -or (Get-Date) -gt $deadline)
if ($state -ne 'healthy') { throw 'API did not become healthy; check: start.ps1 -Action logs' }

$webPort = Get-EnvValue 'WEB_PORT' '9002'
$apiPort = Get-EnvValue 'API_PORT' '8443'
# Only adapters with a default gateway are real LAN/Wi-Fi links (skips WSL, Hyper-V, VirtualBox).
$ips = Get-NetIPConfiguration -ErrorAction SilentlyContinue |
    Where-Object { $_.IPv4DefaultGateway -and $_.NetAdapter.Status -eq 'Up' } |
    ForEach-Object { $_.IPv4Address.IPAddress }
Write-Host ''
Write-Host 'Ready. Login: mifos / password (change it after first login).'
Write-Host "  This laptop : http://localhost:${webPort}/?baseApiUrl=https://localhost:${apiPort}/fineract-provider/api/v1#"
Write-Host "               (first open https://localhost:${apiPort}/fineract-provider/actuator/health and accept the certificate)"
foreach ($ip in $ips) {
    Write-Host "  LAN         : http://${ip}:${webPort}/   (no certificate prompt needed)"
}
Write-Host '  LAN access needs the firewall rule once: run open-firewall.ps1 as Administrator.'
