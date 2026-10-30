param([Parameter(Mandatory=$true)][string]$JsonJar)
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$classes=Join-Path $repo 'app/build/tmp/kotlin-classes/debug'
if(!(Test-Path $classes)){throw 'Run assembleDebug first'}
$stdlib=Get-ChildItem (Join-Path $env:USERPROFILE '.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/1.9.24') -Recurse -Filter '*.jar' | Select-Object -First 1 -ExpandProperty FullName
if(!$stdlib){throw 'Kotlin 1.9.24 standard library missing'}
$out=Join-Path ([IO.Path]::GetTempPath()) ('NovaRehab-field-tests-'+[guid]::NewGuid())
New-Item -ItemType Directory $out | Out-Null
$javaClasses=Join-Path $repo 'app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes'
$cp="$classes;$javaClasses;$JsonJar;$stdlib"
& javac -encoding UTF-8 -cp $cp -d $out (Join-Path $PSScriptRoot 'UpgradeFieldPolicyTest.java') (Join-Path $PSScriptRoot 'ReviewedRuntimeTest.java')
if($LASTEXITCODE -ne 0){throw 'Test compilation failed'}
& java -cp "$out;$cp" UpgradeFieldPolicyTest
if($LASTEXITCODE -ne 0){throw 'Preservation tests failed'}
& java -cp "$out;$cp" ReviewedRuntimeTest (Join-Path $repo 'NovaRehab/data/aac_full_icon_review_manifest.json')
if($LASTEXITCODE -ne 0){throw 'Runtime mappings failed'}
