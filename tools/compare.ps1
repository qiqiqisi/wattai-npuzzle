param([string]$OutputDirectory)
$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location -LiteralPath $taskRoot
if (-not $OutputDirectory) { $OutputDirectory = 'results/compare-' + [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffZ') }
if (Test-Path -LiteralPath $OutputDirectory) { throw 'Refuse to overwrite an existing experiment suite' }
New-Item -ItemType Directory -Path $OutputDirectory | Out-Null
& (Join-Path $PSScriptRoot 'compile.ps1')
$taskManifest = @(Get-ChildItem src -Recurse -Filter '*.java' -File | Sort-Object FullName | ForEach-Object {
    [ordered]@{path=$_.FullName.Substring($taskRoot.Length+1).Replace('\','/'); sha256=(Get-FileHash $_.FullName -Algorithm SHA256).Hash}
})
$taskManifest | ConvertTo-Json -Depth 4 | Set-Content "$OutputDirectory/source-manifest.json" -Encoding UTF8
$taskRows = @()
foreach ($taskCase in 'teacher3','teacher4') {
    $taskInput = if ($taskCase -eq 'teacher3') { 'resources/problems_teacher_stage1.txt' } else { 'resources/problems3.txt' }
    $taskIndex = if ($taskCase -eq 'teacher3') { 0 } else { 3 }
    $taskRepeats = if ($taskCase -eq 'teacher3') { 3 } else { 1 }
    $taskTimeout = if ($taskCase -eq 'teacher3') { 5 } else { 10 }
    foreach ($taskAlgorithm in 'ASTAR','IDA') {
        foreach ($taskHeuristic in 'MISPLACED','MANHATTAN','DISJOINT_PATTERN') {
            for ($taskRepeat=1; $taskRepeat -le $taskRepeats; $taskRepeat++) {
                $taskLabel = "$taskCase-$taskAlgorithm-$taskHeuristic-$taskRepeat"
                $taskOutput = "$OutputDirectory/$taskLabel"
                $taskCache = "generated/$([IO.Path]::GetFileName($OutputDirectory))-$taskLabel"
                if (Test-Path $taskCache) { throw 'Cold comparison cache already exists' }
                & java -Xmx1024m '-Dwattai.fast=true' '-Dwattai.mode=BASIC' '-Dwattai.limit=0' "-Dwattai.cache=$taskCache" "-Dwattai.output=$taskOutput" -cp build-test stud.g01.runner.Stage3Benchmark $taskInput $taskIndex $taskAlgorithm $taskHeuristic 4443 $taskTimeout
                if ($LASTEXITCODE -ne 0) { throw 'Comparison parent failed' }
                $taskRow = Import-Csv "$taskOutput/stage3.csv"
                $taskParent = Get-Content "$taskOutput/$($taskRow.run_id)-parent.txt"
                $taskWall = ($taskParent | Where-Object { $_.StartsWith('end_to_end_ms=') }).Split('=')[1]
                if ($taskRow.status -eq 'SOLVED') {
                    $taskLength = if ($taskCase -eq 'teacher3') {16} else {[int]$taskRow.solution_length}
                    & java -cp build-test stud.g01.runner.BenchmarkAcceptance "$taskOutput/stage3.csv" $taskInput $taskIndex $taskLength ($taskTimeout*1000)
                    if ($LASTEXITCODE -ne 0) { throw 'Comparison replay failed' }
                    if ($taskCase -eq 'teacher3' -and $taskAlgorithm -eq 'ASTAR' -and [double]$taskRow.elapsed_ms -ge 1000) { throw 'Teacher 1 second search target failed' }
                }
                $taskRow | Add-Member -NotePropertyMembers @{
                    dataset=$taskCase; repetition=$taskRepeat; parent_ms=$taskWall; timeout_s=$taskTimeout;
                    input_file=$taskInput; input_sha256=(Get-FileHash $taskInput -Algorithm SHA256).Hash;
                    cache_state= $(if ($taskHeuristic -eq 'DISJOINT_PATTERN') {'cold'} else {'not_applicable'});
                    source_sha256=(Get-FileHash "$OutputDirectory/source-manifest.json" -Algorithm SHA256).Hash
                }
                $taskRows += $taskRow
                $taskRows | Export-Csv "$OutputDirectory/comparison.csv" -NoTypeInformation -Encoding UTF8
            }
        }
    }
}
Write-Output "COMPARISON_COMPLETE: $OutputDirectory/comparison.csv"
