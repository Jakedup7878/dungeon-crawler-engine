<#
.SYNOPSIS
    Compiles and runs the dungeon crawler, or with -Editor the map editor. No build tool needed, just a JDK 17+.
.EXAMPLE
    .\run.ps1
.EXAMPLE
    .\run.ps1 maps\my-floor.txt
.EXAMPLE
    .\run.ps1 -Editor maps\my-floor.txt
#>
param([string]$Map, [switch]$Editor)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot

. (Join-Path $root 'jdk.ps1')

$jdk = Find-Jdk
if (-not $jdk) {
    Write-Error "No JDK 17 or newer found. Install one (for example: winget install EclipseAdoptium.Temurin.21.JDK) or point JAVA_HOME at it."
}
Write-Host "Using JDK at $jdk"

# The game and the editor build into separate folders, so rebuilding one never pulls
# class files out from under the other while it is running.
if ($Editor) {
    $out = Join-Path $root 'out\editor'
    $mainClass = 'crawler.editor.MapEditor'
} else {
    $out = Join-Path $root 'out\classes'
    $mainClass = 'crawler.Main'
}
if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory -Force $out | Out-Null

$sources = Get-ChildItem (Join-Path $root 'src\main\java') -Recurse -Filter *.java | ForEach-Object { $_.FullName }
& (Join-Path $jdk 'bin\javac.exe') --release 17 -encoding UTF-8 -Xlint:all,-serial -d $out @sources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Copy-Item (Join-Path $root 'src\main\resources\*') $out -Recurse -Force

$javaArgs = @('-cp', $out, $mainClass)
if ($Map) {
    # The editor may be given a file that doesn't exist yet: it creates a new map that saves there.
    $javaArgs += $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($Map)
}
& (Join-Path $jdk 'bin\java.exe') @javaArgs
exit $LASTEXITCODE
