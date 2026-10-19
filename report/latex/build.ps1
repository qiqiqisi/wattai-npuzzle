param([switch]$RebuildFigures)
$ErrorActionPreference = 'Stop'
$taskSource = $PSScriptRoot
$taskRoot = [IO.Path]::GetFullPath((Join-Path $taskSource '..\..'))
$taskBuild = Join-Path $taskRoot 'generated\latex-template-build'
$taskTex = (Get-Command xelatex -ErrorAction Stop).Source
New-Item -ItemType Directory -Path $taskBuild -Force | Out-Null

if ($RebuildFigures) {
    $taskFigureSource = Join-Path $taskSource 'section1Figures'
    $taskFigureBuild = Join-Path $taskBuild 'figures'
    New-Item -ItemType Directory -Path $taskFigureBuild -Force | Out-Null
    Push-Location -LiteralPath $taskFigureSource
    try {
        foreach ($taskFigure in Get-ChildItem -LiteralPath $taskFigureSource -Filter '*.tex' -File | Sort-Object Name) {
            $taskFigureLog = Join-Path $taskFigureBuild ($taskFigure.BaseName + '-compile.txt')
            & $taskTex '-no-shell-escape' '-interaction=nonstopmode' '-halt-on-error' '-file-line-error' "-output-directory=$taskFigureBuild" $taskFigure.Name > $taskFigureLog
            if ($LASTEXITCODE -ne 0) {
                Get-Content -LiteralPath $taskFigureLog -Tail 25
                throw "Figure compilation failed: $($taskFigure.Name)"
            }
            Copy-Item -LiteralPath (Join-Path $taskFigureBuild ($taskFigure.BaseName + '.pdf')) -Destination $taskFigureSource
            Write-Output "FIGURE_PASS: $($taskFigure.BaseName)"
        }
    } finally {
        Pop-Location
    }
}

Push-Location -LiteralPath $taskSource
try {
    foreach ($taskPass in 1..3) {
        & $taskTex '-no-shell-escape' '-interaction=nonstopmode' '-halt-on-error' '-file-line-error' "-output-directory=$taskBuild" 'main.tex' > (Join-Path $taskBuild "compile-$taskPass.txt")
        $taskExit = $LASTEXITCODE
        if ($taskExit -ne 0) {
            Get-Content -LiteralPath (Join-Path $taskBuild "compile-$taskPass.txt") -Tail 25
            throw "XeLaTeX pass $taskPass failed with exit code $taskExit"
        }
        Write-Output "XELATEX_PASS_${taskPass}: exit=0"
    }
    $taskFinalLog = Get-Content -LiteralPath (Join-Path $taskBuild 'main.log') -Raw
    if ($taskFinalLog -match 'undefined references|undefined citations|Citation .* undefined|Reference .* undefined|destination with (the )?same identifier|Missing character:') {
        throw 'Final PDF has unresolved references, duplicate destinations, or missing characters. See generated/latex-template-build/main.log.'
    }
    Copy-Item -LiteralPath (Join-Path $taskBuild 'main.pdf') -Destination (Join-Path $taskSource 'main.pdf')
    Write-Output 'LATEX_REPORT_PASS: report/latex/main.pdf'
} finally {
    Pop-Location
}
