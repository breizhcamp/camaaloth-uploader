#!/usr/bin/env bash

test -d assets || mkdir assets

PROJECT_ROOT="$(cd -P "$( dirname "${BASH_SOURCE[0]}" )" && pwd)"
cd "$PROJECT_ROOT"

curl -o assets/schedule.json https://raw.githubusercontent.com/breizhcamp/website/refs/heads/main/src/routes/programme/data/schedule.json
