# Run once as Administrator so other laptops on the LAN can reach the stack.
# Opens the ports for Private/Domain networks plus whatever profile the
# currently connected network has (Windows often marks Wi-Fi as Public).
#Requires -RunAsAdministrator
param([int]$WebPort = 9002, [int]$ApiPort = 8443)
$ErrorActionPreference = 'Stop'

$profiles = @('Private', 'Domain')
foreach ($p in Get-NetConnectionProfile) {
    $category = "$($p.NetworkCategory)" -replace 'DomainAuthenticated', 'Domain'
    if ($category -eq 'Public') {
        Write-Warning "'$($p.InterfaceAlias)' ($($p.Name)) is a Public network; opening the ports there too. Use this only on a trusted network, or switch it to Private in Settings > Network & internet."
    }
    if ($profiles -notcontains $category) { $profiles += $category }
}

foreach ($port in @($WebPort, $ApiPort)) {
    $name = "Score Management test stack TCP $port"
    Get-NetFirewallRule -DisplayName $name -ErrorAction SilentlyContinue | Remove-NetFirewallRule
    New-NetFirewallRule -DisplayName $name -Direction Inbound -Protocol TCP -LocalPort $port `
        -Action Allow -Profile $profiles | Out-Null
    Write-Host "Opened inbound TCP $port for profiles: $($profiles -join ', ')"
}
Write-Host 'Undo with: Get-NetFirewallRule -DisplayName "Score Management test stack*" | Remove-NetFirewallRule'
