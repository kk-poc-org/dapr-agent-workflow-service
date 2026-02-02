# Clone all EntpFact organization repositories with all branches
# Usage: .\clone-entpfact-repos.ps1 [-TargetDir "C:\path\to\repos"] [-MaxParallel 4]

param(
    [string]$TargetDir = "C:\Users\K20100\Development\Projects\HDFC_Workspace\EntpFact-Repos",
    [int]$MaxParallel = 4
)

$ErrorActionPreference = "Continue"

# Get GitHub token from git credential manager
$tokenOutput = echo "protocol=https`nhost=github.com" | git credential fill 2>$null
$token = ($tokenOutput | Select-String -Pattern "password=(.+)").Matches.Groups[1].Value

if (-not $token) {
    Write-Error "Could not retrieve GitHub token. Please run 'git credential fill' manually."
    exit 1
}

Write-Host "=== EntpFact Repository Cloner ===" -ForegroundColor Cyan
Write-Host "Target Directory: $TargetDir" -ForegroundColor Yellow
Write-Host ""

# Create target directory
if (-not (Test-Path $TargetDir)) {
    New-Item -ItemType Directory -Path $TargetDir -Force | Out-Null
    Write-Host "Created directory: $TargetDir" -ForegroundColor Green
}

# Fetch all repositories from EntpFact org
Write-Host "Fetching repository list from EntpFact organization..." -ForegroundColor Cyan
$headers = @{
    Authorization = "Bearer $token"
    Accept = "application/vnd.github+json"
}

$allRepos = @()
$page = 1
do {
    $response = Invoke-RestMethod -Uri "https://api.github.com/orgs/EntpFact/repos?per_page=100&page=$page" -Headers $headers
    $allRepos += $response
    $page++
} while ($response.Count -eq 100)

Write-Host "Found $($allRepos.Count) repositories" -ForegroundColor Green
Write-Host ""

# Create log file
$logFile = Join-Path $TargetDir "clone-log-$(Get-Date -Format 'yyyyMMdd-HHmmss').txt"
"Clone started at $(Get-Date)" | Out-File $logFile

# Clone function
function Clone-RepoWithBranches {
    param($repo, $targetDir, $token)
    
    $repoName = $repo.name
    $cloneUrl = $repo.clone_url -replace "https://", "https://${token}@"
    $repoPath = Join-Path $targetDir $repoName
    
    $result = @{
        Name = $repoName
        Status = "Unknown"
        Branches = 0
        Error = ""
    }
    
    try {
        if (Test-Path $repoPath) {
            # Repository exists, fetch all branches
            Write-Host "  Updating: $repoName" -ForegroundColor Yellow
            Push-Location $repoPath
            git fetch --all --prune 2>&1 | Out-Null
            $branches = git branch -r 2>&1
            Pop-Location
            $result.Status = "Updated"
            $result.Branches = ($branches | Measure-Object).Count
        } else {
            # Clone the repository
            Write-Host "  Cloning: $repoName" -ForegroundColor Cyan
            git clone --mirror $cloneUrl "$repoPath.git" 2>&1 | Out-Null
            
            # Convert bare repo to regular repo with all branches
            git clone "$repoPath.git" $repoPath 2>&1 | Out-Null
            Push-Location $repoPath
            
            # Fetch all remote branches and create local tracking branches
            $remoteBranches = git branch -r 2>&1 | Where-Object { $_ -notmatch "HEAD" }
            foreach ($branch in $remoteBranches) {
                $branchName = $branch.Trim() -replace "origin/", ""
                if ($branchName -ne $repo.default_branch) {
                    git branch --track $branchName "origin/$branchName" 2>&1 | Out-Null
                }
            }
            Pop-Location
            
            # Remove the mirror
            Remove-Item -Recurse -Force "$repoPath.git" 2>$null
            
            $result.Status = "Cloned"
            $result.Branches = ($remoteBranches | Measure-Object).Count
        }
    } catch {
        $result.Status = "Failed"
        $result.Error = $_.Exception.Message
    }
    
    return $result
}

# Process repositories
$total = $allRepos.Count
$current = 0
$success = 0
$failed = 0
$results = @()

Write-Host "Starting clone process..." -ForegroundColor Cyan
Write-Host "=" * 60

foreach ($repo in $allRepos) {
    $current++
    $pct = [math]::Round(($current / $total) * 100)
    Write-Host "[$current/$total] ($pct%)" -NoNewline -ForegroundColor Magenta
    
    $result = Clone-RepoWithBranches -repo $repo -targetDir $TargetDir -token $token
    $results += $result
    
    if ($result.Status -eq "Failed") {
        Write-Host " FAILED: $($result.Name) - $($result.Error)" -ForegroundColor Red
        $failed++
    } else {
        Write-Host " $($result.Status): $($result.Name) ($($result.Branches) branches)" -ForegroundColor Green
        $success++
    }
    
    # Log result
    "$($result.Name): $($result.Status) - $($result.Branches) branches" | Out-File $logFile -Append
}

# Summary
Write-Host ""
Write-Host "=" * 60
Write-Host "=== Clone Complete ===" -ForegroundColor Cyan
Write-Host "Total Repos: $total" -ForegroundColor White
Write-Host "Successful: $success" -ForegroundColor Green
Write-Host "Failed: $failed" -ForegroundColor $(if ($failed -gt 0) { "Red" } else { "Green" })
Write-Host "Log file: $logFile" -ForegroundColor Yellow
Write-Host ""
Write-Host "Repos saved to: $TargetDir" -ForegroundColor Cyan

