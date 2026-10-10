param([string]$OutputDirectory = 'build-test')
$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
# Teacher sources use both GBK and UTF-8. Normalize only ignored build copies.
$taskBuild = Join-Path $taskRoot $OutputDirectory
$taskSource = Join-Path $taskBuild 'sources'
New-Item -ItemType Directory -Path $taskSource -Force | Out-Null
$taskUtf8Strict = [System.Text.UTF8Encoding]::new($false, $true)
$taskUtf8 = [System.Text.UTF8Encoding]::new($false)
$taskGbk = [System.Text.Encoding]::GetEncoding(936)
$taskSources = @()
foreach ($taskFile in Get-ChildItem -LiteralPath (Join-Path $taskRoot 'src'),(Join-Path $taskRoot 'tests') -Recurse -Filter '*.java' -File) {
    $taskBytes = [System.IO.File]::ReadAllBytes($taskFile.FullName)
    try { $taskText = $taskUtf8Strict.GetString($taskBytes) }
    catch [System.Text.DecoderFallbackException] { $taskText = $taskGbk.GetString($taskBytes) }
    $taskRelative = $taskFile.FullName.Substring($taskRoot.Length + 1)
    $taskDestination = Join-Path $taskSource $taskRelative
    New-Item -ItemType Directory -Path (Split-Path $taskDestination) -Force | Out-Null
    [System.IO.File]::WriteAllText($taskDestination, $taskText, $taskUtf8)
    $taskSources += $taskDestination
}
& javac -encoding UTF-8 --release 19 -d $taskBuild $taskSources
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed' }
Write-Output "COMPILE_PASS: --release 19, $($taskSources.Count) sources, $OutputDirectory"
