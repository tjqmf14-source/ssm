#!/usr/bin/env bash
set -euo pipefail

mkdir -p ui-qa
adb logcat -c
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm clear com.ssm.app
adb shell am start -W -n com.ssm.app/.MainActivity | tee ui-qa/start.txt
sleep 2

dump_ui() {
  adb shell uiautomator dump /sdcard/window.xml >/dev/null
  adb pull /sdcard/window.xml ui-qa/window.xml >/dev/null
}

assert_text() {
  dump_ui
  python3 - "$1" <<'PY'
import sys
import xml.etree.ElementTree as ET
needle = sys.argv[1]
values = [n.attrib.get("text", "") for n in ET.parse("ui-qa/window.xml").iter("node")]
if needle not in values:
    print("Missing UI text:", needle)
    print("Visible texts:", [v for v in values if v])
    raise SystemExit(1)
PY
}

tap_text() {
  dump_ui
  local coords
  coords="$(python3 - "$1" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET
needle = sys.argv[1]
for node in ET.parse("ui-qa/window.xml").iter("node"):
    if node.attrib.get("text", "") == needle:
        match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
        if match:
            x = (int(match.group(1)) + int(match.group(3))) // 2
            y = (int(match.group(2)) + int(match.group(4))) // 2
            print(x, y)
            raise SystemExit(0)
print("Unable to find tappable text:", needle, file=sys.stderr)
raise SystemExit(1)
PY
)"
  local x y
  read -r x y <<< "$coords"
  adb shell input tap "$x" "$y"
  sleep 1
}

assert_text "씀"
assert_text "이번 달 총지출"
adb exec-out screencap -p > ui-qa/home.png

tap_text "내역"
assert_text "내역"
adb exec-out screencap -p > ui-qa/transactions.png

tap_text "분석"
assert_text "분석"
assert_text "카테고리"
adb exec-out screencap -p > ui-qa/analysis.png

tap_text "설정"
assert_text "설정"
assert_text "Notion"
adb exec-out screencap -p > ui-qa/settings.png

tap_text "홈"
assert_text "이번 달 총지출"
tap_text "+ 거래 추가"
assert_text "거래 추가"
assert_text "카테고리"
assert_text "결제수단"
adb exec-out screencap -p > ui-qa/add-dialog.png

tap_text "취소"
assert_text "이번 달 총지출"

adb shell dumpsys activity activities > ui-qa/activity.txt
grep -q "com.ssm.app/.MainActivity" ui-qa/activity.txt

adb logcat -d > ui-qa/logcat.txt
if grep -qE "FATAL EXCEPTION.*com\.ssm\.app|Process: com\.ssm\.app.*FATAL" ui-qa/logcat.txt; then
  echo "Fatal app exception detected"
  exit 1
fi

cp ui-qa/window.xml ui-qa/final-window.xml
echo "UI_SMOKE_PASS" > ui-qa/result.txt
