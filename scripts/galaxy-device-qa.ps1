param(
    [string]$EvidenceDir = "app\build\galaxy-real-device"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Fail([string]$Message) {
    Write-Error $Message
    exit 1
}

function Run-Adb {
    param([Parameter(ValueFromRemainingArguments=$true)][string[]]$Args)
    & adb @Args
    if ($LASTEXITCODE -ne 0) { Fail "ADB command failed: adb $($Args -join ' ')" }
}

function Invoke-Instrumentation {
    param(
        [string]$Serial,
        [string]$ClassFilter,
        [string]$EvidenceFile,
        [switch]$AllowFailure
    )

    $args = @("-s", $Serial, "shell", "am", "instrument", "-w", "-r")
    if (-not [string]::IsNullOrWhiteSpace($ClassFilter)) {
        $args += @("-e", "class", $ClassFilter)
    }
    $args += "com.ssm.app.test/androidx.test.runner.AndroidJUnitRunner"

    $output = & adb @args 2>&1
    $output | Out-File -Encoding utf8 $EvidenceFile
    $text = $output -join [Environment]::NewLine
    $ok = $LASTEXITCODE -eq 0 -and
          $text -match "OK \(" -and
          $text -notmatch "FAILURES!!!|Process crashed|INSTRUMENTATION_FAILED|INSTRUMENTATION_CODE: -1"

    if (-not $ok -and -not $AllowFailure) {
        Write-Host $text
        Fail "ON_DEVICE_INSTRUMENTATION_FAILED: $ClassFilter"
    }
    return $ok
}

New-Item -ItemType Directory -Force -Path $EvidenceDir | Out-Null

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) { Fail "ADB_NOT_FOUND" }
if (-not (Get-Command gradle -ErrorAction SilentlyContinue)) { Fail "GRADLE_NOT_FOUND" }

Run-Adb start-server
$deviceLines = @((& adb devices) | Select-Object -Skip 1 | Where-Object { $_ -match "\S+\s+device$" })
(& adb devices -l) | Out-File -Encoding utf8 "$EvidenceDir\adb-devices.txt"
if ($deviceLines.Count -ne 1) {
    Fail "GALAXY_DEVICE_REQUIRED: exactly one authorized Android device must be connected."
}

$serial = (($deviceLines[0] -split "\s+")[0]).Trim()
$manufacturer = (& adb -s $serial shell getprop ro.product.manufacturer).Trim()
$model = (& adb -s $serial shell getprop ro.product.model).Trim()
$android = (& adb -s $serial shell getprop ro.build.version.release).Trim()
$sdk = (& adb -s $serial shell getprop ro.build.version.sdk).Trim()

@(
    "serial=$serial"
    "manufacturer=$manufacturer"
    "model=$model"
    "android=$android"
    "sdk=$sdk"
) | Out-File -Encoding utf8 "$EvidenceDir\device-info.txt"

if ($manufacturer -notmatch "(?i)samsung") {
    Fail "NOT_SAMSUNG_DEVICE: manufacturer=$manufacturer model=$model"
}

Write-Host "Galaxy detected: $manufacturer $model / Android $android (SDK $sdk)"

gradle --no-daemon clean assembleDebug assembleDebugAndroidTest
if ($LASTEXITCODE -ne 0) { Fail "BUILD_FAILED" }

$appApk = "app\build\outputs\apk\debug\app-debug.apk"
$testApk = "app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk"
if (!(Test-Path $appApk)) { Fail "DEBUG_APK_MISSING" }
if (!(Test-Path $testApk)) { Fail "ANDROID_TEST_APK_MISSING" }

& adb -s $serial install -r -t $appApk | Tee-Object -FilePath "$EvidenceDir\install-app.txt"
if ($LASTEXITCODE -ne 0) {
    Fail "APP_INSTALL_FAILED. Existing com.ssm.app may use a different signing key; the QA script will not uninstall automatically because that can erase user data."
}
& adb -s $serial install -r -t $testApk | Tee-Object -FilePath "$EvidenceDir\install-test.txt"
if ($LASTEXITCODE -ne 0) { Fail "TEST_APK_INSTALL_FAILED" }

Run-Adb -s $serial shell pm grant com.ssm.app android.permission.READ_CALENDAR
Run-Adb -s $serial shell pm grant com.ssm.app android.permission.WRITE_CALENDAR
Run-Adb -s $serial shell cmd notification allow_listener com.ssm.app/.PaymentNotificationListener
Start-Sleep -Seconds 2

$enabledListeners = (& adb -s $serial shell settings get secure enabled_notification_listeners).Trim()
$enabledListeners | Out-File -Encoding utf8 "$EvidenceDir\notification-listener.txt"
if ($enabledListeners -notmatch "com\.ssm\.app") {
    Fail "NOTIFICATION_LISTENER_NOT_ENABLED"
}

Write-Host "Running on-device regression tests..."
$regressionClasses = "com.ssm.app.TransactionFlowInstrumentedTest,com.ssm.app.MainActivityUiTest"
Invoke-Instrumentation -Serial $serial -ClassFilter $regressionClasses -EvidenceFile "$EvidenceDir\instrumentation-regression.txt" | Out-Null

Write-Host "Preparing notification-listener end-to-end probe..."
Run-Adb -s $serial shell am force-stop com.ssm.app
& adb -s $serial shell run-as com.ssm.app rm -f databases/ssm.db databases/ssm.db-shm databases/ssm.db-wal
if ($LASTEXITCODE -ne 0) { Fail "DB_RESET_FAILED" }
Run-Adb -s $serial shell monkey -p com.ssm.app -c android.intent.category.LAUNCHER 1
Start-Sleep -Seconds 3
Run-Adb -s $serial shell cmd notification allow_listener com.ssm.app/.PaymentNotificationListener
Start-Sleep -Seconds 2

Run-Adb -s $serial shell cmd notification post -S bigtext -t "현대카드" "ssm-e2e" "12,300원 일시불 스타벅스 승인"
Start-Sleep -Seconds 5
Invoke-Instrumentation -Serial $serial -ClassFilter "com.ssm.app.RealDeviceProbeInstrumentedTest#notificationProbeStoredExpectedTransaction" -EvidenceFile "$EvidenceDir\notification-validation.txt" | Out-Null

Write-Host "Checking transaction_id duplicate protection..."
Run-Adb -s $serial shell cmd notification post -S bigtext -t "현대카드" "ssm-e2e" "12,300원 일시불 스타벅스 승인"
Start-Sleep -Seconds 4
Invoke-Instrumentation -Serial $serial -ClassFilter "com.ssm.app.RealDeviceProbeInstrumentedTest#duplicateGuardStillSingle" -EvidenceFile "$EvidenceDir\duplicate-validation.txt" | Out-Null

Write-Host "Triggering actual Calendar/Notion sync job..."
& adb -s $serial shell cmd jobscheduler run -f com.ssm.app 22002 | Tee-Object -FilePath "$EvidenceDir\jobscheduler.txt"
if ($LASTEXITCODE -ne 0) { Fail "SYNC_JOB_TRIGGER_FAILED" }

$externalVerified = $false
for ($attempt = 1; $attempt -le 6; $attempt++) {
    Start-Sleep -Seconds 10
    $probeFile = "$EvidenceDir\external-sync-validation-$attempt.txt"
    $externalVerified = Invoke-Instrumentation -Serial $serial -ClassFilter "com.ssm.app.RealDeviceProbeInstrumentedTest#externalSyncTargetsAreSyncedWithRemoteIds" -EvidenceFile $probeFile -AllowFailure
    if ($externalVerified) { break }
    if ($attempt -lt 6) {
        & adb -s $serial shell cmd jobscheduler run -f com.ssm.app 22002 | Out-Null
    }
}
if (-not $externalVerified) {
    Fail "EXTERNAL_SYNC_NOT_VERIFIED: Google Calendar, Samsung Calendar and Notion must all be synced with remote IDs. Missing account/permission/Notion token remains BLOCKED, never PASS."
}

Run-Adb -s $serial shell screencap -p /sdcard/ssm-final.png
& adb -s $serial pull /sdcard/ssm-final.png "$EvidenceDir\galaxy-final.png" | Out-Null
if ($LASTEXITCODE -ne 0) { Fail "SCREENSHOT_PULL_FAILED" }
& adb -s $serial shell rm /sdcard/ssm-final.png | Out-Null

@(
    "GALAXY_REAL_DEVICE_QA=PASS"
    "device=$manufacturer $model"
    "android=$android"
    "instrumentation_regression=PASS"
    "notification_listener_e2e=PASS"
    "duplicate_guard=PASS"
    "calendar_google=PASS"
    "calendar_samsung=PASS"
    "notion=PASS"
) | Out-File -Encoding utf8 "$EvidenceDir\qa-summary.txt"

Write-Host "GALAXY REAL DEVICE QA PASS"
