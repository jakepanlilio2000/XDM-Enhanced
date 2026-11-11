<#
.SYNOPSIS
    Packages and validates the XDM Enhanced browser extensions for Chromium and Firefox.

.DESCRIPTION
    Validates manifest files, referenced resources, and CSP requirements, then generates
    clean release zip archives for both Chromium (Manifest V3) and Firefox (Manifest V2).

.PARAMETER Version
    The release version tag to package (default: reads from xdm-app/src/main/resources/app-version.json or '1.0.0-beta.6').

.PARAMETER OutDir
    Target directory for distribution artifacts (default: build/dist/extensions).
#>
param(
    [string]$Version = "",
    [string]$OutDir = ""
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = Resolve-Path (Join-Path $ScriptDir "..")

if (-not $Version) {
    $versionJson = Join-Path $RepoRoot "xdm-app\src\main\resources\app-version.json"
    if (Test-Path $versionJson) {
        try {
            $Version = (Get-Content $versionJson -Raw | ConvertFrom-Json).currentVersion
        } catch {}
    }
    if (-not $Version) {
        $Version = "1.0.0-beta.6"
    }
}

if (-not $OutDir) {
    $OutDir = Join-Path $RepoRoot "build\dist\extensions"
}

$BuildDir = Join-Path $ScriptDir "build"

if (-not (Test-Path $OutDir)) { New-Item -ItemType Directory -Path $OutDir -Force | Out-Null }
if (-not (Test-Path $BuildDir)) { New-Item -ItemType Directory -Path $BuildDir -Force | Out-Null }

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " XDM Enhanced — Browser Extension Packaging ($Version)" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

function Assert-Condition($condition, $message) {
    if (-not $condition) {
        Write-Error "VALIDATION FAILED: $message"
        exit 1
    }
}

function Validate-Extension($extDir, [bool]$isChrome) {
    $kind = if ($isChrome) { "Chromium (MV3)" } else { "Firefox (MV2)" }
    Write-Host "`n>> Validating $kind extension at: $extDir" -ForegroundColor Yellow
    
    $manifestFile = Join-Path $extDir "manifest.json"
    Assert-Condition (Test-Path $manifestFile) "manifest.json not found in $extDir"
    
    $manifest = Get-Content $manifestFile -Raw -Encoding UTF8 | ConvertFrom-Json
    
    Write-Host "   Name:        $($manifest.name)"
    Write-Host "   Version:     $($manifest.version)"
    if ($manifest.version_name) {
        Write-Host "   VersionName: $($manifest.version_name)"
    }
    Write-Host "   Manifest:    v$($manifest.manifest_version)"
    
    # Version regex: 1 to 4 dot-separated integers
    Assert-Condition ($manifest.version -match '^\d+(\.\d+){0,3}$') "Invalid numeric version format: $($manifest.version)"
    
    if ($isChrome) {
        Assert-Condition ($manifest.manifest_version -eq 3) "Chromium manifest_version must be 3"
        Assert-Condition ($manifest.name.Length -le 75) "Extension name exceeds 75 chars ($($manifest.name.Length))"
        Assert-Condition ($manifest.description.Length -le 132) "Description exceeds 132 chars ($($manifest.description.Length))"
    } else {
        Assert-Condition ($manifest.manifest_version -eq 2) "Firefox manifest_version must be 2"
        Assert-Condition ($manifest.name.Length -le 50) "Extension name exceeds 50 chars ($($manifest.name.Length))"
        Assert-Condition ($manifest.description.Length -le 132) "Description exceeds 132 chars ($($manifest.description.Length))"
        
        $gecko = $manifest.browser_specific_settings.gecko
        Assert-Condition ($null -ne $gecko -and -not [string]::IsNullOrWhiteSpace($gecko.id)) "Firefox requires browser_specific_settings.gecko.id"
        Assert-Condition ($null -ne $gecko.data_collection_permissions) "Firefox AMO requires gecko.data_collection_permissions"
        Assert-Condition ($null -ne $gecko.update_url -and $gecko.update_url -match '^https://') "Firefox requires gecko.update_url over HTTPS"
        Write-Host "   Gecko ID:    $($gecko.id)"
        Write-Host "   Update URL:  $($gecko.update_url)"
    }
    
    # Collect referenced files from manifest
    $refFiles = @()
    if ($manifest.icons) {
        $manifest.icons.psobject.Properties | ForEach-Object { $refFiles += $_.Value }
    }
    if ($manifest.action -and $manifest.action.default_icon) {
        $manifest.action.default_icon.psobject.Properties | ForEach-Object { $refFiles += $_.Value }
    }
    if ($manifest.browser_action -and $manifest.browser_action.default_icon) {
        $manifest.browser_action.default_icon.psobject.Properties | ForEach-Object { $refFiles += $_.Value }
    }
    if ($manifest.background) {
        if ($manifest.background.service_worker) { $refFiles += $manifest.background.service_worker }
        if ($manifest.background.page) { $refFiles += $manifest.background.page }
    }
    if ($manifest.options_ui -and $manifest.options_ui.page) {
        $refFiles += $manifest.options_ui.page
    }
    
    foreach ($rf in $refFiles) {
        $fullPath = Join-Path $extDir $rf
        Assert-Condition (Test-Path $fullPath) "Manifest references missing file: $rf"
    }
    Write-Host "   Manifest referenced files: $($refFiles.Count) verified" -ForegroundColor Green

    # Validate HTML pages: ensure scripts and links reference existing local files, no remote URLs, no inline scripts
    $htmlFiles = Get-ChildItem -Path $extDir -Filter *.html -Recurse
    foreach ($h in $htmlFiles) {
        $content = Get-Content $h.FullName -Raw -Encoding UTF8
        
        # Check script tags
        $scriptMatches = [regex]::Matches($content, '<script\b[^>]*\bsrc=[\x22\x27]([^\x22\x27]+)[\x22\x27][^>]*>')
        foreach ($sm in $scriptMatches) {
            $src = $sm.Groups[1].Value
            Assert-Condition (-not ($src -match '^(https?:|//)')) "$($h.Name) references remote script: $src"
            $localTarget = Join-Path $h.DirectoryName $src
            $localTarget = $localTarget -replace '\?.*$', ''
            Assert-Condition (Test-Path $localTarget) "$($h.Name) references missing local script: $src"
        }
        
        # Check stylesheet links
        $linkMatches = [regex]::Matches($content, '<link\b[^>]*\bhref=[\x22\x27]([^\x22\x27]+)[\x22\x27][^>]*>')
        foreach ($lm in $linkMatches) {
            $href = $lm.Groups[1].Value
            # Only check css stylesheets or icon links
            if ($lm.Value -match 'rel=[\x22\x27](stylesheet|icon)[\x22\x27]' -or $href -match '\.css($|\?)') {
                Assert-Condition (-not ($href -match '^(https?:|//)')) "$($h.Name) references remote stylesheet: $href"
                $localTarget = Join-Path $h.DirectoryName $href
                $localTarget = $localTarget -replace '\?.*$', ''
                Assert-Condition (Test-Path $localTarget) "$($h.Name) references missing local link: $href"
            }
        }
        
        # Check inline scripts (<script>...content...</script>)
        $inlineScripts = [regex]::Matches($content, '(?s)<script\b(?![^>]*\bsrc=)[^>]*>(.*?)</script>')
        foreach ($is in $inlineScripts) {
            $code = $is.Groups[1].Value.Trim()
            Assert-Condition ($code.Length -eq 0) "$($h.Name) contains inline script (forbidden by MV3 and strict MV2 CSP)"
        }
    }
    Write-Host "   HTML pages: $($htmlFiles.Count) verified (CSP clean, no remote code, no inline scripts)" -ForegroundColor Green

    # Validate JS files for eval and new Function
    $jsFiles = Get-ChildItem -Path $extDir -Filter *.js -Recurse
    foreach ($j in $jsFiles) {
        $jsContent = Get-Content $j.FullName -Raw -Encoding UTF8
        Assert-Condition (-not ($jsContent -match '\beval\s*\(')) ($j.Name + ' contains eval()')
        Assert-Condition (-not ($jsContent -match '\bnew\s+Function\s*\(')) ($j.Name + ' contains new Function()')
        if ($isChrome) {
            Assert-Condition (-not ($jsContent -match '\bimportScripts\s*\(')) ($j.Name + ' contains importScripts()')
            Assert-Condition (-not ($jsContent -match 'import\s*\(\s*[\x22\x27]https?:')) ($j.Name + ' contains dynamic remote import')
        }
    }
    Write-Host "   JavaScript files: $($jsFiles.Count) verified (no eval, no new Function, no remote imports)" -ForegroundColor Green
    
    return $manifest
}

function Create-CleanZip($srcDir, $destZip) {
    if (Test-Path $destZip) { Remove-Item $destZip -Force }
    
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    Add-Type -AssemblyName System.IO.Compression
    
    $tempZip = [System.IO.Path]::GetTempFileName() + ".zip"
    if (Test-Path $tempZip) { Remove-Item $tempZip -Force }
    
    # Collect files excluding hidden and OS files
    $archive = [System.IO.Compression.ZipFile]::Open($tempZip, [System.IO.Compression.ZipArchiveMode]::Create)
    try {
        $allFiles = Get-ChildItem -Path $srcDir -Recurse -File | Where-Object {
            $_.Name -notmatch '^\.' -and $_.Name -ne 'Thumbs.db' -and $_.FullName -notmatch '\\\.'
        }
        
        foreach ($file in $allFiles) {
            $relative = $file.FullName.Substring($srcDir.Length).TrimStart([char[]]@('\', '/'))
            # Normalize to forward slashes in zip
            $zipEntryPath = $relative -replace '\\', '/'
            [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $file.FullName, $zipEntryPath, [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
        }
    } finally {
        $archive.Dispose()
    }
    
    Move-Item -Path $tempZip -Destination $destZip -Force
}

# --- Validate and Package Chrome Extension ---
$chromeDir = Join-Path $ScriptDir "chrome-extension"
$chromeManifest = Validate-Extension $chromeDir $true

$chromeRelZip = Join-Path $OutDir "XDM-Enhanced-Extension-Chromium-v$Version.zip"
$chromeBuildZip = Join-Path $BuildDir "xdm-chrome-$($chromeManifest.version).zip"

Write-Host ">> Packaging Chromium extension to:" -ForegroundColor Cyan
Write-Host "   $chromeRelZip"
Create-CleanZip $chromeDir $chromeRelZip
Copy-Item $chromeRelZip $chromeBuildZip -Force

# --- Validate Updates Manifests ---
$updatesJsonPath = Join-Path $ScriptDir "updates.json"
Assert-Condition (Test-Path $updatesJsonPath) "updates.json not found in $ScriptDir"
$updatesJson = Get-Content $updatesJsonPath -Raw -Encoding UTF8 | ConvertFrom-Json
Assert-Condition ($null -ne $updatesJson.addons."xdm-enhanced-browser-monitor@jakepanlilio2000.github.io") "updates.json missing Firefox Gecko ID entry"
Write-Host "`n>> Validated Firefox updates.json successfully" -ForegroundColor Green

$chromeXmlPath = Join-Path $ScriptDir "chrome-updates.xml"
Assert-Condition (Test-Path $chromeXmlPath) "chrome-updates.xml not found in $ScriptDir"
[xml]$chromeXml = Get-Content $chromeXmlPath -Raw -Encoding UTF8
Assert-Condition ($chromeXml.gupdate.app.appid -eq "xdmenhancedchromebrowserintegration0") "chrome-updates.xml missing valid appid"
Write-Host ">> Validated Chromium chrome-updates.xml successfully" -ForegroundColor Green

# --- Validate and Package Firefox Extension ---
$firefoxDir = Join-Path $ScriptDir "firefox-extension"
$firefoxManifest = Validate-Extension $firefoxDir $false

$firefoxRelZip = Join-Path $OutDir "XDM-Enhanced-Extension-Firefox-v$Version.zip"
$firefoxRelXpi = Join-Path $OutDir "XDM-Enhanced-Extension-Firefox-v$Version.xpi"
$firefoxBuildZip = Join-Path $BuildDir "xdm-firefox-$($firefoxManifest.version).zip"
$firefoxBuildXpi = Join-Path $BuildDir "xdm-firefox-$($firefoxManifest.version).xpi"

Write-Host ">> Packaging Firefox extension to:" -ForegroundColor Cyan
Write-Host "   $firefoxRelZip"
Write-Host "   $firefoxRelXpi"
Create-CleanZip $firefoxDir $firefoxRelZip
Copy-Item $firefoxRelZip $firefoxRelXpi -Force
Copy-Item $firefoxRelZip $firefoxBuildZip -Force
Copy-Item $firefoxRelZip $firefoxBuildXpi -Force

# --- Summary & Checksums ---
Write-Host "`n==========================================================" -ForegroundColor Green
Write-Host " Browser Extension Packages Successfully Created!" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green

$packages = @(
    @{ Name = "Chromium (MV3)";    File = $chromeRelZip;  Version = $chromeManifest.version;  VersionName = $chromeManifest.version_name },
    @{ Name = "Firefox (MV2 Zip)"; File = $firefoxRelZip; Version = $firefoxManifest.version; VersionName = "v$Version" },
    @{ Name = "Firefox (MV2 XPI)"; File = $firefoxRelXpi; Version = $firefoxManifest.version; VersionName = "v$Version" }
)

$summary = foreach ($pkg in $packages) {
    $f = Get-Item $pkg.File
    $hash = (Get-FileHash -Path $pkg.File -Algorithm SHA256).Hash
    [PSCustomObject]@{
        Browser          = $pkg.Name
        "Package File"   = $f.Name
        "Size (KB)"      = [math]::Round($f.Length / 1KB, 2)
        "Manifest Ver"   = $pkg.Version
        "Version Name"   = $pkg.VersionName
        "SHA-256 Hash"   = $hash
    }
}

$summary | Format-Table -AutoSize

Write-Host ""
Write-Host "Distribution folder: $OutDir" -ForegroundColor Cyan

