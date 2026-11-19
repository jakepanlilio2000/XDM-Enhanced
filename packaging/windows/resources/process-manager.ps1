param(
    [string]$TargetDir = "",
    [string]$Action = "check",
    [int]$PidToWait = 0,
    [int]$TimeoutSec = 6
)

$appName = "xdm-app.exe"

function Get-TargetXdmProcess {
    param([string]$Dir)
    if ([string]::IsNullOrWhiteSpace($Dir) -eq $false) {
        $dirs = @($Dir)
    } else {
        $dirs = @("$env:LOCALAPPDATA\Programs\XDM Enhanced", "$env:ProgramFiles\XDM Enhanced")
    }
    $targets = $dirs | ForEach-Object { (Join-Path $_ $appName).ToLower() }

    $procs = Get-CimInstance Win32_Process -Filter "Name = '$appName'" -ErrorAction SilentlyContinue | Where-Object {
        $_.ExecutablePath -and ($targets -contains $_.ExecutablePath.ToLower())
    }
    return $procs | Select-Object -First 1
}

if ($Action -eq "check") {
    $proc = Get-TargetXdmProcess -Dir $TargetDir
    if ($proc) {
        [Console]::Out.Write("RUNNING:$($proc.ProcessId)")
    } else {
        [Console]::Out.Write("NOT_RUNNING")
    }
    exit 0
}

if ($Action -eq "shutdown") {
    $targetProc = if ($PidToWait -gt 0) {
        Get-Process -Id $PidToWait -ErrorAction SilentlyContinue
    } else {
        Get-TargetXdmProcess -Dir $TargetDir
    }

    if (-not $targetProc) {
        [Console]::Out.Write("CLOSED")
        exit 0
    }

    $pidToMonitor = if ($PidToWait -gt 0) { $PidToWait } else { $targetProc.ProcessId }

    # 1. Request graceful shutdown via loopback HTTP IPC
    try {
        Invoke-RestMethod -Uri "http://127.0.0.1:8597/shutdown" -Method Post -TimeoutSec 2 -ErrorAction SilentlyContinue | Out-Null
    } catch {}

    # 2. Invoke --shutdown argument via executable
    if ($TargetDir -and (Test-Path (Join-Path $TargetDir $appName))) {
        try {
            Start-Process -FilePath (Join-Path $TargetDir $appName) -ArgumentList "--shutdown" -ErrorAction SilentlyContinue | Out-Null
        } catch {}
    }

    # 3. Bounded wait for process to terminate cleanly
    $deadline = [System.DateTime]::UtcNow.AddSeconds($TimeoutSec)
    while ([System.DateTime]::UtcNow -lt $deadline) {
        Start-Sleep -Milliseconds 400
        $alive = Get-Process -Id $pidToMonitor -ErrorAction SilentlyContinue
        if (-not $alive) {
            [Console]::Out.Write("CLOSED")
            exit 0
        }
    }
    [Console]::Out.Write("STILL_RUNNING:$pidToMonitor")
    exit 0
}

if ($Action -eq "force") {
    $procToKill = if ($PidToWait -gt 0) {
        Get-Process -Id $PidToWait -ErrorAction SilentlyContinue
    } else {
        $p = Get-TargetXdmProcess -Dir $TargetDir
        if ($p) { Get-Process -Id $p.ProcessId -ErrorAction SilentlyContinue } else { $null }
    }
    if ($procToKill) {
        Stop-Process -Id $procToKill.Id -Force -ErrorAction SilentlyContinue
        Start-Sleep -Milliseconds 500
    }
    [Console]::Out.Write("TERMINATED")
    exit 0
}
