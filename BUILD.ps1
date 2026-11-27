#Requires -Version 5.1

[CmdletBinding()]
param(
    [ValidateSet('Release', 'Debug')]
    [string]$Configuration = 'Release',

    [switch]$Clean
)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $false

function Find-BuildPython {
    $ErrorActionPreference = 'Continue'
    foreach ($candidate in 'py', 'python', 'python3') {
        $command = Get-Command $candidate -CommandType Application -ErrorAction SilentlyContinue |
            Select-Object -First 1
        if (-not $command) { continue }

        $prefix = @()
        if ($candidate -eq 'py') { $prefix = @('-3') }
        & $command.Source @prefix -c 'import sys; sys.exit(sys.version_info < (3, 9))' 2>$null
        if ($LASTEXITCODE -eq 0) {
            return @{ Executable = $command.Source; Prefix = $prefix }
        }
    }
    throw 'Install Python 3.9 or newer (py -3, python, or python3).'
}

function Restore-BuildLibraries {
    $ErrorActionPreference = 'Continue'
    $python = Find-BuildPython
    $prefix = $python.Prefix
    $distScript = Join-Path $PSScriptRoot 'scripts/dist/dist.py'

    Write-Host 'Checking JNI libraries against build-inputs.json...'
    & $python.Executable @prefix $distScript check-inputs
    if ($LASTEXITCODE -eq 0) { return }

    Write-Host 'Restoring JNI libraries from the APK pinned in build-inputs.json...'
    & $python.Executable @prefix $distScript restore
    if ($LASTEXITCODE -ne 0) { throw 'JNI library restoration failed. See the output above.' }

    & $python.Executable @prefix $distScript check-inputs
    if ($LASTEXITCODE -ne 0) { throw 'JNI library verification failed after restoration.' }
}

function Get-BuiltApk {
    param([string]$Flavor, [string]$BuildType)

    $outputDir = Join-Path $PSScriptRoot "Common/build/outputs/apk/$Flavor/$BuildType"
    $metadataPath = Join-Path $outputDir 'output-metadata.json'
    if (-not (Test-Path -LiteralPath $metadataPath -PathType Leaf)) {
        throw "Gradle APK metadata is missing: $metadataPath"
    }

    $metadata = Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json
    $outputs = @($metadata.elements)
    if ($outputs.Count -ne 1) {
        throw "Expected one APK for $Flavor/$BuildType, found $($outputs.Count)."
    }

    $name = $outputs[0].outputFile
    if (-not $name -or $name -notmatch '\.apk$' -or $name -match '[/\\:]') {
        throw "Invalid APK filename in $metadataPath"
    }

    return Get-Item -LiteralPath (Join-Path $outputDir $name)
}

function Get-BuildGitInfo {
    $ErrorActionPreference = 'Continue'
    if (-not (Get-Command git -CommandType Application -ErrorAction SilentlyContinue)) {
        return 'Git info: unavailable'
    }

    $commit = git rev-parse HEAD 2>$null
    if ($LASTEXITCODE -ne 0) { return 'Git info: unavailable' }
    "Git commit: $commit"

    $branch = git branch --show-current 2>$null
    if ($LASTEXITCODE -eq 0 -and $branch) { "Git branch: $branch" }

    $changes = git status --porcelain 2>$null
    if ($LASTEXITCODE -eq 0) {
        $state = if ($changes) { 'modified' } else { 'clean' }
        "Working tree: $state"
    }
}

$gradle = Join-Path $PSScriptRoot 'gradlew.bat'
if (-not (Test-Path -LiteralPath $gradle -PathType Leaf)) {
    throw 'Place BUILD.ps1 in the repository root, next to gradlew.bat.'
}
if (-not (Test-Path -LiteralPath (Join-Path $PSScriptRoot 'Common/src/main/cpp/libjuice/CMakeLists.txt') -PathType Leaf)) {
    throw 'Initialize libjuice with: git submodule update --init --recursive'
}

$previousAndroidHome = $env:ANDROID_HOME
Push-Location -LiteralPath $PSScriptRoot
try {
    if (-not $env:ANDROID_HOME -and -not $env:ANDROID_SDK_ROOT -and -not (Test-Path -LiteralPath 'local.properties' -PathType Leaf)) {
        if ($env:LOCALAPPDATA) {
            $defaultSdk = Join-Path $env:LOCALAPPDATA 'Android/Sdk'
            if (Test-Path -LiteralPath $defaultSdk -PathType Container) { $env:ANDROID_HOME = $defaultSdk }
        }
        if (-not $env:ANDROID_HOME) {
            throw 'Set ANDROID_HOME or sdk.dir in local.properties. See the Windows section in README.md.'
        }
    }

    $buildType = $Configuration.ToLowerInvariant()
    $variant = if ($buildType -eq 'release') { 'Release' } else { 'Debug' }
    $stamp = Get-Date -Format 'yyyy-MM-dd_HHmmss_fff'
    $gitInfo = @(Get-BuildGitInfo)
    Write-Host "=== JugglucoNG $variant build (phone and Wear OS) ==="
    Restore-BuildLibraries

    $gradleOptions = @('--no-daemon', '--no-configuration-cache', '--max-workers=1')
    if ($Clean) {
        & $gradle 'clean' @gradleOptions
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle clean failed with exit code $LASTEXITCODE."
        }
    }

    # Match the release workflow: use a separate Gradle process for each flavor.
    foreach ($flavor in 'Mobile', 'Wear') {
        $task = ":Common:assemble${flavor}${variant}"
        Write-Host "Building $task..."
        & $gradle $task @gradleOptions
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle task $task failed with exit code $LASTEXITCODE."
        }
    }

    $phoneApk = Get-BuiltApk -Flavor 'mobile' -BuildType $buildType
    $wearApk = Get-BuiltApk -Flavor 'wear' -BuildType $buildType

    $archiveRoot = Join-Path $PSScriptRoot 'Builds'
    New-Item -ItemType Directory -Path $archiveRoot -Force | Out-Null
    $archivePath = Join-Path $archiveRoot "Build-$stamp"
    $stagingPath = Join-Path $archiveRoot ('.staging-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $stagingPath | Out-Null

    try {
        $phoneName = $phoneApk.BaseName
        if ($phoneName -notmatch '-phone(?:-|$)') { $phoneName += '-phone' }
        $phoneArchiveName = "$phoneName-$stamp.apk"
        $wearArchiveName = "$($wearApk.BaseName)-$stamp.apk"
        Copy-Item -LiteralPath $phoneApk.FullName -Destination (Join-Path $stagingPath $phoneArchiveName)
        Copy-Item -LiteralPath $wearApk.FullName -Destination (Join-Path $stagingPath $wearArchiveName)

        $hashLines = foreach ($name in $phoneArchiveName, $wearArchiveName) {
            $hash = Get-FileHash -LiteralPath (Join-Path $stagingPath $name) -Algorithm SHA256
            "$($hash.Hash.ToLowerInvariant())  $name"
        }
        $hashLines | Set-Content -LiteralPath (Join-Path $stagingPath 'SHA256.txt') -Encoding ASCII

        $info = @("Build time: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz')", "Build type: $variant") + $gitInfo
        $info | Set-Content -LiteralPath (Join-Path $stagingPath 'BUILD-INFO.txt') -Encoding UTF8

        # Publish the archive only once both APKs and their metadata are present.
        [System.IO.Directory]::Move($stagingPath, $archivePath)
    }
    finally {
        if (Test-Path -LiteralPath $stagingPath) {
            Remove-Item -LiteralPath $stagingPath -Recurse -Force
        }
    }

    Write-Host "APK, SHA256 and build info: $archivePath"
}
finally {
    Pop-Location
    $env:ANDROID_HOME = $previousAndroidHome
}
