# Avoid Java/Gradle argument-file encoding issues in a Unicode Windows path.
param([Parameter(ValueFromRemainingArguments=$true)][string[]]$GradleArguments=@('check'),[string]$ProjectDirectory='.')
$ErrorActionPreference='Stop'
$taskRoot=Split-Path -Parent $PSScriptRoot
$taskGradleHomeBefore=$env:GRADLE_USER_HOME
$taskDrive=$null
try {
    if (-not $env:GRADLE_USER_HOME) {
        $env:GRADLE_USER_HOME=Join-Path $env:USERPROFILE '.gradle'
    }
    if ($taskRoot -match '[^\x00-\x7F]') {
        foreach ($taskLetter in @('P','Q','R','S','T','U','V','W','X','Y','Z')) {
            if (-not (Get-PSDrive -Name $taskLetter -ErrorAction SilentlyContinue) -and -not (Test-Path ($taskLetter+':\'))) {
                $taskDrive=$taskLetter+':'
                break
            }
        }
        if (-not $taskDrive) { throw 'No free drive letter for temporary workspace alias' }
        & subst.exe $taskDrive $taskRoot
        if ($LASTEXITCODE -ne 0) { throw 'Could not create temporary workspace alias' }
        $taskRunRoot=$taskDrive+'\'
    } else { $taskRunRoot=$taskRoot }
    if ([System.IO.Path]::IsPathRooted($ProjectDirectory) -or ($ProjectDirectory -split '[/\\]') -contains '..') { throw 'Project directory must stay within the workspace' }
    $taskProjectRoot=Join-Path $taskRunRoot $ProjectDirectory
    & (Join-Path $taskRunRoot 'gradlew.bat') -p $taskProjectRoot @GradleArguments
    $taskResult=$LASTEXITCODE
} finally {
    if ($taskDrive) { & subst.exe $taskDrive /D }
    $env:GRADLE_USER_HOME=$taskGradleHomeBefore
}
exit $taskResult
