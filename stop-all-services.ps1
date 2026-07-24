<#
.SYNOPSIS
    Stops everything started by start-all-services.ps1, and only that.

.DESCRIPTION
    Reads logs/.session-pids.json (written by start-all-services.ps1), and for every
    recorded process: confirms a process with that PID still exists AND is still the
    kind of process we started (java for backend services, powershell for the frontend
    dev-server wrapper) before touching it - a recycled PID that now belongs to some
    unrelated process is left alone. Tries a graceful close first, then falls back to a
    forced tree-kill (backend windows rarely honor a plain graceful close on Windows,
    and the frontend wrapper has child node processes that need the whole tree taken
    down). Never touches a Java process this session didn't start itself.
#>

[CmdletBinding()]
param()

# Left at the default 'Continue' - under 'Stop', PS 5.1 turns every stderr line from a
# native command (taskkill included) into a terminating exception. Cmdlets that must fail
# loudly (e.g. parsing the PID file) pass -ErrorAction Stop explicitly instead.

$ProjectRoot = $PSScriptRoot
$LogsDir = Join-Path $ProjectRoot 'logs'
$PidFile = Join-Path $LogsDir '.session-pids.json'

function Write-Ok   { param([string]$Message) Write-Host "  [OK]   $Message" -ForegroundColor Green }
function Write-Skip { param([string]$Message) Write-Host "  [SKIP] $Message" -ForegroundColor Yellow }
function Write-Fail { param([string]$Message) Write-Host "  [FAIL] $Message" -ForegroundColor Red }

Write-Host '=============================================' -ForegroundColor Magenta
Write-Host ' Farm2Home-Milk - Stopping Local Services' -ForegroundColor Magenta
Write-Host '=============================================' -ForegroundColor Magenta

if (-not (Test-Path $PidFile)) {
    Write-Host "`nNo record of a running session ($PidFile not found). Nothing to stop." -ForegroundColor Yellow
    exit 0
}

try {
    $tracked = Get-Content -Path $PidFile -Raw -ErrorAction Stop | ConvertFrom-Json -ErrorAction Stop
    # ConvertFrom-Json already returns a proper array for a JSON array of 3+ items - wrapping
    # that again in @() would nest it into a single-element array containing the whole array
    # (every property access then silently space-joins all entries together). @() is only
    # needed to normalize the opposite case: a one-item JSON array, which ConvertFrom-Json
    # unwraps down to a bare scalar object instead of a one-element array.
    if ($tracked -isnot [System.Array]) { $tracked = @($tracked) }
} catch {
    Write-Host "`nCould not read $PidFile ($($_.Exception.Message)). Nothing to stop." -ForegroundColor Yellow
    exit 0
}
if ($tracked.Count -eq 0) {
    Write-Host "`nSession record is empty. Nothing to stop." -ForegroundColor Yellow
    exit 0
}

function Stop-TrackedProcess {
    # ProcessId is intentionally untyped: ConvertFrom-Json can hand back a JSON number as
    # Int64/Double/etc. depending on its magnitude, and a strict [int] parameter can throw a
    # binding error on that mismatch instead of the harmless implicit conversion it should be.
    param([string]$Name, $ProcessId, [string]$ExpectedProcessName)

    $ProcessId = [int]"$ProcessId"
    $proc = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
    if (-not $proc) {
        Write-Skip "$Name (PID $ProcessId) already stopped"
        return
    }
    if ($proc.ProcessName -ne $ExpectedProcessName) {
        # The PID has been recycled by an unrelated process since this session started.
        Write-Skip "$Name (PID $ProcessId) no longer matches what we started - leaving it alone"
        return
    }

    # /T stops the whole process tree (needed for the frontend wrapper's child npm/node
    # processes); try a plain close first, then force if it's still alive shortly after.
    & taskkill /PID $ProcessId /T *> $null
    $deadline = (Get-Date).AddSeconds(5)
    while ((Get-Date) -lt $deadline -and (Get-Process -Id $ProcessId -ErrorAction SilentlyContinue)) {
        Start-Sleep -Milliseconds 500
    }

    if (Get-Process -Id $ProcessId -ErrorAction SilentlyContinue) {
        & taskkill /PID $ProcessId /T /F *> $null
    }

    if (Get-Process -Id $ProcessId -ErrorAction SilentlyContinue) {
        Write-Fail "$Name (PID $ProcessId) could not be stopped"
    } else {
        Write-Ok "$Name stopped"
    }
}

Write-Host ''
foreach ($entry in $tracked) {
    $expectedName = if ($entry.Name -eq 'frontend') { 'powershell' } else { 'java' }
    Stop-TrackedProcess -Name $entry.Name -ProcessId $entry.ProcessId -ExpectedProcessName $expectedName
}

Remove-Item -Path $PidFile -Force -ErrorAction SilentlyContinue
Write-Host "`nAll tracked services stopped.`n" -ForegroundColor Green
exit 0
