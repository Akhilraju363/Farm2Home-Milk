<#
.SYNOPSIS
    One-click local development startup for the Farm2Home-Milk microservices platform.

.DESCRIPTION
    Verifies prerequisites (Java, Maven, Node, PostgreSQL), builds the backend only when
    sources have changed since the last successful build, then starts every Spring Boot
    service in dependency order - Config Server, then Eureka, then the independent business
    microservices, then the API Gateway - waiting on each one's actuator health endpoint
    before moving on. Finally installs/starts the React frontend and opens the browser.

    All startup logic lives here by design (see start-all.bat, which only invokes this
    script) so there is exactly one place to read, test, and fix the automation.

.PARAMETER SkipBuild
    Skip the "mvn clean install" step even if sources look newer than the last build.
    Useful when you know the jars are current and just want a fast restart.

.PARAMETER HealthTimeoutSeconds
    How long to wait for each service's health check before declaring it failed.

.NOTES
    Exit codes: 0 = success, 1 = missing prerequisite, 2 = backend build failed,
    3 = Config Server/Discovery failed, 4 = a microservice failed, 5 = API Gateway failed,
    6 = frontend failed to become reachable.
#>

[CmdletBinding()]
param(
    [switch]$SkipBuild,
    [int]$HealthTimeoutSeconds = 120
)

# Deliberately left at the default 'Continue' rather than 'Stop': under 'Stop', PS 5.1
# wraps every stderr line from a native command (java/mvn/npm all write normal, non-error
# output to stderr) into a terminating exception, which would make e.g. the Java version
# check fail even when Java is correctly installed. Cmdlets that must fail loudly pass
# -ErrorAction Stop explicitly instead.

# ---------------------------------------------------------------------------
# Project layout - resolved from this script's own location so the project
# can live anywhere (any drive, any username, any IDE's working directory)
# without a single hardcoded path. Works identically from a plain terminal,
# a VS Code integrated terminal, or Eclipse's "Run External Tool".
# ---------------------------------------------------------------------------
$ProjectRoot = $PSScriptRoot
$BackendDir  = Join-Path $ProjectRoot 'backend'
$FrontendDir = Join-Path $ProjectRoot 'frontend'
$LogsDir     = Join-Path $ProjectRoot 'logs'
$PidFile     = Join-Path $LogsDir '.session-pids.json'
$BuildStamp  = Join-Path $LogsDir '.build-stamp'

$script:StartedProcesses = @()

# ---------------------------------------------------------------------------
# Console output helpers
# ---------------------------------------------------------------------------
function Write-Section { param([string]$Message) Write-Host "`n$Message" -ForegroundColor Magenta }
function Write-Step    { param([string]$Message) Write-Host $Message -ForegroundColor Cyan }
function Write-Ok      { param([string]$Message) Write-Host "  [OK]   $Message" -ForegroundColor Green }
function Write-WarnMsg { param([string]$Message) Write-Host "  [WARN] $Message" -ForegroundColor Yellow }
function Write-Fail    { param([string]$Message) Write-Host "  [FAIL] $Message" -ForegroundColor Red }

# ---------------------------------------------------------------------------
# Persist what we started so stop-all-services.ps1 can find it later and so
# a failed run still leaves a record of whatever did come up.
# ---------------------------------------------------------------------------
function Save-PidFile {
    $script:StartedProcesses | ConvertTo-Json -Depth 3 | Set-Content -Path $PidFile -Encoding UTF8
}

function Register-StartedProcess {
    param([string]$Name, [int]$ProcessId, [int]$Port, [string]$LogFile)
    $script:StartedProcesses += [PSCustomObject]@{
        Name      = $Name
        ProcessId = $ProcessId
        Port      = $Port
        LogFile   = $LogFile
        StartedAt = (Get-Date).ToString('o')
    }
    Save-PidFile
}

# ---------------------------------------------------------------------------
# Prerequisite checks
# ---------------------------------------------------------------------------
function Test-TcpPort {
    param([string]$HostName, [int]$Port, [int]$TimeoutMs = 1500)
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $result = $client.BeginConnect($HostName, $Port, $null, $null)
        $connected = $result.AsyncWaitHandle.WaitOne($TimeoutMs)
        if ($connected -and $client.Connected) { return $true }
        return $false
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

function Test-Prerequisites {
    $allOk = $true

    try {
        $javaOutput = (& java -version 2>&1 | Out-String)
        if ($javaOutput -match '"(\d+)') {
            $majorVersion = [int]$Matches[1]
            if ($majorVersion -ge 21) {
                Write-Ok "Java $majorVersion detected"
            } else {
                Write-WarnMsg "Java $majorVersion detected (Java 21+ is required by this project)"
            }
        } else {
            Write-Ok "Java detected"
        }
    } catch {
        Write-Fail "Java not found on PATH. Install JDK 21 and re-run."
        $allOk = $false
    }

    if (Get-Command mvn -ErrorAction SilentlyContinue) {
        Write-Ok "Maven detected"
    } else {
        Write-Fail "Maven not found on PATH. Install Maven 3.9+ and re-run."
        $allOk = $false
    }

    if (Get-Command node -ErrorAction SilentlyContinue) {
        $nodeVersion = (& node -v 2>&1)
        Write-Ok "Node $nodeVersion detected"
    } else {
        Write-Fail "Node not found on PATH. Install Node 20+ and re-run."
        $allOk = $false
    }

    # No universal CLI is guaranteed to exist for a native install vs a Docker container,
    # so a raw TCP probe on the default port is the most portable "is it up" signal.
    if (Test-TcpPort -HostName 'localhost' -Port 5432) {
        Write-Ok "PostgreSQL running"
    } else {
        Write-Fail "PostgreSQL is not running."
        $allOk = $false
    }

    # Not a hard requirement (kept non-blocking since it wasn't part of the original
    # prerequisite list), but order/payment/delivery/notification-service all depend on
    # Kafka and were observed, in testing, to hang indefinitely at startup without it -
    # worth a loud warning rather than a silent, confusing health-check timeout later.
    if (Test-TcpPort -HostName 'localhost' -Port 9092) {
        Write-Ok "Kafka running"
    } else {
        Write-WarnMsg "Kafka not detected on port 9092 - order-service, payment-service, delivery-service and notification-service may hang or fail their health check without it"
    }

    return $allOk
}

# ---------------------------------------------------------------------------
# Health checks - polling loops, never a single blind sleep.
# ---------------------------------------------------------------------------
function New-BasicAuthHeader {
    param([string]$UserName, [string]$Password)
    $pair = "${UserName}:${Password}"
    $bytes = [System.Text.Encoding]::ASCII.GetBytes($pair)
    return @{ Authorization = 'Basic ' + [System.Convert]::ToBase64String($bytes) }
}

function Wait-ForHealth {
    param(
        [Parameter(Mandatory)][string]$Url,
        [int]$TimeoutSeconds = 90,
        [int]$PollIntervalSeconds = 2,
        [hashtable]$Headers,
        # Some services (Eureka) are only required to be "available" per spec, not
        # necessarily returning a parsed UP status - even a 401 proves the port is live.
        [switch]$AnyResponseCounts
    )
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        try {
            $reqParams = @{ Uri = $Url; TimeoutSec = 5; UseBasicParsing = $true; ErrorAction = 'Stop' }
            if ($Headers) { $reqParams['Headers'] = $Headers }
            $response = Invoke-WebRequest @reqParams
            if ($AnyResponseCounts) { return $true }
            # -UseBasicParsing returns .Content as a raw byte[] instead of a string whenever
            # the response's content-type isn't one PowerShell 5.1 recognizes as text - which
            # is exactly Spring Boot actuator's application/vnd.spring-boot.actuator.v3+json.
            # ConvertFrom-Json on a byte[] fails silently, so this must be decoded first.
            $content = $response.Content
            if ($content -is [byte[]]) { $content = [System.Text.Encoding]::UTF8.GetString($content) }
            $status = ($content | ConvertFrom-Json -ErrorAction SilentlyContinue).status
            if ($status -eq 'UP') { return $true }
        } catch [System.Net.WebException] {
            if ($AnyResponseCounts -and $_.Exception.Response) { return $true }
        } catch {
            # Connection refused / DNS not ready yet - keep polling until the deadline.
        }
        Start-Sleep -Seconds $PollIntervalSeconds
    } while ((Get-Date) -lt $deadline)
    return $false
}

# ---------------------------------------------------------------------------
# Backend build - skipped when nothing under backend/**/src or a pom.xml is
# newer than the stamp left by the last successful build.
# ---------------------------------------------------------------------------
function Test-BuildRequired {
    if (-not (Test-Path $BuildStamp)) { return $true }
    $stampTime = (Get-Item $BuildStamp).LastWriteTimeUtc
    $newest = Get-ChildItem -Path $BackendDir -Recurse -Include '*.java', 'pom.xml' -File -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -notmatch '\\target\\' } |
        Measure-Object -Property LastWriteTimeUtc -Maximum
    if (-not $newest.Maximum) { return $true }
    return $newest.Maximum -gt $stampTime
}

function Invoke-BackendBuild {
    if ($SkipBuild) {
        Write-Ok "Skipping build (-SkipBuild)"
        return $true
    }
    if (-not (Test-BuildRequired)) {
        Write-Ok "Backend already up to date - skipping build"
        return $true
    }

    Write-Step "Building backend (mvn clean install -DskipTests)..."
    $buildLog = Join-Path $LogsDir 'build.log'
    Push-Location $BackendDir
    try {
        & mvn clean install -DskipTests 2>&1 | Tee-Object -FilePath $buildLog | Out-Null
        $exitCode = $LASTEXITCODE
    } finally {
        Pop-Location
    }

    if ($exitCode -ne 0) {
        Write-Fail "Backend build failed (exit code $exitCode). See $buildLog"
        return $false
    }

    New-Item -ItemType File -Path $BuildStamp -Force | Out-Null
    Write-Ok "Backend build complete"
    return $true
}

# ---------------------------------------------------------------------------
# Service launch
# ---------------------------------------------------------------------------
function Resolve-ServiceJar {
    param([string]$ModuleName)
    $targetDir = Join-Path $BackendDir "$ModuleName\target"
    return Get-ChildItem -Path $targetDir -Filter '*.jar' -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notmatch '(sources|javadoc)\.jar$' } |
        Select-Object -First 1
}

function Start-BackendService {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][int]$Port,
        [string]$HealthPath = '/actuator/health',
        [hashtable]$AuthHeaders,
        [switch]$AnyResponseCounts,
        [int]$TimeoutSeconds = $HealthTimeoutSeconds,
        # Lets a call site pass extra -D system properties, e.g. a server.port override for a
        # service whose documented default port collides with something else already running on
        # a given developer's machine (add a machine-local override at the call site, not here).
        # $Port must be kept in sync with whatever port is actually passed here - it's what
        # Wait-ForHealth polls.
        [string[]]$ExtraJavaArgs = @()
    )

    Write-Step "Starting $Name..."
    $logFile = Join-Path $LogsDir "$Name.log"

    # Guard against a $null caught by the [string[]] type (e.g. a caller-side if/else that
    # collapsed an empty-array branch to $null) - a null element here would otherwise poison
    # Start-Process's -ArgumentList below.
    if (-not $ExtraJavaArgs) { $ExtraJavaArgs = @() }

    $jar = Resolve-ServiceJar -ModuleName $Name
    if (-not $jar) {
        Write-Fail "$Name : no jar found under backend\$Name\target - did the build succeed?"
        Write-Host "  Log    : $logFile" -ForegroundColor Red
        return $false
    }

    # Explicit -Dlogging.file.path makes every service write into the single shared
    # logs/ folder regardless of the working directory it happens to be launched from.
    $javaArgs = @(
        "-Dlogging.file.path=`"$LogsDir`""
    ) + $ExtraJavaArgs + @(
        '-jar'
        "`"$($jar.FullName)`""
    )

    # Launched directly (no shell wrapper) so java.exe owns its own console window -
    # visible to the developer, and closeable/stoppable like any normal windowed app.
    $proc = Start-Process -FilePath 'java' -ArgumentList $javaArgs `
        -WorkingDirectory (Join-Path $BackendDir $Name) -WindowStyle Normal -PassThru

    $healthUrl = "http://localhost:$Port$HealthPath"
    $waitParams = @{ Url = $healthUrl; TimeoutSeconds = $TimeoutSeconds; AnyResponseCounts = $AnyResponseCounts }
    if ($AuthHeaders) { $waitParams['Headers'] = $AuthHeaders }
    $healthy = Wait-ForHealth @waitParams

    if ($healthy) {
        Write-Ok "$Name started (PID $($proc.Id), port $Port)"
        Register-StartedProcess -Name $Name -ProcessId $proc.Id -Port $Port -LogFile $logFile
        return $true
    }

    Write-Fail "$Name failed to become healthy"
    Write-Host "  Reason : no UP response from $healthUrl within $TimeoutSeconds s" -ForegroundColor Red
    Write-Host "  Log    : $logFile" -ForegroundColor Red
    # Still tracked so stop-all can clean up the window even though the health check failed.
    Register-StartedProcess -Name $Name -ProcessId $proc.Id -Port $Port -LogFile $logFile
    return $false
}

function Start-Frontend {
    Write-Step "Starting frontend..."
    $logFile = Join-Path $LogsDir 'frontend.log'

    if (-not (Test-Path (Join-Path $FrontendDir 'node_modules'))) {
        Write-Host "  Installing frontend dependencies (npm install)..." -ForegroundColor Yellow
        $installLog = Join-Path $LogsDir 'npm-install.log'
        Push-Location $FrontendDir
        try {
            & npm install 2>&1 | Tee-Object -FilePath $installLog | Out-Null
            $installExit = $LASTEXITCODE
        } finally {
            Pop-Location
        }
        if ($installExit -ne 0) {
            Write-Fail "npm install failed. See $installLog"
            return $false
        }
    }

    # Vite has no built-in file logging, so this window's own shell tees output to
    # logs/frontend.log itself, in contrast to the backend services (Logback already
    # writes their log files - redirecting the console too would be a second writer
    # on the same file).
    $command = "Set-Location -LiteralPath `"$FrontendDir`"; " +
        "`$Host.UI.RawUI.WindowTitle = 'Farm2Home - frontend'; " +
        "npm run dev 2>&1 | Tee-Object -FilePath `"$logFile`""
    $proc = Start-Process -FilePath 'powershell.exe' -ArgumentList @('-NoExit', '-Command', $command) -WindowStyle Normal -PassThru

    $healthy = $false
    $deadline = (Get-Date).AddSeconds(60)
    while ((Get-Date) -lt $deadline) {
        if (Test-TcpPort -HostName 'localhost' -Port 3000 -TimeoutMs 800) { $healthy = $true; break }
        Start-Sleep -Seconds 2
    }

    if ($healthy) {
        Write-Ok "Frontend started (PID $($proc.Id), http://localhost:3000)"
        Register-StartedProcess -Name 'frontend' -ProcessId $proc.Id -Port 3000 -LogFile $logFile
        Start-Process 'http://localhost:3000'
        return $true
    }

    Write-Fail "Frontend did not become reachable on port 3000 within 60s"
    Write-Host "  Log    : $logFile" -ForegroundColor Red
    Register-StartedProcess -Name 'frontend' -ProcessId $proc.Id -Port 3000 -LogFile $logFile
    return $false
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
Write-Host '=============================================' -ForegroundColor Magenta
Write-Host ' Farm2Home-Milk - Local Development Startup' -ForegroundColor Magenta
Write-Host '=============================================' -ForegroundColor Magenta

New-Item -ItemType Directory -Path $LogsDir -Force | Out-Null
$script:StartedProcesses = @()

Write-Section 'Step 1/6: Checking prerequisites'
if (-not (Test-Prerequisites)) {
    Write-Host "`nOne or more prerequisites are missing. Fix the issues above and re-run." -ForegroundColor Red
    exit 1
}

Write-Section 'Step 2/6: Building backend'
if (-not (Invoke-BackendBuild)) {
    exit 2
}

Write-Section 'Step 3/6: Starting Spring Cloud infrastructure'
$configAuth = New-BasicAuthHeader -UserName 'configuser' -Password 'config@secret'
$configOk = Start-BackendService -Name 'config-server' -Port 8888 -AuthHeaders $configAuth -TimeoutSeconds 90
if (-not $configOk) {
    Write-Host "`nConfig Server failed to start. Stopping startup." -ForegroundColor Red
    exit 3
}

# Spec only requires Eureka to be reachable, not authenticated - an unauthenticated 401
# still proves the listener is up, so no credentials are needed for this particular wait.
$discoveryOk = Start-BackendService -Name 'discovery-service' -Port 8761 -AnyResponseCounts -TimeoutSeconds 90
if (-not $discoveryOk) {
    Write-Host "`nDiscovery Service (Eureka) failed to start. Stopping startup." -ForegroundColor Red
    exit 3
}

Write-Section 'Step 4/6: Starting business microservices'
$microservices = @(
    @{ Name = 'auth-service';          Port = 8081 },
    @{ Name = 'customer-service';      Port = 8082 },
    @{ Name = 'subscription-service';  Port = 8083 },
    @{ Name = 'farm-service';          Port = 8087 },
    @{ Name = 'inventory-service';     Port = 8089 },
    @{ Name = 'production-service';    Port = 8088 },
    @{ Name = 'order-service';         Port = 8084 },
    @{ Name = 'payment-service';       Port = 8085 },
    @{ Name = 'delivery-service';      Port = 8086 },
    @{ Name = 'notification-service';  Port = 8090 },
    @{ Name = 'dashboard-service';     Port = 8092 },
    @{ Name = 'reports-service';       Port = 8093 }
)

$failedServices = @()
foreach ($service in $microservices) {
    # NOTE: assigning the output of an if/else directly (`$x = if(...){a}else{@()}`) collapses
    # an empty-array branch to $null, which then poisons Start-Process's -ArgumentList with a
    # null element. Building the array imperatively avoids that collapse.
    $extraArgs = @()
    if ($service.ContainsKey('ExtraJavaArgs')) { $extraArgs = $service.ExtraJavaArgs }
    $ok = Start-BackendService -Name $service.Name -Port $service.Port -ExtraJavaArgs $extraArgs
    if (-not $ok) { $failedServices += $service.Name }
}

if ($failedServices.Count -gt 0) {
    Write-Host "`nThe following services failed to start: $($failedServices -join ', ')" -ForegroundColor Red
    Write-Host 'API Gateway and frontend will not be started. Check the logs above, then re-run.' -ForegroundColor Red
    exit 4
}

Write-Section 'Step 5/6: Starting API Gateway'
$gatewayOk = Start-BackendService -Name 'api-gateway' -Port 8080 -TimeoutSeconds 90
if (-not $gatewayOk) {
    Write-Host "`nAPI Gateway failed to start." -ForegroundColor Red
    exit 5
}

Write-Section 'Step 6/6: Starting frontend'
$frontendOk = Start-Frontend
if (-not $frontendOk) {
    exit 6
}

Write-Section 'All Services Started Successfully'
Write-Host "  API Gateway : http://localhost:8080"
Write-Host "  Frontend    : http://localhost:3000"
Write-Host "  Eureka      : http://localhost:8761"
Write-Host "  Logs        : $LogsDir"
Write-Host "`nFinal validation:" -ForegroundColor Magenta
Write-Ok 'PostgreSQL detected'
Write-Ok 'Java detected'
Write-Ok 'Maven detected'
Write-Ok 'Node detected'
Write-Ok 'Backend builds'
Write-Ok 'Config Server started'
Write-Ok 'Discovery started'
Write-Ok 'All microservices started'
Write-Ok 'Gateway started'
Write-Ok 'Frontend started'
Write-Ok 'Browser opened'
Write-Ok 'Logs generated'
Write-Host "`nRun stop-all.bat to shut everything down.`n"

exit 0
