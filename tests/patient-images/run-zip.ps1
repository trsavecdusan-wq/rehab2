param([Parameter(Mandatory=$true)][string]$JsonJar)
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$out=Join-Path ([IO.Path]::GetTempPath()) ('NovaRehab-zip-tests-'+[guid]::NewGuid())
New-Item -ItemType Directory $out | Out-Null
& java -m jdk.compiler/com.sun.tools.javac.Main -encoding UTF-8 -cp $JsonJar -d $out (Join-Path $root 'app/src/main/java/com/rehab2/patient/PatientImageStore.java') (Join-Path $root 'app/src/main/java/com/rehab2/patient/PersonalIconZip.java') (Join-Path $PSScriptRoot 'PersonalIconZipTest.java')
if($LASTEXITCODE -ne 0){throw 'Compilation failed'}
& java -cp "$out;$JsonJar" PersonalIconZipTest
if($LASTEXITCODE -ne 0){throw 'ZIP tests failed'}
