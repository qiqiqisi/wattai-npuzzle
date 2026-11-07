param([string]$Python = 'python')
$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$taskStamp = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffffffZ')
$taskStage = Join-Path $taskRoot ".publication-stage-optimization-$taskStamp"
if (Test-Path -LiteralPath $taskStage) { throw 'Refuse to reuse a worktree' }
# Build the committed version in a nested worktree. The original checkout is read-only evidence.
& git -c "safe.directory=$($taskRoot.Replace('\','/'))" -C $taskRoot worktree add --detach $taskStage HEAD
if ($LASTEXITCODE -ne 0) { throw 'Cannot create isolated worktree' }
Push-Location -LiteralPath $taskStage
try {
    $taskSuite = (& $Python tools/optimization_suite.py init).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Cannot initialize immutable suite' }
    & $Python -u tools/optimization_suite.py prepare --suite $taskSuite
    if ($LASTEXITCODE -ne 0) { throw 'Clean build/test gate failed' }
    & $Python -u tools/optimization_suite.py run --suite $taskSuite
    if ($LASTEXITCODE -ne 0) { throw 'Matrix stopped; preserve outputs and inspect the failure' }
    & $Python -u tools/optimization_diagnostics.py $taskSuite
    if ($LASTEXITCODE -ne 0) { throw 'Diagnostics failed; preserve evidence' }
    & $Python tools/analyze_optimization.py $taskSuite
    if ($LASTEXITCODE -ne 0) { throw 'Analysis failed' }
    & $Python tools/verify_optimization.py $taskSuite --output "$taskSuite/verification.json"
    if ($LASTEXITCODE -ne 0) { throw 'Verification failed' }
    Write-Output "OPTIMIZATION_VERIFIED: $taskStage/$taskSuite"
    Write-Output 'New reports are local to this worktree; no automatic commit or push.'
} finally { Pop-Location }
