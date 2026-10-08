#!/usr/bin/env bash
# Download the schedule of the current edition into an assets directory.
#
# Usage: fetch-schedule.sh [ASSETS_DIR]
# ASSETS_DIR defaults to videos/assets, where the uploader looks when started without
# --camaaloth-uploader.assetsDir: <recordingDir>/assets. For a recording directory on an external
# drive, pass it: fetch-schedule.sh /Volumes/BrzhCampZ1/2026/assets
set -euo pipefail

PROJECT_ROOT="$(cd -P "$( dirname "${BASH_SOURCE[0]}" )" && pwd)"
cd "$PROJECT_ROOT"

assets="${1:-videos/assets}"
mkdir -p "${assets}"

curl -fsSL -o "${assets}/schedule.json" https://raw.githubusercontent.com/breizhcamp/website/refs/heads/main/src/routes/programme/data/schedule.json
echo "Schedule written to ${assets}/schedule.json"
