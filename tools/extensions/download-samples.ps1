$ErrorActionPreference = 'Stop'
$sampleDirectory = Join-Path $PSScriptRoot '../../local/extensions-samples'
New-Item -ItemType Directory -Path $sampleDirectory -Force | Out-Null

$packages = @{
    'bitwarden.zip' = 'https://github.com/bitwarden/clients/releases/download/browser-v2026.10.2/dist-chrome-2026.10.2.zip'
    'ublock.zip' = 'https://github.com/gorhill/uBlock/releases/download/1.75.4/uBlock0_1.75.4.chromium.zip'
}
$storeIds = @{
    'cookies.crx' = 'fihnjjcciajhdojfnbdddfaoknhalnja'
    'darkreader.crx' = 'eimadpbcbfnmbkopoojfekhnkhdbieeh'
}
foreach ($entry in $storeIds.GetEnumerator()) {
    $query = [Uri]::EscapeDataString('id=' + $entry.Value + '&uc')
    $packages[$entry.Key] = 'https://clients2.google.com/service/update2/crx?response=redirect&prodversion=156.0.0.0&acceptformat=crx2,crx3&x=' + $query
}
foreach ($entry in $packages.GetEnumerator()) {
    $target = Join-Path $sampleDirectory $entry.Key
    Invoke-WebRequest -Uri $entry.Value -OutFile $target
    if ((Get-Item -LiteralPath $target).Length -lt 32) {
        throw "The official source did not return a package: $($entry.Key)"
    }
    Get-FileHash -LiteralPath $target -Algorithm SHA256 | Select-Object Path,Hash
}
