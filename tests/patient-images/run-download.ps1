param([Parameter(Mandatory=$true)][string]$JsonJar,[switch]$RealDownload)
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$out=Join-Path ([IO.Path]::GetTempPath()) ('NovaRehab-download-tests-'+[guid]::NewGuid())
New-Item -ItemType Directory $out | Out-Null
& java -m jdk.compiler/com.sun.tools.javac.Main -encoding UTF-8 -cp $JsonJar -d $out (Join-Path $root 'app/src/main/java/com/rehab2/patient/PersonalIconZip.java') (Join-Path $root 'app/src/main/java/com/rehab2/patient/VerifiedPackageDownload.java') (Join-Path $PSScriptRoot 'VerifiedPackageDownloadTest.java')
if($LASTEXITCODE -ne 0){throw 'Compilation failed'}
if($RealDownload){ & java -cp "$out;$JsonJar" VerifiedPackageDownloadTest real } else { & java -cp "$out;$JsonJar" VerifiedPackageDownloadTest }
if($LASTEXITCODE -ne 0){throw 'Download tests failed'}
