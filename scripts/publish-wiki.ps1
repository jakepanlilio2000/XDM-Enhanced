# scripts/publish-wiki.ps1
# Synchronizes the 27 markdown documentation pages from the wiki/ directory
# to the repository's GitHub Wiki git clone (jakepanlilio2000/XDM-Enhanced.wiki.git).

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path "$PSScriptRoot\.."
$wikiSrcDir = Join-Path $repoRoot "wiki"
$wikiRepoUrl = "https://github.com/jakepanlilio2000/XDM-Enhanced.wiki.git"
$tempWikiDir = Join-Path $env:TEMP "XDM-Enhanced-wiki-deploy"

Write-Host "==> XDM Enhanced Wiki Sync Tool <==" -ForegroundColor Cyan

if (-not (Test-Path $wikiSrcDir)) {
    Write-Error "Source wiki directory not found at $wikiSrcDir"
    exit 1
}

$pageFiles = Get-ChildItem "$wikiSrcDir\*.md"
Write-Host "Found $($pageFiles.Count) markdown pages in $wikiSrcDir" -ForegroundColor Green

if (Test-Path $tempWikiDir) {
    Write-Host "Cleaning existing temporary clone directory..."
    Remove-Item -Recurse -Force $tempWikiDir
}

Write-Host "Checking out remote wiki repository from $wikiRepoUrl..."
try {
    # Ensure gh credentials helper is active if available
    if (Get-Command gh -ErrorAction SilentlyContinue) {
        gh auth setup-git 2>$null
    }
    git clone $wikiRepoUrl $tempWikiDir
} catch {
    Write-Host "Clone failed. Please ensure the GitHub Wiki has been initialized once via github.com/jakepanlilio2000/XDM-Enhanced/wiki by clicking 'Create the first page'." -ForegroundColor Yellow
    exit 1
}

Write-Host "Copying $($pageFiles.Count) pages into wiki repository..."
Copy-Item "$wikiSrcDir\*.md" $tempWikiDir -Force

Push-Location $tempWikiDir
try {
    git add .
    $status = git status --porcelain
    if ($status) {
        git commit -m "docs(wiki): synchronize 27 user and developer guide pages"
        git push origin master
        Write-Host "Wiki pages successfully synchronized and published!" -ForegroundColor Green
    } else {
        Write-Host "Wiki is already up-to-date. No changes to commit." -ForegroundColor Cyan
    }
} finally {
    Pop-Location
}
