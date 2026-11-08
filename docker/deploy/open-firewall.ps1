# Run once as Administrator so other laptops on the LAN can reach the stack.
# Opens the ports for Private/Domain networks plus whatever profile the
# currently connected network has (Windows often marks Wi-Fi as Public).
#Requires -RunAsAdministrator
#   -IncludeDb also opens the database port (only useful with DB_BIND_ADDRESS=0.0.0.0).
param([int]$WebPort = 9002, [int]$ApiPort = 8443, [switch]$IncludeDb, [int]$DbPort = 3306)
$ErrorActionPreference = 'Stop'

$profiles = @('Private', 'Domain')
foreach ($p in Get-NetConnectionProfile) {
    $category = "$($p.NetworkCategory)" -replace 'DomainAuthenticated', 'Domain'
    if ($category -eq 'Public') {
        Write-Warning "'$($p.InterfaceAlias)' ($($p.Name)) is a Public network; opening the ports there too. Use this only on a trusted network, or switch it to Private in Settings > Network & internet."
    }
    if ($profiles -notcontains $category) { $profiles += $category }
}

$ports = @($WebPort, $ApiPort)
if ($IncludeDb) { $ports += $DbPort }
foreach ($port in $ports) {
    $name = "Score Management test stack TCP $port"
    Get-NetFirewallRule -DisplayName $name -ErrorAction SilentlyContinue | Remove-NetFirewallRule
    New-NetFirewallRule -DisplayName $name -Direction Inbound -Protocol TCP -LocalPort $port `
        -Action Allow -Profile $profiles | Out-Null
    Write-Host "Opened inbound TCP $port for profiles: $($profiles -join ', ')"
}
Write-Host 'Undo with: Get-NetFirewallRule -DisplayName "Score Management test stack*" | Remove-NetFirewallRule'
