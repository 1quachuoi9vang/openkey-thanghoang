#!/usr/bin/env bash
# OpenKey Android — Antigravity Remote Linux VM Bootstrap Script
# Configures rtk, gh CLI (if absent), and Java PKCS12 SSL proxy truststore so Gradle runs out-of-the-box.
# Safe to run directly or via `source scripts/setup_remote_vm.sh` without polluting caller shell options.

_openkey_setup_remote_vm() {
  local prev_opts
  prev_opts="$(set +o)"
  set -euo pipefail

  export PATH="$HOME/.local/bin:$PATH"
  mkdir -p "$HOME/.local/bin"

  # 1. Bootstrap static rtk binary if missing
  if ! command -v rtk >/dev/null 2>&1; then
    echo "[setup] Installing rtk to $HOME/.local/bin/rtk..."
    curl -fsSL https://raw.githubusercontent.com/rtk-ai/rtk/refs/heads/master/install.sh | sh
  fi

  # 2. Only install standalone GitHub CLI (gh) if no gh binary (e.g. /opt/antigravity/bin/gh) is already on PATH
  if ! command -v gh >/dev/null 2>&1; then
    local gh_version="2.83.2"
    echo "[setup] Installing gh v${gh_version} to $HOME/.local/bin/gh..."
    curl -fsSL "https://github.com/cli/cli/releases/download/v${gh_version}/gh_${gh_version}_linux_amd64.tar.gz" -o /tmp/gh.tar.gz
    tar -xzmf /tmp/gh.tar.gz -C /tmp
    cp "/tmp/gh_${gh_version}_linux_amd64/bin/gh" "$HOME/.local/bin/gh"
    chmod +x "$HOME/.local/bin/gh"
    rm -rf /tmp/gh.tar.gz "/tmp/gh_${gh_version}_linux_amd64"
  fi

  # 3. Generate PKCS12 truststore from /etc/ssl/certs/ca-certificates.crt if running under agy-plugin-proxy
  local truststore_path="/tmp/agy-cacerts.p12"
  if [ -f "/etc/ssl/certs/ca-certificates.crt" ] && [ ! -f "$truststore_path" ]; then
    echo "[setup] Generating PKCS12 truststore at $truststore_path..."
    keytool -importcert -noprompt -trustcacerts \
      -alias agy-proxy-ca \
      -file /etc/ssl/certs/ca-certificates.crt \
      -keystore "$truststore_path" \
      -storetype PKCS12 \
      -storepass changeit
  fi

  if [ -n "${HTTPS_PROXY:-}" ] && [ -f "$truststore_path" ]; then
    local proxy_host proxy_port
    proxy_host=$(echo "$HTTPS_PROXY" | sed -E 's|https?://([^:]+):([0-9]+).*|\1|')
    proxy_port=$(echo "$HTTPS_PROXY" | sed -E 's|https?://([^:]+):([0-9]+).*|\2|')
    export JAVA_TOOL_OPTIONS="-Djavax.net.ssl.trustStore=$truststore_path -Djavax.net.ssl.trustStorePassword=changeit -Dhttp.proxyHost=$proxy_host -Dhttp.proxyPort=$proxy_port -Dhttps.proxyHost=$proxy_host -Dhttps.proxyPort=$proxy_port"
  fi

  echo "[OK] Remote VM environment ready (rtk: $(rtk --version))."
  eval "$prev_opts"
}

_openkey_setup_remote_vm
unset -f _openkey_setup_remote_vm
