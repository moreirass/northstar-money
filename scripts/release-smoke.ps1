param(
    [string]$DeviceSerial,
    [string]$Adb
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
if (-not $Adb) {
    $localAdb = Join-Path $projectRoot '.android-home/platform-tools/adb.exe'
    if (Test-Path -LiteralPath $localAdb) { $Adb = $localAdb }
    elseif ($env:ANDROID_HOME) { $Adb = Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe' }
    else { $Adb = (Get-Command adb -ErrorAction Stop).Source }
}

$devices = @(& $Adb devices | Where-Object { $_ -match '^([^\s]+)\s+device$' } |
    ForEach-Object { ($_ -split '\s+')[0] })
if ($LASTEXITCODE -ne 0) { throw 'Não foi possível consultar o ADB.' }
if ($DeviceSerial) {
    if ($DeviceSerial -notin $devices) { throw 'O dispositivo indicado não está disponível ou autorizado.' }
} elseif ($devices.Count -eq 1) {
    $DeviceSerial = $devices[0]
} else {
    throw 'Ligar um dispositivo de teste ou indicar -DeviceSerial para escolher um dos dispositivos disponíveis.'
}

$previousSerial = $env:ANDROID_SERIAL
try {
    $env:ANDROID_SERIAL = $DeviceSerial
    Push-Location $projectRoot
    try {
        $classes = @(
            'com.northstar.money.release.ReleaseSmokeTest',
            'com.northstar.money.data.backup.FullBackupRepositoryTest',
            'com.northstar.money.data.repository.TransactionEditingTest',
            'com.northstar.money.data.repository.CsvImportAtomicityTest',
            'com.northstar.money.core.database.AsyncCurrencyMigrationTest'
        ) -join ','
        & ./gradlew.bat connectedReleaseAndroidTest '-PreleaseSmoke=true' "-Pandroid.testInstrumentationRunnerArguments.class=$classes" --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' '-Dorg.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8' --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'O teste de fumo release falhou; consultar app/build/reports/androidTests.' }
    } finally { Pop-Location }
} finally {
    $env:ANDROID_SERIAL = $previousSerial
}
