param([switch]$RunHard, [switch]$RunWarm)
$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location -LiteralPath $taskRoot
& (Join-Path $PSScriptRoot 'compile.ps1')
foreach ($test in 'PuzzleFoundationTest','PuzzleStage1Test','PuzzleStage2Test','PuzzleStage3Test','PuzzleCloseoutTest','PuzzleAlternativePdbTest') {
    & java -Xmx1024m '-Dwattai.fast=true' -cp build-test $test
    if ($LASTEXITCODE -ne 0) { throw "$test failed" }
}
& java -Xmx1024m -cp build-test TeacherStage1Test
if ($LASTEXITCODE -ne 0) { throw 'Teacher stage 1 regression failed' }
& java -Xmx1024m -cp build-test BenchmarkAcceptanceTest
if ($LASTEXITCODE -ne 0) { throw 'Benchmark acceptance regression failed' }
if ($RunHard) {
    $taskRun = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffZ')
    $taskCache = "generated/pdb-cold-$taskRun"
    if (Test-Path -LiteralPath $taskCache) { throw 'Cold-run cache already exists; refuse to reuse it' }
    & java -Xmx1024m '-Dwattai.fast=true' '-Dwattai.mode=BASIC' '-Dwattai.limit=0' "-Dwattai.cache=$taskCache" "-Dwattai.output=results/run-$taskRun" -cp build-test stud.g01.runner.Stage3Benchmark resources/problems3.txt 9 IDA DISJOINT_PATTERN 555T 60
    if ($LASTEXITCODE -ne 0) { throw 'Benchmark process failed' }
    & java -Xmx1024m -cp build-test stud.g01.runner.BenchmarkAcceptance "results/run-$taskRun/stage3.csv" resources/problems3.txt 9 70 60000
    if ($LASTEXITCODE -ne 0) { throw 'Hard acceptance failed: CSV status, timing or path invalid' }
    $taskCpu = Get-CimInstance Win32_Processor | Select-Object -First 1 -ExpandProperty Name
    $taskJavaInfo = [System.Diagnostics.Process]::new()
    $taskJavaInfo.StartInfo = [System.Diagnostics.ProcessStartInfo]::new('java','-version')
    $taskJavaInfo.StartInfo.UseShellExecute = $false
    $taskJavaInfo.StartInfo.CreateNoWindow = $true
    $taskJavaInfo.StartInfo.RedirectStandardError = $true
    $taskJavaInfo.StartInfo.RedirectStandardOutput = $true
    $taskJavaInfo.Start() | Out-Null
    $taskJavaVersion = $taskJavaInfo.StandardError.ReadToEnd() + $taskJavaInfo.StandardOutput.ReadToEnd()
    $taskJavaInfo.WaitForExit()
    if ($taskJavaInfo.ExitCode -ne 0) { throw 'Cannot read Java version' }
    $taskJavaInfo.Dispose()
    $taskEnvironment = [ordered]@{
        run_id = $taskRun; cache_state = 'cold'; timeout_s = 60; heap = '-Xmx1024m';
        java = $taskJavaVersion;
        os = [Environment]::OSVersion.VersionString; cpu = $taskCpu;
        input_sha256 = (Get-FileHash resources/problems3.txt -Algorithm SHA256).Hash;
        base_commit = (& git rev-parse HEAD); source_tree_sha256 = ''
    }
    $taskSourceManifest = @(Get-ChildItem src -Recurse -Filter '*.java' -File | Sort-Object FullName | ForEach-Object {
        [ordered]@{path=$_.FullName.Substring($taskRoot.Length+1).Replace('\','/'); sha256=(Get-FileHash $_.FullName -Algorithm SHA256).Hash}
    })
    $taskSourceManifest | ConvertTo-Json -Depth 4 | Set-Content "results/run-$taskRun/source-manifest.json" -Encoding UTF8
    $taskEnvironment.source_tree_sha256 = (Get-FileHash "results/run-$taskRun/source-manifest.json" -Algorithm SHA256).Hash
    $taskEnvironment | ConvertTo-Json -Depth 4 | Set-Content "results/run-$taskRun/environment.json" -Encoding UTF8
    Write-Output "HARD_ACCEPTED: results/run-$taskRun/stage3.csv"
    if ($RunWarm) {
        & java -Xmx1024m '-Dwattai.fast=true' '-Dwattai.mode=BASIC' '-Dwattai.limit=0' "-Dwattai.cache=$taskCache" "-Dwattai.output=results/run-$taskRun-warm" -cp build-test stud.g01.runner.Stage3Benchmark resources/problems3.txt 9 IDA DISJOINT_PATTERN 555T 60
        if ($LASTEXITCODE -ne 0) { throw 'Warm benchmark process failed' }
        & java -Xmx1024m -cp build-test stud.g01.runner.BenchmarkAcceptance "results/run-$taskRun-warm/stage3.csv" resources/problems3.txt 9 70 60000
        if ($LASTEXITCODE -ne 0) { throw 'Warm hard acceptance failed' }
        $taskEnvironment.cache_state = 'warm'
        $taskEnvironment | ConvertTo-Json -Depth 4 | Set-Content "results/run-$taskRun-warm/environment.json" -Encoding UTF8
    }
} elseif ($RunWarm) {
    throw '-RunWarm requires -RunHard, so its cache has explicit provenance'
}
