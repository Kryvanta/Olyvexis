#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"
repo=Kryvanta/Olyvexis
url=https://github.com/Kryvanta/Olyvexis.git
keystore=.release/olyvexis-release.jks
password_file=.release/signing-password

test -s "$keystore" && test -s "$password_file" || {
  echo 'Missing local release signing key or password.' >&2
  exit 1
}
gh auth status >/dev/null
gh repo view "$repo" --json name >/dev/null
gh auth setup-git

if git remote get-url origin >/dev/null 2>&1; then
  test "$(git remote get-url origin)" = "$url" || {
    echo 'origin points to another repository; stopped.' >&2
    exit 1
  }
else
  git remote add origin "$url"
fi

# Keep this signing identity for future updates. gh encrypts secrets before upload.
base64 < "$keystore" | tr -d '\n' | gh secret set KEYSTORE_BASE64 -R "$repo"
printf '%s' "$(cat "$password_file")" | gh secret set KEYSTORE_PASSWORD -R "$repo"
printf 'olyvexis' | gh secret set KEY_ALIAS -R "$repo"
printf '%s' "$(cat "$password_file")" | gh secret set KEY_PASSWORD -R "$repo"

git add -A
if ! git diff --cached --quiet; then
  git commit -m 'Initial Olyvexis 0.1.0 release'
fi
git branch -M main

remote_head=$(git ls-remote origin refs/heads/main | cut -f1)
if [ -n "$remote_head" ] && [ "$remote_head" != "$(git rev-parse HEAD)" ]; then
  echo 'Remote main differs from this local commit; stopped without overwriting it.' >&2
  exit 1
fi

gh repo edit "$repo" \
  --description 'Android desktop mode with movable windows on external displays or over Wi-Fi, powered by Shizuku.' \
  --add-topic 'android,android-desktop,desktop-mode,shizuku,virtual-display,external-display,wifi-streaming,kotlin,jetpack-compose'

git push -u origin main
if git rev-parse -q --verify refs/tags/v0.1.0 >/dev/null; then
  test "$(git rev-parse v0.1.0)" = "$(git rev-parse HEAD)" || {
    echo 'Local v0.1.0 tag points to a different commit; stopped.' >&2
    exit 1
  }
else
  git tag v0.1.0
fi
git push origin v0.1.0
echo 'Pushed main and v0.1.0. The release workflow will build and publish the signed APK.'
