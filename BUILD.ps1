$ErrorActionPreference = "Stop"

Set-Location $PSScriptRoot

$Stamp = Get-Date -Format "yyyy-MM-dd_HHmmss"
$BuildRoot = Join-Path $PSScriptRoot "Builds"
$Backup = Join-Path $BuildRoot "Build-$Stamp"

Write-Host ""
Write-Host "=== JugglucoNG build ==="
Write-Host "Katalog projektu: $PSScriptRoot"
Write-Host ""

# Przygotuj oryginalne biblioteki Juggluco 11.3.0.
# Folder jest lokalnym magazynem i nie jest czescia repozytorium.
$OriginalLibRoot = Join-Path $PSScriptRoot "_original-libs-11.3.0"
$JniLibRoot = Join-Path $PSScriptRoot "Common\src\main\jniLibs"
$KnownAbis = @("arm64-v8a", "armeabi-v7a", "x86_64", "x86")

if (-not (Test-Path $OriginalLibRoot)) {
    throw "Brak folderu z oryginalnymi bibliotekami Juggluco: $OriginalLibRoot"
}

$OriginalLibs = @(Get-ChildItem $OriginalLibRoot -Recurse -Filter "*.so" -File -ErrorAction Stop)
if ($OriginalLibs.Count -eq 0) {
    throw "Folder $OriginalLibRoot nie zawiera bibliotek .so."
}

$CopiedLibs = 0
$SkippedLibs = @()

foreach ($Lib in $OriginalLibs) {
    $Relative = $Lib.FullName.Substring($OriginalLibRoot.Length).TrimStart('\', '/')
    $Parts = $Relative -split '[\\/]'
    $Abi = $Parts | Where-Object { $_ -in $KnownAbis } | Select-Object -Last 1

    if (-not $Abi) {
        $SkippedLibs += $Relative
        continue
    }

    $TargetDir = Join-Path $JniLibRoot $Abi
    New-Item -ItemType Directory -Path $TargetDir -Force | Out-Null
    Copy-Item $Lib.FullName (Join-Path $TargetDir $Lib.Name) -Force
    $CopiedLibs++
}

Write-Host "Oryginalne biblioteki Juggluco: skopiowano $CopiedLibs plikow .so."

if ($SkippedLibs.Count -gt 0) {
    Write-Warning ("Pominieto {0} bibliotek bez rozpoznanego ABI: {1}" -f $SkippedLibs.Count, ($SkippedLibs -join ", "))
}

if ($CopiedLibs -eq 0) {
    throw "Nie skopiowano zadnej biblioteki. Oczekiwany jest katalog ABI, np. lib\arm64-v8a lub arm64-v8a."
}


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
