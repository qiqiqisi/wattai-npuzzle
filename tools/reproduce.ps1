param([switch]$RunHard)
$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location -LiteralPath $taskRoot
$sources = @(Get-ChildItem -LiteralPath 'src','tests' -Recurse -Filter '*.java' -File | ForEach-Object FullName)
& javac -encoding UTF-8 --release 19 -d build-test $sources
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed' }
foreach ($test in 'PuzzleFoundationTest','PuzzleStage1Test','PuzzleStage2Test','PuzzleStage3Test','PuzzleCloseoutTest','PuzzleAlternativePdbTest') {
    & java -Xmx1024m '-Dwattai.fast=true' -cp build-test $test
    if ($LASTEXITCODE -ne 0) { throw "$test failed" }
}
if ($RunHard) {
    $taskRun = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffZ')
    $taskCache = "generated/pdb-cold-$taskRun"
    if (Test-Path -LiteralPath $taskCache) { throw 'Cold-run cache already exists; refuse to reuse it' }
    & java -Xmx1024m '-Dwattai.fast=true' '-Dwattai.mode=BASIC' '-Dwattai.limit=0' "-Dwattai.cache=$taskCache" "-Dwattai.output=results/run-$taskRun" -cp build-test stud.g01.runner.Stage3Benchmark resources/problems3.txt 9 IDA DISJOINT_PATTERN 555T 60
    if ($LASTEXITCODE -ne 0) { throw 'Benchmark process failed' }
    Write-Output 'Inspect the emitted status and snapshot; a successful parent exit does not imply SOLVED.'
}
