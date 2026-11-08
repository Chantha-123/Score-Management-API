# Dumps this laptop's app data (fineract_default) to data\fineract_default.sql so
# another laptop can start with the same data (import-data.ps1 there).
#   powershell -ExecutionPolicy Bypass -File export-data.ps1
# fineract_tenants is not exported: its connection row holds this laptop's
# DB_PASSWORD, and each laptop keeps its own.
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

function Invoke-Docker {
    & docker @args
    if ($LASTEXITCODE -ne 0) { throw "docker $($args -join ' ') failed with exit code $LASTEXITCODE" }
}

$dbId = (& docker compose ps -q db).Trim()
if (-not $dbId) { throw 'The db container is not running; start the stack first (start.ps1).' }

# Dump inside the container and copy the file out: piping a native command's
# output through PowerShell 5.1 re-encodes it and corrupts the SQL. No double quotes
# inside sh -c: PowerShell 5.1 strips them from native-command arguments.
Write-Host 'Dumping fineract_default ...'
Invoke-Docker compose exec -T db sh -c 'MYSQL_PWD=$MARIADB_ROOT_PASSWORD mariadb-dump -uroot --single-transaction --routines --triggers --events --databases fineract_default > /tmp/fineract_default.sql'
New-Item -ItemType Directory -Force data | Out-Null
Invoke-Docker cp "${dbId}:/tmp/fineract_default.sql" data\fineract_default.sql
Invoke-Docker compose exec -T db rm -f /tmp/fineract_default.sql

Write-Host ("Done: data\fineract_default.sql ({0:N1} MB)." -f ((Get-Item data\fineract_default.sql).Length / 1MB))
Write-Host 'Copy the data folder next to the other laptop''s start.ps1 and run import-data.ps1 there.'
