$ErrorActionPreference = "Stop"

Set-Location $PSScriptRoot

$Stamp = Get-Date -Format "yyyy-MM-dd_HHmmss"
$BuildRoot = Join-Path $PSScriptRoot "Builds"
$Backup = Join-Path $BuildRoot "Build-$Stamp"

# Proprietary Libre libraries stay local and are injected only for the duration of the build.
$LibreSourceRoot = Join-Path $PSScriptRoot "_original-libs-11.3.0"
$LibreJniRoot = Join-Path $PSScriptRoot "Common\src\main\jniLibs"
$LibreAbis = @("arm64-v8a", "armeabi-v7a")
$LibreLibraries = @(
    "libcalibrat2.so",
    "libcalibrate.so",
    "liblibre_receiver_skb.so"
)

$InjectedLibreFiles = @()
$BackedUpLibreFiles = @()
$LibreBackupRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("JugglucoNG-libre-libs-" + $PID)

function Add-LibreLibraries {
    if (-not (Test-Path $LibreSourceRoot)) {
        throw "Brak lokalnego katalogu bibliotek Libre: $LibreSourceRoot"
    }

    foreach ($Abi in $LibreAbis) {
        $SourceDir = Join-Path $LibreSourceRoot $Abi
        if (-not (Test-Path $SourceDir)) {
            throw "Brak katalogu ABI bibliotek Libre: $SourceDir"
        }

        $TargetDir = Join-Path $LibreJniRoot $Abi
        New-Item -ItemType Directory -Path $TargetDir -Force | Out-Null

        foreach ($Library in $LibreLibraries) {
            $Source = Join-Path $SourceDir $Library

            # 11.3.0 arm64-v8a nie zawiera libcalibrate.so; kopiujemy ja tylko tam,
            # gdzie faktycznie istnieje. Pozostale biblioteki Libre musza byc obecne.
            if (-not (Test-Path $Source)) {
                if ($Library -eq "libcalibrate.so") {
                    Write-Host "Libre [$Abi]: pomijam brakujace $Library"
                    continue
                }
                throw "Brak wymaganej biblioteki Libre: $Source"
            }

            $Target = Join-Path $TargetDir $Library

            # Jesli kiedys pojawi sie tracked/localna wersja pliku, zachowaj ja
            # i przywroc po buildzie zamiast nadpisywac ja na stale.
            if (Test-Path $Target) {
                $BackupDir = Join-Path $LibreBackupRoot $Abi
                New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null
                $BackupFile = Join-Path $BackupDir $Library
                Copy-Item $Target $BackupFile -Force
                $BackedUpLibreFiles += [PSCustomObject]@{
                    Target = $Target
                    Backup = $BackupFile
                }
            }

            Copy-Item $Source $Target -Force
            $InjectedLibreFiles += $Target
            Write-Host "Libre [$Abi]: $Library"
        }
    }
}

function Remove-LibreLibraries {
    foreach ($Target in $InjectedLibreFiles) {
        Remove-Item $Target -Force -ErrorAction SilentlyContinue
    }

    foreach ($Entry in $BackedUpLibreFiles) {
        New-Item -ItemType Directory -Path (Split-Path $Entry.Target -Parent) -Force | Out-Null
        Copy-Item $Entry.Backup $Entry.Target -Force
    }

    foreach ($Abi in $LibreAbis) {
        $Dir = Join-Path $LibreJniRoot $Abi
        if ((Test-Path $Dir) -and -not (Get-ChildItem $Dir -Force -ErrorAction SilentlyContinue)) {
            Remove-Item $Dir -Force -ErrorAction SilentlyContinue
        }
    }

    if ((Test-Path $LibreJniRoot) -and -not (Get-ChildItem $LibreJniRoot -Force -ErrorAction SilentlyContinue)) {
        Remove-Item $LibreJniRoot -Force -ErrorAction SilentlyContinue
    }

    Remove-Item $LibreBackupRoot -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host ""
Write-Host "=== JugglucoNG build ==="
Write-Host "Katalog projektu: $PSScriptRoot"
Write-Host ""

try {
    Write-Host "=== Libre libraries ==="
    Add-LibreLibraries
    Write-Host ""

    # Buduj telefon + Wear. Clean usuwa tylko katalogi Gradle, nie katalog Builds.
    & .\gradlew.bat clean `
        :Common:assembleMobileRelease `
        :Common:assembleWearRelease `
        --no-daemon `
        --no-configuration-cache

    if ($LASTEXITCODE -ne 0) {
        throw "Build zakonczyl sie bledem. APK nie zostaly zarchiwizowane."
    }

    $Apks = @(
        Get-ChildItem ".\Common\build\outputs\apk" -Recurse -Filter "*.apk" -File -ErrorAction Stop
    )

    if ($Apks.Count -eq 0) {
        throw "Build zakonczyl sie, ale nie znaleziono zadnych plikow APK."
    }

    New-Item -ItemType Directory -Path $Backup -Force | Out-Null

    foreach ($Apk in $Apks) {
        $Destination = Join-Path $Backup $Apk.Name

        # Gdyby dwa warianty mialy identyczna nazwe, nie nadpisuj pierwszego.
        if (Test-Path $Destination) {
            $ParentName = Split-Path $Apk.DirectoryName -Leaf
            $BaseName = [System.IO.Path]::GetFileNameWithoutExtension($Apk.Name)
            $Destination = Join-Path $Backup ("{0}-{1}.apk" -f $BaseName, $ParentName)
        }

        Copy-Item $Apk.FullName $Destination -Force
    }

    # Zapisz sumy kontrolne, zeby pozniej latwo rozpoznac dokladnie ten build.
    $HashFile = Join-Path $Backup "SHA256.txt"
    Get-ChildItem $Backup -Filter "*.apk" -File |
        Get-FileHash -Algorithm SHA256 |
        ForEach-Object {
            "{0}  {1}" -f $_.Hash, (Split-Path $_.Path -Leaf)
        } |
        Set-Content -Path $HashFile -Encoding ASCII

    # Zapisz informacje o wersji z Git, jesli repo jest dostepne.
    $InfoFile = Join-Path $Backup "BUILD-INFO.txt"
    $Info = @()
    $Info += "Build time: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"

    try {
        $Commit = (git rev-parse HEAD 2>$null).Trim()
        if ($Commit) {
            $Info += "Git commit: $Commit"
        }

        $Branch = (git branch --show-current 2>$null).Trim()
        if ($Branch) {
            $Info += "Git branch: $Branch"
        }

        $Dirty = git status --porcelain 2>$null
        if ($Dirty) {
            $Info += "Working tree: modified"
        } else {
            $Info += "Working tree: clean"
        }
    }
    catch {
        $Info += "Git info: unavailable"
    }

    $Info | Set-Content -Path $InfoFile -Encoding UTF8

    Write-Host ""
    Write-Host "=== BUILD OK ==="
    Write-Host "APK zapisane w:"
    Write-Host $Backup
    Write-Host ""

    Get-ChildItem $Backup -Filter "*.apk" -File |
        Select-Object Name, Length, LastWriteTime

    Write-Host ""
    Write-Host "SHA256: $HashFile"
    Write-Host "Info:   $InfoFile"
}
finally {
    Remove-LibreLibraries
}
