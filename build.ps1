param(
    [string]$ServerPath = 'Z:\Minecraft server'
)

$ErrorActionPreference = 'Stop'
$projectPath = Split-Path -Parent $MyInvocation.MyCommand.Path
$sourcePath = Join-Path $projectPath 'src\main\java'
$resourcePath = Join-Path $projectPath 'src\main\resources'
$buildPath = Join-Path $projectPath 'build'
$classesPath = Join-Path $buildPath 'classes'
$outputPath = Join-Path $buildPath 'NordPhantoms-1.0.0.jar'
$javaPath = 'C:\Program Files\Java\jdk-25\bin'

if (-not (Test-Path -LiteralPath $ServerPath)) { throw "Server path not found: $ServerPath" }
New-Item -ItemType Directory -Force -Path $classesPath | Out-Null
Get-ChildItem -LiteralPath $classesPath -Force -ErrorAction SilentlyContinue | Remove-Item -Recurse -Force

$paperApi = Get-ChildItem -LiteralPath (Join-Path $ServerPath 'libraries\io\papermc\paper\paper-api') -Recurse -Filter 'paper-api-26.2.build.127-stable.jar' | Select-Object -First 1
if (-not $paperApi) { throw 'Paper API 26.2 build 127 was not found in the server libraries.' }

$dependencies = @($paperApi.FullName)
$dependencies += Get-ChildItem -LiteralPath (Join-Path $ServerPath 'libraries') -Recurse -File -Filter '*.jar' |
    Where-Object { $_.FullName -match 'adventure|examination|annotations|bungeecord-chat|guava' } |
    Select-Object -ExpandProperty FullName
$classpath = ($dependencies | Sort-Object -Unique) -join ';'
$sources = Get-ChildItem -LiteralPath $sourcePath -Recurse -Filter '*.java' | Select-Object -ExpandProperty FullName

& (Join-Path $javaPath 'javac.exe') --release 25 -encoding UTF-8 -classpath $classpath -d $classesPath $sources
if ($LASTEXITCODE -ne 0) { throw 'NordPhantoms compilation failed.' }
Copy-Item -Path (Join-Path $resourcePath '*') -Destination $classesPath -Recurse -Force
if (Test-Path -LiteralPath $outputPath) { Remove-Item -LiteralPath $outputPath -Force }
Push-Location $classesPath
try {
    & (Join-Path $javaPath 'jar.exe') --create --file $outputPath .
    if ($LASTEXITCODE -ne 0) { throw 'NordPhantoms packaging failed.' }
} finally {
    Pop-Location
}
Write-Output $outputPath
