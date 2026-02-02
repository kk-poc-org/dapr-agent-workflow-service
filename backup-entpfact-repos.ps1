# ============================================================================
# EntpFact GitHub Organization - Full Repository Backup Script
# ============================================================================
# This script clones ALL repositories from the EntpFact organization
# with FULL working copies including src, test, and ALL branches
#
# Prerequisites:
# 1. Git installed and in PATH
# 2. GitHub authentication configured (gh auth login OR git credentials)
# 3. Sufficient disk space for 238 repositories
#
# Usage: .\backup-entpfact-repos.ps1 [-BackupDir "C:\path\to\backup"]
# ============================================================================

param(
    [string]$BackupDir = "C:\Users\K20100\Development\Projects\EntpFact_Backup",
    [string]$OrgName = "EntpFact",
    [string]$GitHubToken = $env:GITHUB_TOKEN,
    [int]$PerPage = 100
)

# Function to clone repo and checkout all branches
function Clone-RepoWithAllBranches {
    param(
        [string]$RepoUrl,
        [string]$RepoDir,
        [string]$RepoName
    )

    # Clone the repository (full clone, not bare)
    $cloneResult = git clone $RepoUrl $RepoDir 2>&1
    if ($LASTEXITCODE -ne 0) {
        return @{ Success = $false; Error = $cloneResult }
    }

    # Change to repo directory
    Push-Location $RepoDir

    # Fetch all remote branches
    git fetch --all 2>&1 | Out-Null

    # Get all remote branches and create local tracking branches
    $remoteBranches = git branch -r 2>&1 | ForEach-Object { $_.Trim() } | Where-Object { $_ -notmatch "HEAD" }

    $branchCount = 0
    foreach ($remoteBranch in $remoteBranches) {
        # Extract branch name (remove origin/ prefix)
        $branchName = $remoteBranch -replace "^origin/", ""

        # Skip if it's the default branch (already checked out)
        $currentBranch = git branch --show-current 2>&1
        if ($branchName -eq $currentBranch) { continue }

        # Create local branch tracking the remote
        git branch --track $branchName $remoteBranch 2>&1 | Out-Null
        $branchCount++
    }

    Pop-Location

    return @{ Success = $true; BranchCount = $branchCount + 1 }
}

# Colors for output
function Write-Success { param($msg) Write-Host "[OK] $msg" -ForegroundColor Green }
function Write-Info { param($msg) Write-Host "[INFO] $msg" -ForegroundColor Cyan }
function Write-Warn { param($msg) Write-Host "[WARN] $msg" -ForegroundColor Yellow }
function Write-Err { param($msg) Write-Host "[ERROR] $msg" -ForegroundColor Red }

Write-Host ""
Write-Host ("=" * 70) -ForegroundColor Magenta
Write-Host "  EntpFact GitHub Organization - Full Repository Backup" -ForegroundColor Magenta
Write-Host ("=" * 70) -ForegroundColor Magenta
Write-Host ""

# Create backup directory
if (-not (Test-Path $BackupDir)) {
    New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null
    Write-Success "Created backup directory: $BackupDir"
} else {
    Write-Info "Using existing backup directory: $BackupDir"
}

# Use git credentials - no need for gh CLI
Write-Info "Using existing git credentials for cloning"

# Function to fetch all repositories using GitHub REST API with pagination
function Get-AllRepos {
    Write-Info "Fetching all repository names from GitHub API..."
    $allRepos = @()
    $page = 1

    $headers = @{ "Accept" = "application/vnd.github.v3+json" }
    if ($GitHubToken) {
        $headers["Authorization"] = "token $GitHubToken"
    }

    do {
        $url = "https://api.github.com/orgs/$OrgName/repos?per_page=$PerPage&page=$page&type=all"
        Write-Info "  Fetching page $page..."

        try {
            $response = Invoke-RestMethod -Uri $url -Headers $headers -Method Get
            if ($response.Count -eq 0) { break }
            $allRepos += $response
            Write-Info "    Got $($response.Count) repos (total: $($allRepos.Count))"
            $page++
        } catch {
            Write-Err "API Error: $_"
            break
        }
    } while ($response.Count -eq $PerPage)

    return $allRepos
}

# Fetch all repositories
$repos = Get-AllRepos

if (-not $repos -or $repos.Count -eq 0) {
    Write-Err "No repositories found or failed to fetch. Exiting."
    exit 1
}

$totalRepos = $repos.Count
Write-Success "Found $totalRepos repositories to backup"
Write-Host ""

# Create log file
$logFile = Join-Path $BackupDir "backup_log_$(Get-Date -Format 'yyyyMMdd_HHmmss').txt"
"Backup started at $(Get-Date)" | Out-File $logFile

# Clone each repository with --mirror
$successCount = 0
$failCount = 0
$skippedCount = 0

for ($i = 0; $i -lt $repos.Count; $i++) {
    $repo = $repos[$i]
    $repoName = if ($useGhCli) { $repo.name } else { $repo.name }

    $progress = [math]::Round((($i + 1) / $totalRepos) * 100, 1)
    Write-Host "[$($i + 1)/$totalRepos] ($progress%) " -NoNewline -ForegroundColor White

    # Full working directory (not .git bare repo)
    $repoDir = Join-Path $BackupDir $repoName

    # Check if already cloned
    if (Test-Path $repoDir) {
        Write-Warn "Updating $repoName (already exists)..."
        Push-Location $repoDir
        git fetch --all 2>&1 | Out-Null
        # Pull all branches
        $branches = git branch -r 2>&1 | ForEach-Object { $_.Trim() } | Where-Object { $_ -notmatch "HEAD" }
        foreach ($branch in $branches) {
            $localBranch = $branch -replace "^origin/", ""
            git checkout $localBranch 2>&1 | Out-Null
            git pull origin $localBranch 2>&1 | Out-Null
        }
        Pop-Location
        $skippedCount++
        "UPDATED: $repoName" | Out-File $logFile -Append
        continue
    }

    Write-Host "Cloning $repoName with all branches..." -NoNewline

    # Clone full working copy with all branches
    $cloneUrl = "https://github.com/$OrgName/$repoName.git"
    $result = Clone-RepoWithAllBranches -RepoUrl $cloneUrl -RepoDir $repoDir -RepoName $repoName

    if ($result.Success) {
        Write-Success " Done ($($result.BranchCount) branches)"
        $successCount++
        "SUCCESS: $repoName - $($result.BranchCount) branches" | Out-File $logFile -Append
    } else {
        Write-Err " Failed"
        $failCount++
        "FAILED: $repoName - $($result.Error)" | Out-File $logFile -Append
    }
}

# Summary
Write-Host ""
Write-Host ("=" * 70) -ForegroundColor Magenta
Write-Host "  Backup Complete" -ForegroundColor Magenta
Write-Host ("=" * 70) -ForegroundColor Magenta
Write-Host ""
Write-Success "Successfully cloned: $successCount"
Write-Warn "Updated (existed):  $skippedCount"
Write-Err "Failed:             $failCount"
Write-Info "Total:              $totalRepos"
Write-Host ""
Write-Info "Backup location: $BackupDir"
Write-Info "Log file: $logFile"

