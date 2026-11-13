$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$output = Join-Path $PSScriptRoot 'build\standalone'
$classes = Join-Path $output 'classes'
New-Item -ItemType Directory -Path $classes -Force | Out-Null
$javaBin = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin' } else { '' }
$javac = if ($javaBin) { Join-Path $javaBin 'javac.exe' } else { 'javac' }
$jar = if ($javaBin) { Join-Path $javaBin 'jar.exe' } else { 'jar' }
$sources = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'shared\src\main\java') -Recurse -Filter '*.java')
$sources += @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'src\main\java') -Recurse -Filter '*.java')
& $javac --release 11 -encoding UTF-8 -d $classes @($sources.FullName)
if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed.' }
$artifact = Join-Path $output 'sky-room-server.jar'
& $jar --create --file $artifact --main-class edu.hitsz.sky.server.ServerMain -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'JAR packaging failed.' }
Write-Output $artifact
