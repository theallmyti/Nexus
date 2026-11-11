# run-and-profile.ps1
# Runs pc-monitor-agent and pc-monitor-agent tray, samples RAM & CPU every 10 seconds for 5 minutes (30 samples).

$bin = "D:\Random project\MS\agent\target\release\pc-monitor-agent.exe"
$config = "C:\ProgramData\PCMonitor\config.toml"

Write-Host "Starting Nexus Agent..."
$agentProc = Start-Process -FilePath $bin -ArgumentList "`"$config`"" -PassThru
Write-Host "Started Agent PID: $($agentProc.Id)"

Write-Host "Starting Nexus Tray..."
$trayProc = Start-Process -FilePath $bin -ArgumentList "tray" -PassThru
Write-Host "Started Tray PID: $($trayProc.Id)"

$samples = @()
$durationSecs = 300 # 5 minutes
$intervalSecs = 10
$iterations = [int]($durationSecs / $intervalSecs)

Write-Host "Profiling for $durationSecs seconds ($iterations samples at ${intervalSecs}s intervals)..."

for ($i = 1; $i -le $iterations; $i++) {
    Start-Sleep -Seconds $intervalSecs
    
    $p1 = Get-Process -Id $agentProc.Id -ErrorAction SilentlyContinue
    $p2 = Get-Process -Id $trayProc.Id -ErrorAction SilentlyContinue

    $p1RamMB = if ($p1) { [math]::Round($p1.WorkingSet64 / 1MB, 2) } else { 0 }
    $p2RamMB = if ($p2) { [math]::Round($p2.WorkingSet64 / 1MB, 2) } else { 0 }
    $totalRamMB = [math]::Round($p1RamMB + $p2RamMB, 2)

    $sample = [PSCustomObject]@{
        ElapsedSec = $i * $intervalSecs
        AgentRamMB = $p1RamMB
        TrayRamMB  = $p2RamMB
        TotalRamMB = $totalRamMB
        AgentAlive = [bool]$p1
        TrayAlive  = [bool]$p2
    }
    $samples += $sample

    Write-Host ("[{0,3}s] Agent: {1,5} MB | Tray: {2,5} MB | Total: {3,5} MB" -f ($i * $intervalSecs), $p1RamMB, $p2RamMB, $totalRamMB)
}

# Final metrics
$p1Final = Get-Process -Id $agentProc.Id -ErrorAction SilentlyContinue
$p2Final = Get-Process -Id $trayProc.Id -ErrorAction SilentlyContinue

$agentCpu = if ($p1Final) { $p1Final.CPU } else { 0 }
$trayCpu = if ($p2Final) { $p2Final.CPU } else { 0 }

Write-Host "`n=== PROFILING RESULTS (5 Minutes) ==="
$avgRam = ($samples | Measure-Object -Property TotalRamMB -Average).Average
$maxRam = ($samples | Measure-Object -Property TotalRamMB -Maximum).Maximum
$avgAgentRam = ($samples | Measure-Object -Property AgentRamMB -Average).Average
$avgTrayRam = ($samples | Measure-Object -Property TrayRamMB -Average).Average

Write-Host ("Average Total RAM : {0:N2} MB" -f $avgRam)
Write-Host ("Peak Total RAM    : {0:N2} MB" -f $maxRam)
Write-Host ("Average Agent RAM : {0:N2} MB" -f $avgAgentRam)
Write-Host ("Average Tray RAM  : {0:N2} MB" -f $avgTrayRam)
Write-Host ("Agent CPU Time    : {0:N2} seconds over 300s ({1:P2} average CPU)" -f $agentCpu, ($agentCpu / $durationSecs))
Write-Host ("Tray CPU Time     : {0:N2} seconds over 300s ({1:P2} average CPU)" -f $trayCpu, ($trayCpu / $durationSecs))

# Save results to json for reporting
$samples | Export-Clixml -Path "D:\Random project\MS\dist\profile-results.xml"

# Keep running or exit? The user requested to run it and report back with data.
Write-Host "Profiling run complete. Processes left running."
