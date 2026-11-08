param(
    [Parameter(Mandatory = $true)][string]$ProfileRoot,
    [switch]$OrphansOnly
)

$ErrorActionPreference = 'Stop'
$workspaceRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$allowedRoot = [IO.Path]::GetFullPath((Join-Path $workspaceRoot 'build/native-tests')).TrimEnd('\')
$requestedRoot = [IO.Path]::GetFullPath($ProfileRoot).TrimEnd('\')
if (-not ($requestedRoot.Equals($allowedRoot, [StringComparison]::OrdinalIgnoreCase) -or
        $requestedRoot.StartsWith($allowedRoot + '\', [StringComparison]::OrdinalIgnoreCase))) {
    throw 'Only browser processes using this workspace native-test profiles may be stopped.'
}

function Get-TestProfile([string]$CommandLine) {
    if (-not $CommandLine) { return $null }
    $argument = [regex]::Match($CommandLine, '(?:"--user-data-dir=([^"]+)"|--user-data-dir="([^"]+)"|--user-data-dir=([^\s"]+))')
    if (-not $argument.Success) { return $null }
    $value = @($argument.Groups | Select-Object -Skip 1 | Where-Object Success)[0].Value
    $profilePath = [IO.Path]::GetFullPath($value).TrimEnd('\')
    if ($profilePath.Equals($requestedRoot, [StringComparison]::OrdinalIgnoreCase) -or
            $profilePath.StartsWith($requestedRoot + '\', [StringComparison]::OrdinalIgnoreCase)) {
        return $profilePath
    }
    return $null
}

$processes = @(Get-CimInstance Win32_Process)
$byId = @{}
foreach ($processRecord in $processes) { $byId[$processRecord.ProcessId] = $processRecord }
$candidates = @(
    foreach ($processRecord in $processes) {
        if ($processRecord.Name -eq 'msedgewebview2.exe') {
            $profilePath = Get-TestProfile $processRecord.CommandLine
            if ($profilePath) {
                $processRecord | Add-Member -NotePropertyName TestProfile -NotePropertyValue $profilePath -PassThru
            }
        }
    }
)

$stopped = 0
foreach ($group in ($candidates | Group-Object TestProfile)) {
    if ($OrphansOnly) {
        $groupIds = @($group.Group | ForEach-Object ProcessId)
        $hasLiveHost = $false
        foreach ($processRecord in $group.Group) {
            $parent = $byId[$processRecord.ParentProcessId]
            if ($parent -and $parent.ProcessId -notin $groupIds -and
                    $parent.CreationDate -le $processRecord.CreationDate) {
                $hasLiveHost = $true
                break
            }
        }
        if ($hasLiveHost) { continue }
    }
    foreach ($processRecord in $group.Group) {
        # Recheck identity before stopping a PID that may already have exited.
        $current = Get-CimInstance Win32_Process -Filter "ProcessId = $($processRecord.ProcessId)"
        if ($current -and $current.Name -eq 'msedgewebview2.exe' -and
                $current.CreationDate -eq $processRecord.CreationDate -and
                (Get-TestProfile $current.CommandLine)) {
            try {
                Stop-Process -Id $current.ProcessId -Force -ErrorAction Stop
                $stopped++
            } catch {
                if (Get-Process -Id $current.ProcessId -ErrorAction SilentlyContinue) { throw }
            }
        }
    }
}
if ($stopped -gt 0) { Write-Output "Stopped $stopped WebView2 process(es) for disposable native-test profiles." }
