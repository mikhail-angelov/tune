#!/usr/bin/env python3
"""Build a uniquely versioned release APK signed with a local test key."""

import os
import pathlib
import re
import shutil
import subprocess
import tempfile


root = pathlib.Path(__file__).resolve().parents[1]
version_file = root / "version.properties"
match = re.fullmatch(r"versionCode=(\d+)\s*", version_file.read_text())
if match is None:
    raise SystemExit("Expected versionCode=<number> in version.properties")

code = int(match.group(1)) + 1
output_dir = root / "app/build/outputs/apk/release"
while (output_dir / f"Tune-v1.0.{code - 1}-test.apk").exists():
    code += 1
name = f"1.0.{code - 1}"
output = output_dir / f"Tune-v{name}-test.apk"

env = os.environ.copy()
for secret in ("TUNE_TEST_STORE_PASSWORD", "TUNE_TEST_KEY_PASSWORD"):
    if not env.get(secret):
        raise SystemExit(f"Set {secret} before building a test APK")
env["TUNE_VERSION_CODE"] = str(code)
env["TUNE_VERSION_NAME"] = name

sdk_root = pathlib.Path(env.get("ANDROID_HOME") or env.get("ANDROID_SDK_ROOT") or pathlib.Path.home() / "Library/Android/sdk")
tools = sdk_root / "build-tools/35.0.0"
key = pathlib.Path(env.get("TUNE_TEST_KEYSTORE") or pathlib.Path.home() / ".android/debug.keystore")
alias = env.get("TUNE_TEST_KEY_ALIAS", "androiddebugkey")
if not key.is_file():
    raise SystemExit(f"Test keystore not found: {key}")

subprocess.run([str(root / "gradlew"), "--no-daemon", ":app:assembleRelease"], cwd=root, env=env, check=True)
unsigned = output_dir / "app-release-unsigned.apk"
with tempfile.TemporaryDirectory(prefix="tune-test-apk-") as temp:
    aligned = pathlib.Path(temp) / "aligned.apk"
    signed = pathlib.Path(temp) / "signed.apk"
    subprocess.run([str(tools / "zipalign"), "-P", "16", "-f", "4", str(unsigned), str(aligned)], check=True)
    env["ANDROID_SIGNING_STORE_PASSWORD"] = env["TUNE_TEST_STORE_PASSWORD"]
    env["ANDROID_SIGNING_KEY_PASSWORD"] = env["TUNE_TEST_KEY_PASSWORD"]
    subprocess.run([
        str(tools / "apksigner"), "sign", "--ks", str(key), "--ks-key-alias", alias,
        "--ks-pass", "env:ANDROID_SIGNING_STORE_PASSWORD", "--key-pass", "env:ANDROID_SIGNING_KEY_PASSWORD",
        "--out", str(signed), str(aligned),
    ], env=env, check=True)
    subprocess.run([str(tools / "apksigner"), "verify", str(signed)], check=True)
    badging = subprocess.check_output([str(tools / "aapt2"), "dump", "badging", str(signed)], text=True)
    if f"versionCode='{code}'" not in badging or f"versionName='{name}'" not in badging:
        raise SystemExit(f"Signed APK does not contain versionCode={code}, versionName={name}")
    shutil.copy2(signed, output)

version_file.write_text(f"versionCode={code}\n")
print(f"Created {output} (versionCode={code}, versionName={name})")
