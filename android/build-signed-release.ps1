param(
    [string]$OutputPath = (Join-Path $env:USERPROFILE 'Downloads\Saldo-1.14.apk')
)

$ErrorActionPreference = 'Stop'
$signingDir = Join-Path $env:LOCALAPPDATA 'Saldo\release-signing'
$keystore = Join-Path $signingDir 'saldo.jks'
$passwordFile = Join-Path $signingDir 'password.dpapi'
$buildTools = Join-Path $env:LOCALAPPDATA 'Android\Sdk\build-tools\34.0.0'
$releaseDir = Join-Path $PSScriptRoot 'app\build\outputs\apk\release'

if (!(Test-Path -LiteralPath $keystore) -or !(Test-Path -LiteralPath $passwordFile)) {
    throw 'The existing Saldo signing key is missing. Do not generate a new key for an app update.'
}

& (Join-Path $PSScriptRoot 'gradlew.bat') :app:assembleRelease -p $PSScriptRoot
if ($LASTEXITCODE -ne 0) { throw 'Android release build failed.' }

$secure = ConvertTo-SecureString (Get-Content -LiteralPath $passwordFile -Raw).Trim()
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try {
    $env:SALDO_STORE_PASS = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
}

try {
    $aligned = Join-Path $releaseDir 'app-release-aligned.apk'
    & (Join-Path $buildTools 'zipalign.exe') -f -p 4 (Join-Path $releaseDir 'app-release-unsigned.apk') $aligned
    if ($LASTEXITCODE -ne 0) { throw 'APK alignment failed.' }

    & (Join-Path $buildTools 'apksigner.bat') sign --ks $keystore --ks-key-alias saldo `
        --ks-pass env:SALDO_STORE_PASS --key-pass env:SALDO_STORE_PASS --out $OutputPath $aligned
    if ($LASTEXITCODE -ne 0) { throw 'APK signing failed.' }

    & (Join-Path $buildTools 'apksigner.bat') verify --verbose $OutputPath
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
} finally {
    Remove-Item Env:SALDO_STORE_PASS -ErrorAction SilentlyContinue
}

Get-Item -LiteralPath $OutputPath | Select-Object FullName, Length, LastWriteTime
