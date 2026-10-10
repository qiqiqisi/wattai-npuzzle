param(
    [Parameter(Mandatory=$true)][string]$InputDocx,
    [Parameter(Mandatory=$true)][string]$OutputPdf,
    [switch]$UpdateFields
)
$ErrorActionPreference = 'Stop'
$taskRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$taskInput = (Resolve-Path -LiteralPath $InputDocx).Path
$taskPdf = [IO.Path]::GetFullPath((Join-Path $taskRoot $OutputPdf))
if (-not $taskPdf.StartsWith($taskRoot + [IO.Path]::DirectorySeparatorChar)) { throw 'Output must remain inside project' }
if ($UpdateFields -and -not $taskInput.StartsWith((Join-Path $taskRoot 'report') + [IO.Path]::DirectorySeparatorChar)) { throw 'Field updates are only allowed in generated report/' }
New-Item -ItemType Directory -Path (Split-Path $taskPdf) -Force | Out-Null
$taskWord = $null
$taskDoc = $null
try {
    $taskWord = New-Object -ComObject Word.Application
    $taskWord.Visible = $false
    $taskWord.DisplayAlerts = 0
    $taskWord.AutomationSecurity = 3
    $taskDoc = $taskWord.Documents.Open($taskInput,$false,(-not $UpdateFields.IsPresent))
    if ($UpdateFields) {
        $taskDoc.Fields.Update() | Out-Null
        foreach ($taskToc in $taskDoc.TablesOfContents) {
            $taskToc.Update()
            $taskToc.Range.Font.Size = 9
            $taskToc.Range.Font.Color = 0
            $taskToc.Range.ParagraphFormat.SpaceBefore = 0
            $taskToc.Range.ParagraphFormat.SpaceAfter = 0
            $taskToc.Range.ParagraphFormat.LineSpacingRule = 0
            $taskToc.Range.ParagraphFormat.KeepWithNext = 0
        }
        $taskDoc.Repaginate()
        foreach ($taskToc in $taskDoc.TablesOfContents) { $taskToc.UpdatePageNumbers() }
        $taskDoc.Save()
    }
    $taskDoc.Repaginate()
    $taskDoc.ExportAsFixedFormat($taskPdf,17)
    Write-Output "WORD_RENDER_PASS: pages=$($taskDoc.ComputeStatistics(2))"
} finally {
    if ($null -ne $taskDoc) {
        try { $taskDoc.Close(0) } catch { Write-Verbose 'Word document was already closed' }
        try { [Runtime.InteropServices.Marshal]::FinalReleaseComObject($taskDoc) | Out-Null } catch {}
    }
    if ($null -ne $taskWord) {
        try { $taskWord.Quit() } catch { Write-Verbose 'Word automation server was already closed' }
        try { [Runtime.InteropServices.Marshal]::FinalReleaseComObject($taskWord) | Out-Null } catch {}
    }
}
