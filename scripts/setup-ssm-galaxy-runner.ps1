param(
    [string]$RegistrationToken = "",
    [string]$InstallDir = "C:\actions-runner-ssm",
    [string]$Repo = "tjqmf14-source/ssm"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Fail([string]$Message) {
    Write-Error $Message
    exit 1
}

$isAdmin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
    [Security.Principal.WindowsBuiltInRole]::Administrator
)
if (-not $isAdmin) { Fail "관리자 권한 PowerShell에서 실행해야 합니다." }

# 기존 CoreCare runner(C:\actions-runner-corecare)는 건드리지 않는다.
if ($InstallDir -ieq "C:\actions-runner-corecare") {
    Fail "CoreCare runner 경로는 사용할 수 없습니다. SSM 전용 경로를 사용하세요."
}

$repoUrl = "https://github.com/$Repo"
$runnerName = "SSM-Galaxy-$env:COMPUTERNAME"

if (Test-Path (Join-Path $InstallDir ".runner")) {
    Write-Host "SSM runner가 이미 구성되어 있습니다: $InstallDir"
    $serviceFile = Join-Path $InstallDir ".service"
    if (Test-Path $serviceFile) {
        $svc = (Get-Content $serviceFile -Raw).Trim()
        Set-Service -Name $svc -StartupType Automatic
        Start-Service -Name $svc
        Get-Service -Name $svc | Format-List Name,Status,StartType
    } else {
        Fail "기존 runner는 있지만 Windows 서비스 구성이 없습니다. $InstallDir 을 점검하세요."
    }
    exit 0
}

if ([string]::IsNullOrWhiteSpace($RegistrationToken)) {
    $gh = Get-Command gh -ErrorAction SilentlyContinue
    if ($gh) {
        Write-Host "GitHub CLI 인증을 사용해 SSM runner 등록 토큰을 발급합니다."
        $RegistrationToken = (& gh api -X POST "repos/$Repo/actions/runners/registration-token" --jq .token).Trim()
        if ($LASTEXITCODE -ne 0) { Fail "gh로 runner 등록 토큰을 발급하지 못했습니다. gh auth status를 확인하세요." }
    }
}

if ([string]::IsNullOrWhiteSpace($RegistrationToken)) {
    Fail "Runner 등록 토큰이 필요합니다. GitHub > $Repo > Settings > Actions > Runners > New self-hosted runner 에서 토큰을 발급한 뒤 -RegistrationToken 값으로 전달하세요."
}

New-Item -ItemType Directory -Force -Path $InstallDir | Out-Null

Write-Host "최신 GitHub Actions runner를 확인합니다."
$release = Invoke-RestMethod -Headers @{ "User-Agent" = "ssm-galaxy-runner-setup" } -Uri "https://api.github.com/repos/actions/runner/releases/latest"
$asset = $release.assets | Where-Object { $_.name -match '^actions-runner-win-x64-.*\.zip$' } | Select-Object -First 1
if (-not $asset) { Fail "Windows x64 GitHub Actions runner 패키지를 찾지 못했습니다." }

$zip = Join-Path $env:TEMP $asset.name
Write-Host "다운로드: $($asset.name)"
Invoke-WebRequest -UseBasicParsing -Uri $asset.browser_download_url -OutFile $zip
Expand-Archive -Path $zip -DestinationPath $InstallDir -Force
Remove-Item $zip -Force -ErrorAction SilentlyContinue

Push-Location $InstallDir
try {
    Write-Host "SSM 전용 self-hosted runner를 등록합니다: $runnerName"
    & .\config.cmd --unattended --replace --url $repoUrl --token $RegistrationToken --name $runnerName --labels "galaxy" --work "_work" --runasservice --windowslogonaccount "NT AUTHORITY\SYSTEM"
    if ($LASTEXITCODE -ne 0) { Fail "GitHub Actions runner 등록에 실패했습니다." }

    if (!(Test-Path ".service")) { Fail "runner 서비스 파일(.service)이 생성되지 않았습니다." }

    $svc = (Get-Content ".service" -Raw).Trim()
    Set-Service -Name $svc -StartupType Automatic
    Start-Service -Name $svc

    $state = Get-CimInstance Win32_Service -Filter "Name='$svc'" | Select-Object Name,State,StartMode,StartName
    $state | Format-List
    $state | ConvertTo-Json | Out-File -Encoding utf8 "ssm-runner-service.json"

    if ($state.State -ne "Running") { Fail "SSM runner 서비스가 Running 상태가 아닙니다." }
    if ($state.StartName -notmatch "(?i)LocalSystem|SYSTEM") { Fail "SSM runner 서비스가 LocalSystem으로 실행되지 않습니다." }

    Write-Host ""
    Write-Host "SSM Galaxy runner 등록 완료."
    Write-Host "다음으로 Galaxy를 USB 연결하고 USB 디버깅을 허용하세요."
    if (Get-Command adb -ErrorAction SilentlyContinue) {
        adb devices -l
    } else {
        Write-Warning "adb가 PATH에 없습니다. Galaxy QA workflow의 SDK 단계에서 adb는 설치되지만, 로컬 연결 확인에는 Android platform-tools가 필요합니다."
    }
}
finally {
    Pop-Location
}
