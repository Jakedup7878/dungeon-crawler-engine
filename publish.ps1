<#
.SYNOPSIS
    Turns a project made with the editor into a game anyone can run: a folder with an .exe, its own copy of
    Java, and the project's files encrypted into game.pak. Players don't need to install anything.
    (The packaged editor does the same from Project > Publish Game.)
.EXAMPLE
    .\publish.ps1 "C:\Games\My Game"
.EXAMPLE
    .\publish.ps1 "C:\Games\My Game" "D:\Releases"
#>
param([Parameter(Mandatory = $true)][string]$Project, [string]$Output)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
. (Join-Path $root 'jdk.ps1')

$projectDir = (Resolve-Path $Project).Path
if (-not (Test-Path (Join-Path $projectDir 'project.txt'))) {
    Write-Error "$projectDir has no project.txt. Make a project with File > New Project in the editor."
}
if (-not $Output) { $Output = Join-Path $root 'dist' }

$jdk = Find-Jdk
if (-not $jdk) {
    Write-Error "No JDK 17 or newer found. Install one (for example: winget install EclipseAdoptium.Temurin.21.JDK) or point JAVA_HOME at it."
}
Write-Host "Using JDK at $jdk"

# 1. Compile the engine.
$classes = Join-Path $root 'out\publish\classes'
if (Test-Path $classes) { Remove-Item $classes -Recurse -Force }
New-Item -ItemType Directory -Force $classes | Out-Null
$sources = @(Get-ChildItem (Join-Path $root 'src\main\java') -Recurse -Filter *.java | ForEach-Object { $_.FullName })
& (Join-Path $jdk 'bin\javac.exe') --release 17 -encoding UTF-8 -d $classes @sources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Copy-Item (Join-Path $root 'src\main\resources\*') $classes -Recurse -Force

# 2. The publisher (the same code as the editor's Publish Game) builds the program and encrypts the project.
& (Join-Path $jdk 'bin\java.exe') -cp $classes crawler.publish.PublishTool $projectDir $Output
exit $LASTEXITCODE
