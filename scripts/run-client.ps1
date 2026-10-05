param([ValidateSet('candles','field','fireworks','lifecycle','pulses','showcase')][string]$Mode,[string]$RunDirectory,[switch]$DisableTransitionGate,[switch]$GlDiagnostics)
$ErrorActionPreference='Stop'
$taskRoot=Split-Path -Parent $PSScriptRoot
$taskClients=@(Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -match 'production-runtime[\\/]arguments\.txt|argFiles[\\/]runClient|KnotClient|net\.minecraft\.client\.main\.Main' })
if($taskClients.Count -gt 0){throw 'Another Minecraft client is running'}
$taskDrive=$null
try {
 foreach($taskLetter in @('P','Q','R','S','T','U','V','W','X','Y','Z')){
  if(-not (Get-PSDrive -Name $taskLetter -ErrorAction SilentlyContinue) -and -not (Test-Path ($taskLetter+':\'))){$taskDrive=$taskLetter+':';break}
 }
 if(-not $taskDrive){throw 'No free workspace alias'}
 & subst.exe $taskDrive $taskRoot
 if($LASTEXITCODE -ne 0){throw 'Workspace alias failed'}
 Push-Location ($taskDrive+'\')
 try {
  $taskFlags=@($Mode,$RunDirectory)
  if($DisableTransitionGate){$taskFlags+='gate-off'}
  if($GlDiagnostics){$taskFlags+='gl-debug'}
  & 'C:/Users/jp200/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe' -X utf8 tools/client_arguments.py @taskFlags
  if($LASTEXITCODE -ne 0){throw 'Argument preparation failed'}
  $taskRuntime=Get-Content '.\compatibility\java17\runtime.json' -Raw | ConvertFrom-Json
  & $taskRuntime.java ('@'+$taskDrive+'\compatibility\production-runtime\arguments.txt')
  $taskResult=$LASTEXITCODE
 } finally {Pop-Location}
} finally {if($taskDrive){& subst.exe $taskDrive /D}}
exit $taskResult
