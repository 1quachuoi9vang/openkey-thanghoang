#!/usr/bin/env bash
# OpenKey Android — Antigravity Remote Linux VM Bootstrap Script
# Configures rtk, gh CLI, and Java PKCS12 SSL proxy truststore so Gradle runs out-of-the-box.
set -euo pipefail

export PATH="$HOME/.local/bin:$PATH"
mkdir -p "$HOME/.local/bin"

# 1. Bootstrap static rtk binary if missing
if ! command -v rtk >/dev/null 2>&1; then
  echo "[setup] Installing rtk to $HOME/.local/bin/rtk..."
  curl -fsSL https://raw.githubusercontent.com/rtk-ai/rtk/refs/heads/master/install.sh | sh
fi

# 2. Ensure GitHub CLI (gh) binary is unpacked in $HOME/.local/bin/gh for /opt/antigravity/bin/gh
if [ ! -x "$HOME/.local/bin/gh" ]; then
  GH_VERSION="2.103.3"
  echo "[setup] Installing gh v${GH_VERSION} to $HOME/.local/bin/gh..."
  curl -fsSL "https://github.com/cli/cli/releases/download/v${GH_VERSION}/gh_${GH_VERSION}_linux_amd64.tar.gz" -o /tmp/gh.tar.gz
  tar -xzmf /tmp/gh.tar.gz -C /tmp
  cp "/tmp/gh_${GH_VERSION}_linux_amd64/bin/gh" "$HOME/.local/bin/gh"
  chmod +x "$HOME/.local/bin/gh"
  rm -rf /tmp/gh.tar.gz "/tmp/gh_${GH_VERSION}_linux_amd64"
fi

# 3. Generate PKCS12 truststore from /etc/ssl/certs/ca-certificates.crt if running under agy-plugin-proxy
TRUSTSTORE_PATH="/tmp/agy-cacerts.p12"
if [ -f "/etc/ssl/certs/ca-certificates.crt" ] && [ ! -f "$TRUSTSTORE_PATH" ]; then
  echo "[setup] Generating PKCS12 truststore at $TRUSTSTORE_PATH..."
  keytool -importcert -noprompt -trustcacerts \
    -alias agy-proxy-ca \
    -file /etc/ssl/certs/ca-certificates.crt \
    -keystore "$TRUSTSTORE_PATH" \
    -storetype PKCS12 \
    -storepass changeit
fi

if [ -n "${HTTPS_PROXY:-}" ] && [ -f "$TRUSTSTORE_PATH" ]; then
  PROXY_HOST=$(echo "$HTTPS_PROXY" | sed -E 's|https?://([^:]+):([0-9]+).*|\1|')
  PROXY_PORT=$(echo "$HTTPS_PROXY" | sed -E 's|https?://([^:]+):([0-9]+).*|\2|')
  export JAVA_TOOL_OPTIONS="-Djavax.net.ssl.trustStore=$TRUSTSTORE_PATH -Djavax.net.ssl.trustStorePassword=changeit -Dhttp.proxyHost=$PROXY_HOST -Dhttp.proxyPort=$PROXY_PORT -Dhttps.proxyHost=$PROXY_HOST -Dhttps.proxyPort=$PROXY_PORT"
fi

echo "[OK] Remote VM environment ready (rtk: $(rtk --version))."
