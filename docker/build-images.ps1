# Builds the API, frontend and DB images and exports them to
# docker/deploy/images/score-images.tar so the docker/deploy folder can be
# copied to a testing laptop and started there without source or internet.
#
#   powershell -ExecutionPolicy Bypass -File docker\build-images.ps1 [-Tag local] [-FrontendPath ..\Score-Management-System-Frontend] [-NoExport]
param(
    [string]$Tag = 'local',
    [string]$FrontendPath = (Join-Path $PSScriptRoot '..\..\Score-Management-System-Frontend'),
    [switch]$NoExport
)
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$FrontendPath = (Resolve-Path $FrontendPath).Path
$env:DOCKER_BUILDKIT = '1'

function Invoke-Docker {
    & docker @args
    if ($LASTEXITCODE -ne 0) { throw "docker $($args -join ' ') failed with exit code $LASTEXITCODE" }
}

$images = @(
    "mini-pawnshop-db:$Tag",
    "mini-pawnshop-api:$Tag",
    "mini-pawnshop-frontend:$Tag"
)

Write-Host "==> Building mini-pawnshop-db:$Tag"
Invoke-Docker build -f (Join-Path $repoRoot 'docker\db\Dockerfile') -t $images[0] $repoRoot

Write-Host "==> Building mini-pawnshop-api:$Tag (first build takes several minutes)"
Invoke-Docker build --target production -t $images[1] $repoRoot

Write-Host "==> Building mini-pawnshop-frontend:$Tag"
Invoke-Docker build --target production -t $images[2] $FrontendPath

if (-not $NoExport) {
    $outDir = Join-Path $PSScriptRoot 'deploy\images'
    New-Item -ItemType Directory -Force $outDir | Out-Null
    $tar = Join-Path $outDir 'score-images.tar'
    Write-Host "==> Exporting images to $tar"
    Invoke-Docker save -o $tar @images
    Write-Host ("Done. {0:N0} MB. Copy the whole docker\deploy folder to the testing laptop and run start.ps1 (or start.sh)." -f ((Get-Item $tar).Length / 1MB))
}
