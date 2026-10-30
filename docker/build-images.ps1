# Builds the API, frontend and DB images as tags of one Docker Hub repository
# (<Repo>:db-<Tag>, <Repo>:api-<Tag>, <Repo>:frontend-<Tag>) and optionally
# pushes them, so a testing laptop only needs docker\deploy's small files and
# pulls the images itself.
#
#   docker login                       # once, as the Docker Hub account that owns the repo
#   powershell -ExecutionPolicy Bypass -File docker\build-images.ps1 -Tag 1.0.0 -Push
#
# Options: -Repo pichoudam/mini_pawnshop_v1  -FrontendPath ..\Score-Management-System-Frontend
#          -NoBuild (push/export what is already built)  -Export (also write deploy\images\score-images.tar for offline laptops)
param(
    [Parameter(Mandatory = $true)][string]$Tag,
    [string]$Repo = 'pichoudam/mini_pawnshop_v1',
    [string]$FrontendPath = (Join-Path $PSScriptRoot '..\..\Score-Management-System-Frontend'),
    [switch]$Push,
    [switch]$NoBuild,
    [switch]$Export
)
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$env:DOCKER_BUILDKIT = '1'

function Invoke-Docker {
    & docker @args
    if ($LASTEXITCODE -ne 0) { throw "docker $($args -join ' ') failed with exit code $LASTEXITCODE" }
}

$images = @(
    "${Repo}:db-$Tag",
    "${Repo}:api-$Tag",
    "${Repo}:frontend-$Tag"
)

if (-not $NoBuild) {
    $FrontendPath = (Resolve-Path $FrontendPath).Path

    Write-Host "==> Building $($images[0])"
    Invoke-Docker build -f (Join-Path $repoRoot 'docker\db\Dockerfile') -t $images[0] $repoRoot

    Write-Host "==> Building $($images[1]) (first build takes several minutes)"
    Invoke-Docker build --target production -t $images[1] $repoRoot

    Write-Host "==> Building $($images[2])"
    Invoke-Docker build --target production -t $images[2] $FrontendPath
}

if ($Push) {
    foreach ($image in $images) {
        Write-Host "==> Pushing $image"
        Invoke-Docker push $image
    }
    Write-Host "Pushed. On the testing laptop set IMAGE_TAG=$Tag in docker\deploy\.env and run start.ps1 (or start.sh)."
}

if ($Export) {
    $outDir = Join-Path $PSScriptRoot 'deploy\images'
    New-Item -ItemType Directory -Force $outDir | Out-Null
    $tar = Join-Path $outDir 'score-images.tar'
    Write-Host "==> Exporting images to $tar"
    Invoke-Docker save -o $tar @images
    Write-Host ("Done. {0:N0} MB. For offline laptops: copy it to docker\deploy\images and run start.ps1 -Action load." -f ((Get-Item $tar).Length / 1MB))
}
