param([switch]$NoAndroid, [switch]$LongNetwork, [switch]$Offline)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { (Get-Command java).Source }
$keytool = Join-Path (Split-Path -Parent $java) 'keytool.exe'
Push-Location $projectRoot
$tlsDirectory = Join-Path ([IO.Path]::GetTempPath()) ('sky-tls-' + [Guid]::NewGuid().ToString('N'))
$previousPassword = $env:SKY_TEST_KEYSTORE_PASSWORD
try {
    $arguments = @('--no-daemon', ':shared:check')
    if (!$NoAndroid) { $arguments += @(':app:check', ':app:assembleDebug') }
    if ($LongNetwork) { $arguments += ':shared:verifyRoomTerminalTransitions' }
    if ($Offline) { $arguments += '--offline' }
    & .\gradlew.bat @arguments
    if ($LASTEXITCODE -ne 0) { throw 'Gradle verification failed' }

    New-Item -ItemType Directory -Path $tlsDirectory | Out-Null
    $env:SKY_TEST_KEYSTORE_PASSWORD = [Guid]::NewGuid().ToString('N')
    $key = Join-Path $tlsDirectory 'server.p12'
    $certificate = Join-Path $tlsDirectory 'localhost.cer'
    $trust = Join-Path $tlsDirectory 'trust.p12'
    & $keytool -genkeypair -alias localhost -keyalg RSA -keysize 2048 -validity 2 -dname 'CN=localhost' -ext 'SAN=dns:localhost' -storetype PKCS12 -keystore $key -storepass:env SKY_TEST_KEYSTORE_PASSWORD -keypass:env SKY_TEST_KEYSTORE_PASSWORD -noprompt
    if ($LASTEXITCODE -ne 0) { throw 'Test certificate generation failed' }
    & $keytool -exportcert -alias localhost -keystore $key -storepass:env SKY_TEST_KEYSTORE_PASSWORD -file $certificate
    if ($LASTEXITCODE -ne 0) { throw 'Test certificate export failed' }
    & $keytool -importcert -alias localhost -file $certificate -keystore $trust -storetype PKCS12 -storepass:env SKY_TEST_KEYSTORE_PASSWORD -noprompt
    if ($LASTEXITCODE -ne 0) { throw 'Test trust store creation failed' }
    $classpath = @('shared/build/classes/java/main', 'shared/build/classes/java/test') -join [IO.Path]::PathSeparator
    & $java -cp $classpath edu.hitsz.sky.net.TlsRoomServerTest $key $trust
    if ($LASTEXITCODE -ne 0) { throw 'TLS verification failed' }
} finally {
    $env:SKY_TEST_KEYSTORE_PASSWORD = $previousPassword
    foreach ($file in @('server.p12', 'localhost.cer', 'trust.p12')) {
        $path = Join-Path $tlsDirectory $file
        if (Test-Path -LiteralPath $path) { Remove-Item -LiteralPath $path -Force }
    }
    if (Test-Path -LiteralPath $tlsDirectory) { Remove-Item -LiteralPath $tlsDirectory -Force }
    Pop-Location
}
