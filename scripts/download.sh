#!/usr/bin/env sh
# This script is used in the README and https://www.infracost.io/docs/#quick-start
set -e

# check_sha is separated into a defined function so that we can
# capture the exit code effectively with `set -e` enabled
check_sha() {
  (
    cd /tmp/
    shasum -sc "$1"
  )
  return $?
}

download_binary() {
  bin_target=$1
  resource_path=$2

  if [ -z "$bin_target" ]; then
    echo "Please provide the target binary to download"
    exit 1
  fi

  if [ -z "$resource_path" ]; then
    echo "Please provide the resource path to download the binary to"
    exit 1
  fi

  url="https://infracost.io/downloads/latest"
  tar="infracost-$bin_target.tar.gz"
  echo "Downloading latest release of infracost-$bin_target..."
  curl -sL "$url/$tar" -o "/tmp/$tar"
  echo

  code=$(curl -s -L -o /dev/null -w "%{http_code}" "$url/$tar.sha256")
  if [ "$code" = "404" ]; then
    echo "Skipping checksum validation as the sha for the release could not be found, no action needed."
  else
    echo "Validating checksum for infracost-$bin_target..."
    curl -sL "$url/$tar.sha256" -o "/tmp/$tar.sha256"

    if ! check_sha "$tar.sha256"; then
      exit 1
    fi

    rm "/tmp/$tar.sha256"
  fi
  echo

  tar xzf "/tmp/$tar" -C /tmp
  rm "/tmp/$tar"

  # shellcheck disable=SC2046
  mkdir -p $(dirname "${resource_path}")

  if echo "$bin_target" | grep "windows-arm"; then
    mv "/tmp/infracost-arm64.exe" "${resource_path}"
  elif echo "$bin_target" | grep "windows"; then
    mv "/tmp/infracost.exe" "${resource_path}"
  else
    mv "/tmp/infracost-$bin_target" "${resource_path}"
  fi
}

download_binary "darwin-amd64" "src/main/resources/binaries/macos/amd64/infracost"
download_binary "darwin-arm64" "src/main/resources/binaries/macos/aarch64/infracost"
download_binary "linux-amd64" "src/main/resources/binaries/linux/amd64/infracost"
download_binary "linux-arm64" "src/main/resources/binaries/linux/aarch64/infracost"
download_binary "windows-arm64" "src/main/resources/binaries/windows/aarch64/infracost.exe"
download_binary "windows-amd64" "src/main/resources/binaries/windows/amd64/infracost.exe"
