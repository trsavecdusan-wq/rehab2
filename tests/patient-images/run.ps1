$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$testOutput = Join-Path ([IO.Path]::GetTempPath()) ('NovaRehab-patient-tests-' + [guid]::NewGuid())
New-Item -ItemType Directory -Path $testOutput | Out-Null
& javac -encoding UTF-8 -d $testOutput (Join-Path $repoRoot 'app/src/main/java/com/rehab2/patient/PatientImageStore.java') (Join-Path $PSScriptRoot 'PatientImageStoreTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }
& java -cp $testOutput PatientImageStoreTest
if ($LASTEXITCODE -ne 0) { throw 'Patient image tests failed' }
