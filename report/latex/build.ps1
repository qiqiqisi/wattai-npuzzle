param()
$ErrorActionPreference = 'Stop'
$taskSource = $PSScriptRoot
$taskRoot = [IO.Path]::GetFullPath((Join-Path $taskSource '..\..'))
$taskBuild = Join-Path $taskRoot 'generated\latex-template-build'
$taskTex = (Get-Command xelatex -ErrorAction Stop).Source
New-Item -ItemType Directory -Path $taskBuild -Force | Out-Null
Push-Location -LiteralPath $taskSource
try {
    foreach ($taskPass in 1..2) {
        & $taskTex '-interaction=nonstopmode' '-halt-on-error' "-output-directory=$taskBuild" 'main.tex' > (Join-Path $taskBuild "compile-$taskPass.txt")
        $taskExit = $LASTEXITCODE
        if ($taskExit -ne 0) {
            Get-Content -LiteralPath (Join-Path $taskBuild "compile-$taskPass.txt") -Tail 25
            throw "XeLaTeX pass $taskPass failed with exit code $taskExit"
        }
        Write-Output "XELATEX_PASS_${taskPass}: exit=0"
    }
    Copy-Item -LiteralPath (Join-Path $taskBuild 'main.pdf') -Destination (Join-Path $taskSource 'NPuzzle-template.pdf')
    Write-Output 'LATEX_TEMPLATE_PASS: report/latex/NPuzzle-template.pdf'
} finally {
    Pop-Location
}
