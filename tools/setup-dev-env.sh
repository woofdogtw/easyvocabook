#!/bin/bash
# Provision a fresh Ubuntu/WSL2 machine to build & test EasyVocaBook:
#   - Android (Kotlin) toolchain: JDK 25 + Android SDK (cmdline-tools, platform,
#     build-tools, platform-tools/adb)
#   - Rust (iced) toolchain: rustup stable + iced system libraries
#   - Helper tools: p7zip (backup.sh), gh, python3
#
# Idempotent: safe to re-run. Bootstrap after a WSL2 reinstall with:
#   git clone <repo> && ./easyvocabook/tools/setup-dev-env.sh
#
# Toggle optional pieces with env vars, e.g.:
#   INSTALL_EMULATOR=1 INSTALL_NODE=1 ./tools/setup-dev-env.sh

set -euo pipefail

# ---- versions / paths (match the CI in .github/workflows/build-test.yaml) ----
JDK_PKG="openjdk-25-jdk"
ANDROID_PLATFORM="platforms;android-37.0"
ANDROID_BUILD_TOOLS="build-tools;36.0.0"
ANDROID_EMU_IMAGE="system-images;android-36;default;x86_64"
SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
# Google's command-line tools bootstrap zip (self-updates via sdkmanager after).
CMDLINE_TOOLS_VER="13114758"
CMDLINE_TOOLS_ZIP="commandlinetools-linux-${CMDLINE_TOOLS_VER}_latest.zip"

# ---- optional features (0 = skip, 1 = install) ----
INSTALL_EMULATOR="${INSTALL_EMULATOR:-0}"       # WSL2 has no KVM; emulator cannot run there
INSTALL_RUST_COVERAGE="${INSTALL_RUST_COVERAGE:-1}"  # nightly + llvm-tools + cargo-llvm-cov
INSTALL_NODE="${INSTALL_NODE:-0}"               # only needed by the other ~/ai projects

log() { printf '\n\033[1;34m==> %s\033[0m\n' "$*"; }

# --------------------------------------------------------------------------
log "APT: base packages, JDK, iced system libs, helper tools"
sudo apt-get update
sudo apt-get install -y \
  "$JDK_PKG" \
  curl unzip ca-certificates \
  p7zip-full gh python3 \
  build-essential pkg-config \
  libwayland-dev libxkbcommon-dev libx11-dev libxrandr-dev \
  libxcursor-dev libxi-dev libgl1-mesa-dev libdbus-1-dev

# --------------------------------------------------------------------------
log "Android SDK at $SDK_ROOT"
mkdir -p "$SDK_ROOT/cmdline-tools"
if [ ! -x "$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" ]; then
  log "  downloading command-line tools ($CMDLINE_TOOLS_ZIP)"
  tmp="$(mktemp -d)"
  curl -fL "https://dl.google.com/android/repository/${CMDLINE_TOOLS_ZIP}" -o "$tmp/cmdline.zip"
  unzip -q "$tmp/cmdline.zip" -d "$tmp"
  rm -rf "$SDK_ROOT/cmdline-tools/latest"
  mv "$tmp/cmdline-tools" "$SDK_ROOT/cmdline-tools/latest"
  rm -rf "$tmp"
fi

SDKMANAGER="$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
yes | "$SDKMANAGER" --licenses >/dev/null
PKGS=("platform-tools" "$ANDROID_PLATFORM" "$ANDROID_BUILD_TOOLS")
if [ "$INSTALL_EMULATOR" = "1" ]; then
  PKGS+=("emulator" "$ANDROID_EMU_IMAGE")
fi
log "  installing: ${PKGS[*]}"
yes | "$SDKMANAGER" "${PKGS[@]}"

# --------------------------------------------------------------------------
log "Rust toolchain"
if ! command -v rustc >/dev/null 2>&1; then
  curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y
fi
# shellcheck disable=SC1091
source "$HOME/.cargo/env"
rustup default stable
if [ "$INSTALL_RUST_COVERAGE" = "1" ]; then
  rustup toolchain install nightly --component llvm-tools-preview
  command -v cargo-llvm-cov >/dev/null 2>&1 || cargo install cargo-llvm-cov
fi

# --------------------------------------------------------------------------
if [ "$INSTALL_NODE" = "1" ]; then
  log "Node.js 24 (NodeSource)"
  if ! command -v node >/dev/null 2>&1; then
    curl -fsSL https://deb.nodesource.com/setup_24.x | sudo -E bash -
    sudo apt-get install -y nodejs
  fi
fi

# --------------------------------------------------------------------------
log "Shell environment (~/.bashrc)"
BRC="$HOME/.bashrc"
add_line() { grep -qxF "$1" "$BRC" 2>/dev/null || echo "$1" >>"$BRC"; }
add_line "export ANDROID_SDK_ROOT=$SDK_ROOT"
add_line "export ANDROID_HOME=$SDK_ROOT"
add_line 'export PATH=$PATH:$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin'
add_line '. "$HOME/.cargo/env"'

# --------------------------------------------------------------------------
log "Done. Notes:"
cat <<EOF
  * Open a new shell (or 'source ~/.bashrc') to pick up ANDROID_SDK_ROOT/PATH.
  * In the kotlin/ project, create local.properties with:
        sdk.dir=$SDK_ROOT
    (it is gitignored and machine-specific)
  * Gradle itself needs no install — the project ships ./gradlew.
  * Emulator was ${INSTALL_EMULATOR/1/installed}${INSTALL_EMULATOR/0/skipped}; WSL2 lacks KVM
    so instrumented tests still run only in CI, not locally.
  * Verify:  java -version ; adb --version ; sdkmanager --list_installed ; rustc --version
EOF
