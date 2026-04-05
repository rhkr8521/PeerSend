param(
    [string]$OutputDir = ""
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RootDir = Resolve-Path (Join-Path $ScriptDir "..\..")

if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $OutputDir = Join-Path $RootDir "dist\windows"
}

$SpecPath = Join-Path $ScriptDir "PeerSendStandalone.spec"
$BuildDir = Join-Path $OutputDir "build"
$DistDir = Join-Path $OutputDir "dist"
$AppDir = Join-Path $DistDir "PeerSend"
$InstallerScript = Join-Path $ScriptDir "PeerSendSetup.iss"
$PngIcon = Join-Path $RootDir "icon.png"
$IcoPath = Join-Path $OutputDir "PeerSend.ico"
$VersionPath = Join-Path $RootDir "engine\version.py"

function Get-PeerSendVersion {
    param(
        [Parameter(Mandatory = $true)][string]$VersionFile
    )

    if (-not (Test-Path $VersionFile)) {
        return "1.0.0"
    }

    $content = Get-Content -Path $VersionFile -Raw
    $match = [regex]::Match($content, 'ENGINE_VERSION\s*=\s*"([^"]+)"')
    if ($match.Success) {
        return $match.Groups[1].Value
    }
    return "1.0.0"
}

function Resolve-ISCCPath {
    $command = Get-Command ISCC.exe -ErrorAction SilentlyContinue
    if ($command -and $command.Source) {
        return $command.Source
    }

    $candidates = New-Object System.Collections.Generic.List[string]
    foreach ($baseDir in @($env:ProgramFiles, ${env:ProgramFiles(x86)}, $env:LocalAppData)) {
        if ([string]::IsNullOrWhiteSpace($baseDir)) {
            continue
        }
        foreach ($relativePath in @(
            "Inno Setup 6\ISCC.exe",
            "Inno Setup 5\ISCC.exe"
        )) {
            $candidates.Add((Join-Path $baseDir $relativePath))
        }
    }

    foreach ($registryPath in @(
        "HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\App Paths\ISCC.exe",
        "HKLM:\SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\App Paths\ISCC.exe",
        "HKCU:\SOFTWARE\Microsoft\Windows\CurrentVersion\App Paths\ISCC.exe",
        "HKCU:\SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\App Paths\ISCC.exe"
    )) {
        try {
            $registryKey = Get-Item -Path $registryPath -ErrorAction Stop
            $defaultValue = $registryKey.GetValue("")
            $pathValue = $registryKey.GetValue("Path")
            if ($defaultValue) {
                $candidates.Add([string]$defaultValue)
            }
            if ($pathValue) {
                $candidates.Add((Join-Path ([string]$pathValue) "ISCC.exe"))
            }
        }
        catch {
        }
    }

    foreach ($candidate in $candidates) {
        if (-not [string]::IsNullOrWhiteSpace($candidate) -and (Test-Path $candidate)) {
            return (Resolve-Path $candidate).Path
        }
    }

    return $null
}

function New-PeerSendIco {
    param(
        [Parameter(Mandatory = $true)][string]$SourcePng,
        [Parameter(Mandatory = $true)][string]$TargetIco
    )

    [byte[]]$pngBytes = [System.IO.File]::ReadAllBytes($SourcePng)
    $stream = [System.IO.MemoryStream]::new()
    $writer = [System.IO.BinaryWriter]::new($stream)
    try {
        $writer.Write([UInt16]0)
        $writer.Write([UInt16]1)
        $writer.Write([UInt16]1)
        $writer.Write([byte]0)
        $writer.Write([byte]0)
        $writer.Write([byte]0)
        $writer.Write([byte]0)
        $writer.Write([UInt16]1)
        $writer.Write([UInt16]32)
        $writer.Write([UInt32]$pngBytes.Length)
        $writer.Write([UInt32]22)
        $writer.Write($pngBytes)
        [System.IO.File]::WriteAllBytes($TargetIco, $stream.ToArray())
    }
    finally {
        $writer.Dispose()
        $stream.Dispose()
    }
}

if (-not (Get-Command pyinstaller -ErrorAction SilentlyContinue)) {
    throw "pyinstaller is not installed. Run: python -m pip install pyinstaller"
}

Push-Location $RootDir
try {
    $appVersion = Get-PeerSendVersion -VersionFile $VersionPath

    if (-not (Test-Path (Join-Path $RootDir "web\dist\index.html"))) {
        Push-Location (Join-Path $RootDir "web")
        try {
            npm run build
        }
        finally {
            Pop-Location
        }
    }

    Remove-Item $BuildDir, $DistDir -Recurse -Force -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force -Path $BuildDir, $DistDir | Out-Null

    if (Test-Path $PngIcon) {
        New-PeerSendIco -SourcePng $PngIcon -TargetIco $IcoPath
        $env:PEERSEND_WINDOWS_ICON = $IcoPath
    }
    else {
        Remove-Item Env:PEERSEND_WINDOWS_ICON -ErrorAction SilentlyContinue
    }

    pyinstaller --noconfirm --clean `
        --distpath $DistDir `
        --workpath $BuildDir `
        $SpecPath

    Write-Host "Created standalone Windows app:"
    Write-Host "  $AppDir"

    $isccPath = Resolve-ISCCPath
    if ($isccPath) {
        Write-Host "Using Inno Setup compiler:"
        Write-Host "  $isccPath"
        & $isccPath "/DSourceRoot=$AppDir" "/DOutputDir=$OutputDir" "/DAppVersion=$appVersion" $InstallerScript
        Write-Host "Created Windows installer in:"
        Write-Host "  $OutputDir"
    }
    else {
        Write-Warning "ISCC.exe was not found in PATH, the usual Inno Setup install folders, or App Paths registry entries. Standalone app is ready, but the installer was not built."
    }
}
finally {
    Pop-Location
}
