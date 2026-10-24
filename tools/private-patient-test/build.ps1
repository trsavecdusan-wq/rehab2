param(
    [Parameter(Mandatory=$true)][string]$PackageRoot,
    [Parameter(Mandatory=$true)][string]$BaselineApk,
    [switch]$Unsigned
)
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$packagePath = (Resolve-Path -LiteralPath $PackageRoot).Path
$baselinePath = (Resolve-Path -LiteralPath $BaselineApk).Path
$sdkPath = $env:ANDROID_HOME
if (!$sdkPath) { throw 'Set ANDROID_HOME to the existing Android SDK.' }
$buildTools = Join-Path $sdkPath 'build-tools/34.0.0'
$aapt = Join-Path $buildTools 'aapt.exe'
$signer = Join-Path $buildTools 'apksigner.bat'
$baselineBadging = & $aapt dump badging $baselinePath
$baseInfo = $baselineBadging | Select-Object -First 1
if ($LASTEXITCODE -ne 0 -or $baseInfo -notmatch "name='com.rehab2' versionCode='10982' versionName='1.2.794'") {
    throw 'Expected the verified stable com.rehab2 1.2.794 / 10982 baseline.'
}
if (!$Unsigned) {
    foreach ($key in @('ANDROID_KEYSTORE_PATH','ANDROID_KEYSTORE_PASSWORD','ANDROID_KEY_ALIAS','ANDROID_KEY_PASSWORD')) {
        if (![Environment]::GetEnvironmentVariable($key)) { throw 'Original signing configuration missing. Do not substitute another key.' }
    }
}
$output = Join-Path $packagePath 'android-build'
$arguments = @('-I', (Join-Path $PSScriptRoot 'private-build.init.gradle'),
    ('-PprivatePatientAssets=' + (Join-Path $packagePath 'assets')),
    ('-PprivatePatientOutput=' + $output), ':app:assemblePrivatePatientTest',
    '--offline', '--no-build-cache', '--no-configuration-cache', '--console=plain',
    '--no-daemon', '--max-workers=1', '-Dorg.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8',
    '-Pkotlin.compiler.execution.strategy=in-process')
if ($Unsigned) { $arguments += '-PprivatePatientUnsigned=true' }
Push-Location $repoRoot
try {
    & ./gradlew.bat @arguments
    if ($LASTEXITCODE -ne 0) { throw 'Private build failed.' }
} finally { Pop-Location }
$apkName = if ($Unsigned) { 'NovaRehab-1.2.796-private-test-UNSIGNED.apk' } else { 'NovaRehab-1.2.796-private-test.apk' }
$apk = Join-Path $output ('outputs/apk/privatePatientTest/' + $apkName)
$privateBadging = & $aapt dump badging $apk
$info = $privateBadging | Select-Object -First 1
if ($LASTEXITCODE -ne 0 -or $info -notmatch "name='com.rehab2' versionCode='10984' versionName='1.2.796-private-test'") {
    throw 'Private APK identity/version mismatch.'
}
if (!$Unsigned) {
    $baselineCertificates = & $signer verify --print-certs $baselinePath
    if ($LASTEXITCODE -ne 0) { throw 'Baseline signature invalid.' }
    $testCertificates = & $signer verify --print-certs $apk
    if ($LASTEXITCODE -ne 0) { throw 'Private APK signature invalid.' }
    $oldDigest = @($baselineCertificates | Select-String 'certificate SHA-256 digest:' | ForEach-Object { $_.Line.Trim() })
    $newDigest = @($testCertificates | Select-String 'certificate SHA-256 digest:' | ForEach-Object { $_.Line.Trim() })
    if (!$oldDigest.Count -or (Compare-Object $oldDigest $newDigest)) { throw 'SIGNATURE MISMATCH. This is NOT a compatible upgrade.' }
    Write-Output 'Signature matches the verified 794 APK. Installed tablet version still needs confirmation.'
} else {
    Write-Output 'UNSIGNED: not installable, not a verified upgrade. Original release key is still required.'
}
Write-Output $info
Write-Output $apk
