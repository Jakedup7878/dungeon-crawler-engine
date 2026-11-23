<#
.SYNOPSIS
    Compiles the game and its JUnit tests (src/test/java) and runs them.
    The first run downloads JUnit's test runner from Maven Central into lib/.
.EXAMPLE
    .\test.ps1
.EXAMPLE
    .\test.ps1 -Filter Battle      # only test classes whose name contains "Battle"
#>
param([string]$Filter)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$root = $PSScriptRoot
. (Join-Path $root 'jdk.ps1')

# 1. JUnit's standalone runner, checked against Maven Central's checksum.
$junitVersion = '6.1.3'
$junitJar = Join-Path $root "lib\junit-platform-console-standalone-$junitVersion.jar"
if (-not (Test-Path $junitJar)) {
    $url = "https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/$junitVersion/junit-platform-console-standalone-$junitVersion.jar"
    Write-Host "Downloading JUnit $junitVersion from Maven Central..."
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    New-Item -ItemType Directory -Force (Split-Path $junitJar) | Out-Null
    $part = "$junitJar.part"
    Invoke-WebRequest -Uri $url -OutFile $part -UseBasicParsing
    $sha1File = "$part.sha1"
    Invoke-WebRequest -Uri "$url.sha1" -OutFile $sha1File -UseBasicParsing
    $expected = (Get-Content $sha1File -Raw).Trim().Split(' ')[0]
    Remove-Item $sha1File
    $actual = (Get-FileHash $part -Algorithm SHA1).Hash
    if ($actual -ne $expected) {
        Remove-Item $part
        Write-Error "The downloaded JUnit jar doesn't match Maven Central's checksum; not using it."
    }
    Move-Item $part $junitJar
}

$jdk = Find-Jdk
if (-not $jdk) {
    Write-Error "No JDK 17 or newer found. Install one (for example: winget install EclipseAdoptium.Temurin.21.JDK) or point JAVA_HOME at it."
}

# 2. Compile the game, then the tests against it.
$out = Join-Path $root 'out\test'
$mainOut = Join-Path $out 'main'
$testOut = Join-Path $out 'tests'
if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory -Force $mainOut, $testOut | Out-Null

# @(...) keeps these lists even when there is only one file, so each path stays one argument.
$mainSources = @(Get-ChildItem (Join-Path $root 'src\main\java') -Recurse -Filter *.java | ForEach-Object { $_.FullName })
& (Join-Path $jdk 'bin\javac.exe') --release 17 -encoding UTF-8 -Xlint:all,-serial -d $mainOut @mainSources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Copy-Item (Join-Path $root 'src\main\resources\*') $mainOut -Recurse -Force

$testSources = @(Get-ChildItem (Join-Path $root 'src\test\java') -Recurse -Filter *.java | ForEach-Object { $_.FullName })
& (Join-Path $jdk 'bin\javac.exe') --release 17 -encoding UTF-8 -cp "$mainOut;$junitJar" -d $testOut @testSources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

# 3. Run them. Headless, so no windows pop up.
$launcherArgs = @('-Djava.awt.headless=true', '-jar', $junitJar, 'execute', '--disable-banner',
    '--class-path', "$mainOut;$testOut", '--scan-class-path', $testOut)
if ($Filter) { $launcherArgs += @('--include-classname', ".*$Filter.*") }
& (Join-Path $jdk 'bin\java.exe') @launcherArgs
exit $LASTEXITCODE
