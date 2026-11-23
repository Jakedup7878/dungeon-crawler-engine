<#
.SYNOPSIS
    Turns a project made with the editor into a game anyone can run: a folder with an .exe,
    the game's files and its own copy of Java, so players don't need to install anything.
.EXAMPLE
    .\publish.ps1 "C:\Games\My Game"
#>
param([Parameter(Mandatory = $true)][string]$Project)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
. (Join-Path $root 'jdk.ps1')

$projectDir = (Resolve-Path $Project).Path
$projectFile = Join-Path $projectDir 'project.txt'
if (-not (Test-Path $projectFile)) {
    Write-Error "$projectDir has no project.txt. Make a project with File > New Project in the editor."
}
$nameLine = Select-String -Path $projectFile -Pattern '^\s*name\s*=\s*(.+)$' | Select-Object -First 1
$gameName = if ($nameLine) { $nameLine.Matches[0].Groups[1].Value.Trim() } else { Split-Path $projectDir -Leaf }
# The program's file name: letters, digits, spaces, dashes and underscores only.
$appName = ($gameName -replace '[^A-Za-z0-9 _-]', '').Trim()
if (-not $appName) { $appName = 'Game' }

$jdk = Find-Jdk
if (-not $jdk) {
    Write-Error "No JDK 17 or newer found. Install one (for example: winget install EclipseAdoptium.Temurin.21.JDK) or point JAVA_HOME at it."
}
Write-Host "Using JDK at $jdk"

$work = Join-Path $root 'out\publish'
$classes = Join-Path $work 'classes'
$stage = Join-Path $work 'stage'
$dist = Join-Path $root 'dist'
foreach ($dir in @($classes, $stage)) {
    if (Test-Path $dir) { Remove-Item $dir -Recurse -Force }
    New-Item -ItemType Directory -Force $dir | Out-Null
}

# 1. Compile the game and pack it into one jar.
$sources = Get-ChildItem (Join-Path $root 'src\main\java') -Recurse -Filter *.java | ForEach-Object { $_.FullName }
& (Join-Path $jdk 'bin\javac.exe') --release 17 -encoding UTF-8 -d $classes @sources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Copy-Item (Join-Path $root 'src\main\resources\*') $classes -Recurse -Force
& (Join-Path $jdk 'bin\jar.exe') --create --file (Join-Path $stage 'crawler.jar') --main-class crawler.Main -C $classes .
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

# 2. The project goes next to the jar in a folder called "game"; Main plays it when started.
Copy-Item $projectDir (Join-Path $stage 'game') -Recurse

# 3. Wrap it up with a Java runtime and an .exe.
New-Item -ItemType Directory -Force $dist | Out-Null
$appDir = Join-Path $dist $appName
if (Test-Path $appDir) { Remove-Item $appDir -Recurse -Force }
& (Join-Path $jdk 'bin\jpackage.exe') --type app-image --name $appName --input $stage `
    --main-jar crawler.jar --main-class crawler.Main --add-modules java.desktop --dest $dist
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host ""
Write-Host "Published '$gameName' to $appDir"
Write-Host "Players run $appName.exe. Zip the whole '$appName' folder to share it."
