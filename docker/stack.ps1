param(
    [ValidateSet('dev', 'prod')][string]$Mode = 'dev',
    [ValidateSet('up', 'down', 'build', 'logs', 'ps', 'restart-api', 'config', 'pull')][string]$Action = 'up'
)
$ErrorActionPreference = 'Stop'
$envPath = Join-Path $PSScriptRoot ".env.$Mode"
if (-not (Test-Path -LiteralPath $envPath)) {
    if ($Mode -eq 'dev') {
        Copy-Item -LiteralPath (Join-Path $PSScriptRoot '.env.dev.example') -Destination $envPath
    } else {
        throw 'Copy docker/.env.prod.example to docker/.env.prod and set both database passwords first.'
    }
}
$composeArgs = @('compose', '--env-file', $envPath, '-p', "score-$Mode", '-f',
    (Join-Path $PSScriptRoot 'compose.yml'), '-f', (Join-Path $PSScriptRoot "compose.$Mode.yml"))
$actionArgs = switch ($Action) {
    'up' { @('up', '-d', '--build') }
    'down' { @('down') }
    'build' { @('build') }
    'logs' { @('logs', '-f', '--tail', '100') }
    'ps' { @('ps') }
    'restart-api' { @('restart', 'api') }
    'config' { @('config', '--quiet') }
    'pull' { @('pull') }
}
& docker @composeArgs @actionArgs
if ($LASTEXITCODE -ne 0) { throw "Docker Compose failed with exit code $LASTEXITCODE" }
