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

function Pull-AppDb([string]$Destination) {
    $pythonCode = @'
import subprocess, sys
out = subprocess.check_output(["adb","exec-out","run-as","com.ssm.app","cat","databases/ssm.db"])
open(sys.argv[1], "wb").write(out)
'@
    python -c $pythonCode $Destination
    if ($LASTEXITCODE -ne 0 -or !(Test-Path $Destination)) {
        Fail "Could not copy app database from Galaxy."
    }
}

function Query-Db([string]$DbPath, [string]$Sql) {
    $pythonCode = @'
import sqlite3, sys, json
db, sql = sys.argv[1], sys.argv[2]
con = sqlite3.connect(db)
rows = con.execute(sql).fetchall()
print(json.dumps(rows, ensure_ascii=False))
'@
    $output = python -c $pythonCode $DbPath $Sql
    if ($LASTEXITCODE -ne 0) { Fail "SQLite query failed: $Sql" }
    return $output
}

New-Item -ItemType Directory -Force -Path $EvidenceDir | Out-Null

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) { Fail "ADB_NOT_FOUND" }
if (-not (Get-Command python -ErrorAction SilentlyContinue)) { Fail "PYTHON_NOT_FOUND" }
if (-not (Get-Command gradle -ErrorAction SilentlyContinue)) { Fail "GRADLE_NOT_FOUND" }

Run-Adb start-server
$deviceLines = @((& adb devices) | Select-Object -Skip 1 | Where-Object { $_ -match "\S+\s+device$" })
if ($deviceLines.Count -ne 1) {
    (& adb devices -l) | Out-File -Encoding utf8 "$EvidenceDir\adb-devices.txt"
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
    Fail "APP_INSTALL_FAILED. Existing com.ssm.app may be signed with a different key; do not uninstall automatically because that can erase user data."
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

Write-Host "Running on-device instrumentation regression tests..."
$instrumentation = & adb -s $serial shell am instrument -w -r com.ssm.app.test/androidx.test.runner.AndroidJUnitRunner 2>&1
$instrumentation | Tee-Object -FilePath "$EvidenceDir\instrumentation.txt"
$instrumentationText = $instrumentation -join [Environment]::NewLine
if ($LASTEXITCODE -ne 0 -or $instrumentationText -match "FAILURES!!!|Process crashed|INSTRUMENTATION_FAILED") {
    Fail "ON_DEVICE_INSTRUMENTATION_FAILED"
}
if ($instrumentationText -notmatch "OK \(") {
    Fail "ON_DEVICE_INSTRUMENTATION_ZERO_OR_UNKNOWN_TEST_RESULT"
}

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

$db1 = Join-Path $EvidenceDir "after-notification.db"
Pull-AppDb $db1
$txRows = Query-Db $db1 "SELECT transaction_id,merchant,amount,type,category,payment_method FROM transactions ORDER BY id"
$txRows | Out-File -Encoding utf8 "$EvidenceDir\notification-transaction.json"

$validateTransaction = @'
import sqlite3, sys
con = sqlite3.connect(sys.argv[1])
rows = con.execute("SELECT merchant,amount,type,category,payment_method FROM transactions").fetchall()
assert len(rows) == 1, f"expected 1 transaction, got {len(rows)}"
merchant, amount, typ, category, method = rows[0]
assert amount == 12300, amount
assert typ == "expense", typ
assert "스타벅스" in merchant, merchant
assert category == "카페", category
assert method == "카드", method
print("notification_e2e=PASS")
'@
python -c $validateTransaction $db1 | Tee-Object -FilePath "$EvidenceDir\notification-validation.txt"
if ($LASTEXITCODE -ne 0) { Fail "NOTIFICATION_E2E_FAILED" }

Write-Host "Checking transaction_id duplicate protection..."
Run-Adb -s $serial shell cmd notification post -S bigtext -t "현대카드" "ssm-e2e" "12,300원 일시불 스타벅스 승인"
Start-Sleep -Seconds 4
$db2 = Join-Path $EvidenceDir "after-duplicate.db"
Pull-AppDb $db2
$validateDuplicate = @'
import sqlite3, sys
con = sqlite3.connect(sys.argv[1])
count = con.execute("SELECT COUNT(*) FROM transactions").fetchone()[0]
assert count == 1, f"duplicate inserted: count={count}"
print("duplicate_guard=PASS")
'@
python -c $validateDuplicate $db2 | Tee-Object -FilePath "$EvidenceDir\duplicate-validation.txt"
if ($LASTEXITCODE -ne 0) { Fail "DUPLICATE_GUARD_FAILED" }

Write-Host "Triggering actual Calendar/Notion sync job..."
& adb -s $serial shell cmd jobscheduler run -f com.ssm.app 22002 | Tee-Object -FilePath "$EvidenceDir\jobscheduler.txt"
if ($LASTEXITCODE -ne 0) { Fail "SYNC_JOB_TRIGGER_FAILED" }
Start-Sleep -Seconds 15

$db3 = Join-Path $EvidenceDir "after-sync.db"
Pull-AppDb $db3
$syncRows = Query-Db $db3 "SELECT target,status,COALESCE(remote_id,''),attempts,COALESCE(last_error,'') FROM sync_targets ORDER BY target"
$syncRows | Out-File -Encoding utf8 "$EvidenceDir\sync-targets.json"

$validateSync = @'
import sqlite3, sys
con = sqlite3.connect(sys.argv[1])
rows = con.execute("SELECT target,status,remote_id,attempts,last_error FROM sync_targets").fetchall()
by = {r[0]: r[1:] for r in rows}
required = ("calendar_google","calendar_samsung","notion")
missing = [x for x in required if x not in by]
assert not missing, f"missing sync targets: {missing}"
bad = []
for target in required:
    status, remote_id, attempts, last_error = by[target]
    if status != "synced" or not remote_id:
        bad.append((target,status,remote_id,attempts,last_error))
assert not bad, "external sync not fully verified: " + repr(bad)
print("calendar_google=PASS")
print("calendar_samsung=PASS")
print("notion=PASS")
'@
python -c $validateSync $db3 | Tee-Object -FilePath "$EvidenceDir\external-sync-validation.txt"
if ($LASTEXITCODE -ne 0) {
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
    "instrumentation=PASS"
    "notification_listener_e2e=PASS"
    "duplicate_guard=PASS"
    "calendar_google=PASS"
    "calendar_samsung=PASS"
    "notion=PASS"
) | Out-File -Encoding utf8 "$EvidenceDir\qa-summary.txt"

Write-Host "GALAXY REAL DEVICE QA PASS"
