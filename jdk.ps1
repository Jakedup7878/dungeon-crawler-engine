# Finds a JDK 17 or newer. Shared by run.ps1 and publish.ps1 (dot-source it: . .\jdk.ps1).

function Get-JdkVersion([string]$jdkHome) {
    $release = Join-Path $jdkHome 'release'
    if (-not (Test-Path $release)) { return 0 }
    $match = Select-String -Path $release -Pattern '^JAVA_VERSION="([^"]+)"' | Select-Object -First 1
    if (-not $match) { return 0 }
    $version = $match.Matches[0].Groups[1].Value
    if ($version.StartsWith('1.')) { return [int]$version.Split('.')[1] }
    return [int]($version -split '[.+-]')[0]
}

function Find-Jdk {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\javac.exe')) -and (Get-JdkVersion $env:JAVA_HOME) -ge 17) {
        return $env:JAVA_HOME
    }
    $roots = @(
        "$env:USERPROFILE\.jdks",
        "$env:ProgramFiles\Java",
        "$env:ProgramFiles\Eclipse Adoptium",
        "$env:ProgramFiles\Microsoft",
        "$env:ProgramFiles\Amazon Corretto",
        "$env:ProgramFiles\Zulu",
        "$env:ProgramFiles\BellSoft"
    )
    $best = $roots |
        Where-Object { Test-Path $_ } |
        ForEach-Object { Get-ChildItem $_ -Directory } |
        Where-Object { Test-Path (Join-Path $_.FullName 'bin\javac.exe') } |
        ForEach-Object { [pscustomobject]@{ Path = $_.FullName; Version = (Get-JdkVersion $_.FullName) } } |
        Where-Object { $_.Version -ge 17 } |
        Sort-Object Version -Descending |
        Select-Object -First 1
    if ($best) { return $best.Path }
    return $null
}
