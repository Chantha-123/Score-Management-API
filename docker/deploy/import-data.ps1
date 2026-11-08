# Replaces this laptop's app data with data\fineract_default.sql from export-data.ps1.
#   powershell -ExecutionPolicy Bypass -File import-data.ps1
# Run start.ps1 once first so the database exists. Everything currently in
# fineract_default on this laptop is DELETED. Logins, users and passwords come
# from the exported laptop (the app login is no longer necessarily mifos/password).
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

function Invoke-Docker {
    & docker @args
    if ($LASTEXITCODE -ne 0) { throw "docker $($args -join ' ') failed with exit code $LASTEXITCODE" }
}

$dump = 'data\fineract_default.sql'
if (-not (Test-Path $dump)) { throw "$dump not found; run export-data.ps1 on the source laptop and copy the data folder here." }
$dbId = (& docker compose ps -q db).Trim()
if (-not $dbId) { throw 'The db container is not running; run start.ps1 first.' }

$answer = Read-Host 'This deletes all data in fineract_default on this laptop. Type YES to continue'
if ($answer -cne 'YES') { Write-Host 'Cancelled.'; return }

# The API keeps a connection pool open and would fail mid-import.
Write-Host 'Stopping the API ...'
Invoke-Docker compose stop api frontend

Write-Host 'Importing ...'
Invoke-Docker cp $dump "${dbId}:/tmp/fineract_default.sql"
# The dump recreates the database itself; the fineract user's grant survives the drop.
# No double quotes inside sh -c: PowerShell 5.1 strips them from native-command arguments.
Invoke-Docker compose exec -T db sh -c 'export MYSQL_PWD=$MARIADB_ROOT_PASSWORD; mariadb -uroot -e ''DROP DATABASE IF EXISTS fineract_default'' && mariadb -uroot < /tmp/fineract_default.sql'
Invoke-Docker compose exec -T db rm -f /tmp/fineract_default.sql

# The API runs any newer migrations on start, so an older dump works with newer images.
Write-Host 'Starting the API (waits until healthy) ...'
Invoke-Docker compose up -d --wait --wait-timeout 900 api frontend
Write-Host 'Done. Log in with the users from the exported laptop.'
