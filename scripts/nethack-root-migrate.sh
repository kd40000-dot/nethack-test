#!/system/bin/sh
# Root-only, local migration of NetHack 5 Guide between DIFFERENT APK signing keys.
# Read this file before running it. No remote commands or uploads.
#
# In NetHack first use the in-game save/quit feature, then:
#   su -c 'sh /sdcard/Download/nethack-root-migrate.sh backup'
# Download the NEW APK, then:
#   su -c 'sh /sdcard/Download/nethack-root-migrate.sh restore /sdcard/Download/NEW.apk'
#
# Runs only on Android with root, and only for com.tbd.nethack5.guide.
# The old APK and private app data are retained for emergency recovery.

set -e
PKG="com.tbd.nethack5.guide"
DATA="/data/user/0/$PKG"
BACKUP="/sdcard/Download/NetHackGuide-migration"
TAR="$BACKUP/private-data.tar.gz"
OLDAPK="$BACKUP/original-signed.apk"

fail() { echo "ERROR: $*" >&2; exit 1; }
info() { echo "[NetHack] $*"; }
[ "$(id -u)" = 0 ] || fail "Run with root: su -c 'sh this-file backup'"
MODE="$1"
case "$MODE" in
  backup|restore) ;;
  *) fail "Usage: sh nethack-root-migrate.sh backup | restore /sdcard/Download/new.apk" ;;
esac

check_archive() {
  [ -s "$TAR" ] || fail "Data backup is missing: $TAR"
  tar -tzf "$TAR" >/dev/null || fail "Data archive integrity check failed"
}

restore_data() {
  [ -d "$DATA" ] || fail "Android did not create app data directory: $DATA"
  NEW_OWNER="$(stat -c '%u:%g' "$DATA")"
  info "Restoring game files and settings..."
  tar -xzf "$TAR" -C "$DATA" || fail "Extract failed. Keep backup and seek recovery help."
  chown -R "$NEW_OWNER" "$DATA"
  restorecon -RF "$DATA" >/dev/null 2>&1 || info "Warning: restorecon reported a problem."
  info "Restored files with package directory owner $NEW_OWNER"
}

if [ "$MODE" = "backup" ]; then
  [ -d "$DATA" ] || fail "Original app's data not found; do not uninstall anything."
  APK_LINE="$(pm path "$PKG" | sed -n 's/^package://p' | head -n 1)"
  [ -f "$APK_LINE" ] || fail "Could not locate existing signed APK"
  mkdir -p "$BACKUP"
  [ ! -e "$TAR" ] || fail "Existing backup found; refusing to overwrite $TAR"
  [ ! -e "$OLDAPK" ] || fail "Existing original APK found; refusing to overwrite $OLDAPK"
  info "Ensure your current NetHack run has been SAVED and QUIT in-game."
  info "Stopping the app to create a consistent file backup."
  am force-stop "$PKG"
  cp "$APK_LINE" "$OLDAPK"
  tar -czf "$TAR.tmp" -C "$DATA" . || fail "Backup failed. Old app is intact."
  mv "$TAR.tmp" "$TAR"
  check_archive
  sha256sum "$TAR" "$OLDAPK" > "$BACKUP/SHA256SUMS.txt"
  info "Backup verified. Old app has NOT been uninstalled."
  info "Private files: $TAR"
  info "Original signed APK (for rollback): $OLDAPK"
  exit 0
fi

NEWAPK="$2"
[ -f "$NEWAPK" ] || fail "Specify the exact path to the downloaded NEW APK"
[ -s "$OLDAPK" ] || fail "Original signed APK is missing; backup first."
check_archive
[ -d "$DATA" ] || fail "Existing app data missing. Do not continue."
info "Valid backup found."
info "A signing-key change REQUIRES uninstalling this exact package first."
info "The original signed APK and verified private-data backup remain untouched."
am force-stop "$PKG"

info "Removing old signature/package registration..."
pm uninstall "$PKG" || fail "Uninstall failed; original app may still be installed."
info "Installing replacement APK..."
if ! pm install -r -t "$NEWAPK"; then
  info "NEW APK install failed; attempting to put original signed APK back."
  pm install -r -t "$OLDAPK" || fail "Rollback APK failed! DO NOT delete backup."
  restore_data
  fail "New install unsuccessful. Original app restored, verify it opens."
fi
restore_data
info "Migration finished. Verify the game and settings BEFORE deleting ANY backup."
info "Preserved backup remains in $BACKUP"
