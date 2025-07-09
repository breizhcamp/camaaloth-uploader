#!/usr/bin/env bash

PROJECT_ROOT="$(cd -P "$( dirname "${BASH_SOURCE[0]}" )" && pwd)"
cd "$PROJECT_ROOT"

curl -o assets/schedule.json https://raw.githubusercontent.com/breizhcamp/website/refs/heads/production/static/json/schedule.json
