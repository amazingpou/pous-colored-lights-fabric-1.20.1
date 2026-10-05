param([string]$Candidate='blockonly',[switch]$SkipLifecycle,[switch]$IncludeVanilla)
$ErrorActionPreference='Stop'
if($Candidate -notmatch '^[a-z0-9-]+$'){throw 'Invalid candidate'}
$taskRoot=Split-Path -Parent $PSScriptRoot
$taskPlans=@(
 @('field',"run-optimization-validation-field-$Candidate-sodium"),
 @('lifecycle',"run-frame-validation-transition-lifecycle-$Candidate-sodium"),
 @('pulses',"run-frame-validation-pulses-green-isolated-pixels-$Candidate-sodium"),
 @('candles',"run-candle-validation-$Candidate-shaders"),
 @('pulses',"run-frame-validation-pulses-green-isolated-pixels-$Candidate-reimagined"),
 @('fireworks',"run-optimization-validation-fireworks-$Candidate-shaders")
)
if($IncludeVanilla){$taskPlans=,@('candles',"run-candle-validation-$Candidate-vanilla")+$taskPlans}
foreach($taskPlan in $taskPlans){
 if($SkipLifecycle -and $taskPlan[0] -eq 'lifecycle'){continue}
 $taskLog=Join-Path $taskRoot ("validation/client-"+$taskPlan[0]+"-"+$Candidate+"-"+($taskPlan[1] -split '-')[-1]+'.log')
 if(Test-Path -LiteralPath $taskLog){throw "Refusing to replace $taskLog"}
 & (Join-Path $PSScriptRoot 'run-client.ps1') -Mode $taskPlan[0] -RunDirectory $taskPlan[1] *> $taskLog
 if($LASTEXITCODE -ne 0){throw ("Client failed: "+$taskPlan[1]+"; inspect "+$taskLog)}
 Write-Output ("Completed "+$taskPlan[1])
}
