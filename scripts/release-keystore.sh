#!/bin/sh
# Create the Android release signing key, once per app — never again.
# Obtainium and Android itself pin the signature: a re-keyed APK is a different
# app to every device that already has this one, and the only fix is uninstall.
# So the key lives outside the repo and the file below is the backup that matters.
#
#   scripts/release-keystore.sh            # ~/.spross/release.jks
#   SPROSS_KEY_DIR=/path scripts/release-keystore.sh
#
# Prints nothing secret. The values to paste into the repo's Actions secrets are
# written to <dir>/github-secrets.txt, readable by you alone.
set -eu

KEYDIR="${SPROSS_KEY_DIR:-$HOME/.spross}"
KS="$KEYDIR/release.jks"
SECRETS="$KEYDIR/github-secrets.txt"
ALIAS=spross

if [ -f "$KS" ]; then
  echo "error: $KS already exists — this is the one key, not a new one" >&2
  exit 1
fi

if [ -z "${JAVA_HOME:-}" ] && [ -x /usr/libexec/java_home ]; then
  JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home)"
fi
KEYTOOL="${JAVA_HOME:+$JAVA_HOME/bin/}keytool"

mkdir -p "$KEYDIR"
chmod 700 "$KEYDIR"

PW=$(LC_ALL=C tr -dc 'A-Za-z0-9' < /dev/urandom | head -c 40)

# 30 years: the key outlives the app or the app outlives its installs.
"$KEYTOOL" -genkeypair \
  -keystore "$KS" -storetype PKCS12 \
  -storepass "$PW" -keypass "$PW" \
  -alias "$ALIAS" -keyalg RSA -keysize 4096 -validity 10950 \
  -dname "CN=Spross, O=polygon GmbH, C=DE" >/dev/null
chmod 600 "$KS"

{
  echo "Repository secrets for the release workflow."
  echo "Settings > Secrets and variables > Actions > New repository secret."
  echo
  echo "SPROSS_KEYSTORE_PASSWORD"
  echo "$PW"
  echo
  echo "SPROSS_KEY_ALIAS"
  echo "$ALIAS"
  echo
  echo "SPROSS_KEYSTORE_BASE64"
  base64 < "$KS" | tr -d '\n'
  echo
} > "$SECRETS"
chmod 600 "$SECRETS"

echo "key:     $KS"
echo "secrets: $SECRETS"
echo
echo "Back up both somewhere off this machine, then paste the three values into"
echo "the repo's Actions secrets. Losing the key ends updates for every install."
