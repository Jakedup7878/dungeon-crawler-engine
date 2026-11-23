<#
.SYNOPSIS
    Builds the map editor as a program anyone can run, without installing Java:
    dist\Dungeon Crawler Editor\Dungeon Crawler Editor.exe

    The folder also holds "Sample Quest.exe" (plays the sample game, and is what Project > Publish Game
    copies to make new games) and the "Sample Quest" project to open in the editor. Copy or zip the whole
    folder to use it on another computer.
.EXAMPLE
    .\build-editor.ps1
#>
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
. (Join-Path $root 'jdk.ps1')

$jdk = Find-Jdk
if (-not $jdk) {
    Write-Error "No JDK 17 or newer found. Install one (for example: winget install EclipseAdoptium.Temurin.21.JDK) or point JAVA_HOME at it."
}
Write-Host "Using JDK at $jdk"

$appName = 'Dungeon Crawler Editor'
# Must match Publisher.TEMPLATE_NAME in src\main\java\crawler\publish\Publisher.java.
$playerName = 'Sample Quest'
$work = Join-Path $root 'out\editor-build'
$classes = Join-Path $work 'classes'
$stage = Join-Path $work 'stage'
foreach ($dir in @($classes, $stage)) {
    if (Test-Path $dir) { Remove-Item $dir -Recurse -Force }
    New-Item -ItemType Directory -Force $dir | Out-Null
}

# 1. Compile everything and pack it into one jar.
$sources = @(Get-ChildItem (Join-Path $root 'src\main\java') -Recurse -Filter *.java | ForEach-Object { $_.FullName })
& (Join-Path $jdk 'bin\javac.exe') --release 17 -encoding UTF-8 -d $classes @sources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Copy-Item (Join-Path $root 'src\main\resources\*') $classes -Recurse -Force
& (Join-Path $jdk 'bin\jar.exe') --create --file (Join-Path $stage 'crawler.jar') --main-class crawler.editor.MapEditor -C $classes .
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

# 2. The editor program, plus a second program that plays games (the template for publishing).
$player = Join-Path $work 'player.properties'
Set-Content -Path $player -Value "main-class=crawler.Main`r`ndescription=Plays the sample game" -Encoding ASCII
$dist = Join-Path $root 'dist'
$appDir = Join-Path $dist $appName
New-Item -ItemType Directory -Force $dist | Out-Null
if (Test-Path $appDir) { Remove-Item $appDir -Recurse -Force }
& (Join-Path $jdk 'bin\jpackage.exe') --type app-image --name $appName --input $stage `
    --main-jar crawler.jar --main-class crawler.editor.MapEditor `
    --add-launcher "$playerName=$player" --add-modules java.desktop --dest $dist
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

# 3. The sample project, to open in the editor and learn from.
Copy-Item (Join-Path $root 'src\main\resources\sample') (Join-Path $appDir $playerName) -Recurse

Write-Host ""
Write-Host "Built $appDir"
Write-Host "Run '$appName.exe'. Copy or zip the whole folder to use it on another computer."
