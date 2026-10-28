#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

python3 - <<'PY'
import json, re, pathlib
gradle = pathlib.Path("app/build.gradle.kts").read_text()
version_code = int(re.search(r"versionCode\s*=\s*(\d+)", gradle).group(1))
version_name = re.search(r'versionName\s*=\s*"([^"]+)"', gradle).group(1)
meta = json.loads(pathlib.Path("release/version.json").read_text())
if meta["versionCode"] != version_code or meta["versionName"] != version_name:
    raise SystemExit(
        f"release/version.json ({meta['versionName']}/{meta['versionCode']}) "
        f"与 app/build.gradle.kts ({version_name}/{version_code}) 不一致"
    )
print(f"version {version_name} ({version_code})")
PY

if [[ -z "${ANDROID_HOME:-}" && -z "${ANDROID_SDK_ROOT:-}" ]]; then
  echo "请先设置 ANDROID_HOME，并在 local.properties 里写 sdk.dir" >&2
  exit 1
fi

./gradlew :app:assembleRelease
cp -f app/build/outputs/apk/release/app-release.apk release/diary.apk
echo "已写入 release/diary.apk"

if [[ -n "${ANDROID_HOME:-}" ]]; then
  apksigner="$(find "$ANDROID_HOME/build-tools" -name apksigner -type f | sort | tail -1 || true)"
  if [[ -n "$apksigner" ]]; then
    "$apksigner" verify --verbose release/diary.apk
  fi
fi
