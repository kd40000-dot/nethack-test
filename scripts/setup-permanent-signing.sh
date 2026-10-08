#!/usr/bin/env bash
# Run on YOUR OWN Termux device/computer, NEVER inside a public CI log.
# Creates a long-lived personal APK signing key, stores an offline backup in
# your home directory, and uploads encrypted GitHub Actions repository secrets.
# Requires GitHub CLI authenticated as repository owner and a JDK keytool.
set -euo pipefail
umask 077

REPO="kd40000-dot/nethack-test"
KEYDIR="$HOME/.local/share/nethack-guide-signing"
KEYFILE="$KEYDIR/nethack-guide-release.p12"
ALIAS="nethackguide"
for executable in gh keytool base64; do
  command -v "$executable" >/dev/null || {
    echo "Missing '$executable'. In Termux: pkg install gh openjdk-21 coreutils"
    exit 1
  }
done
gh auth status >/dev/null || {
  echo "First run: gh auth login"
  exit 1
}
mkdir -p "$KEYDIR"
if [ -e "$KEYFILE" ]; then
  echo "Key already exists at $KEYFILE; refusing to replace your signing identity."
  echo "If Actions secrets need restoring, configure them from this keystore."
  exit 1
fi

echo "Creating your PERSONAL signing key. Keep an offline backup of the .p12 file"
echo "and its password; a lost key means future APKs cannot update one another."
read -r -s -p "Choose a strong keystore password (8+ characters): " PASSWORD
printf '\n'
if [ "$(printf '%s' "$PASSWORD" | wc -c)" -lt 8 ]; then
  echo "Password is too short."
  exit 1
fi
read -r -s -p "Repeat keystore password: " VERIFY_PASSWORD
printf '\n'
[ "$PASSWORD" = "$VERIFY_PASSWORD" ] || { echo "Passwords did not match."; exit 1; }
unset VERIFY_PASSWORD

keytool -genkeypair -noprompt \
  -alias "$ALIAS" -keyalg RSA -keysize 3072 -validity 10000 \
  -keystore "$KEYFILE" -storetype PKCS12 \
  -storepass "$PASSWORD" -keypass "$PASSWORD" \
  -dname "CN=NetHack Guide Personal Signing, OU=Android, O=Personal, C=ES"

echo "Setting encrypted Actions secrets for $REPO..."
base64 -w 0 "$KEYFILE" | gh secret set NETHACK_KEYSTORE_BASE64 --repo "$REPO"
printf '%s' "$PASSWORD" | gh secret set NETHACK_KEYSTORE_PASSWORD --repo "$REPO"
printf '%s' "$ALIAS" | gh secret set NETHACK_KEY_ALIAS --repo "$REPO"
printf '%s' "$PASSWORD" | gh secret set NETHACK_KEY_PASSWORD --repo "$REPO"
unset PASSWORD

echo "Signing key saved ONLY at $KEYFILE on this device and as encrypted GitHub Actions secrets."
echo "Make an offline backup of $KEYFILE plus its password. Never commit the .p12 file."
echo "Triggering a permanently signed build..."
gh workflow run build.yml --repo "$REPO"
echo "Build: https://github.com/$REPO/actions"
