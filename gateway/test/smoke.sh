#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PORT="${BCI_TEST_PORT:-18776}"
DB="$(mktemp)"
LOG="$(mktemp)"

export BCI_HOST=127.0.0.1
export BCI_PORT="$PORT"
export BCI_STREAM_ID=bciandroid-main
export BCI_EXPECTED_PUBLISHER_ID=bciwhisper-android
export BCI_PUBLISHER_TOKEN=test-publisher-token
export BCI_ADMIN_TOKEN=test-admin-token
export BCI_DB_PATH="$DB"
export BCI_PUBLIC_BASE_URL="http://127.0.0.1:$PORT"

cleanup() {
  if [[ -n "${PID:-}" ]]; then
    kill "$PID" 2>/dev/null || true
    wait "$PID" 2>/dev/null || true
  fi
  rm -f "$DB" "$LOG"
}
trap cleanup EXIT

bun run "$ROOT/src/server.ts" >"$LOG" 2>&1 &
PID=$!

for _ in $(seq 1 30); do
  if curl -fsS "http://127.0.0.1:$PORT/health" >/tmp/bciandroid-health.json; then
    break
  fi
  sleep 0.25
done

curl -fsS "http://127.0.0.1:$PORT/health" |
  grep -q '"ok":true'

curl -fsS "http://127.0.0.1:$PORT/api/v1/status" |
  grep -q '"id":"bciandroid-main"'

curl -fsS "http://127.0.0.1:$PORT/" |
  grep -q 'Live Transcription'

curl -fsS "http://127.0.0.1:$PORT/api/v1/transcript/bciandroid-main/history" |
  grep -q '"segments":\[\]'

bun run "$ROOT/test/publish-transcript.ts"

history="$(
  curl -fsS     "http://127.0.0.1:$PORT/api/v1/transcript/bciandroid-main/history"
)"
grep -q 'BCI live caption integration test' <<<"$history"
grep -q '"speaker":"Speaker 1"' <<<"$history"

sse="$(
  timeout 1 curl -NsS     "http://127.0.0.1:$PORT/api/v1/transcript/bciandroid-main"     || true
)"
grep -q 'retry: 2000' <<<"$sse"
grep -q 'BCI live caption integration test' <<<"$sse"

code="$(
  curl -sS -o /dev/null -w '%{http_code}'     "http://127.0.0.1:$PORT/api/v1/admin/status"
)"
test "$code" = "401"

curl -fsS   -H "Authorization: Bearer $BCI_ADMIN_TOKEN"   "http://127.0.0.1:$PORT/api/v1/admin/status" |
  grep -q '"online":true'

code="$(
  curl -sS -o /dev/null -w '%{http_code}'     -H "Authorization: Bearer wrong-token"     -H "X-BCI-Publisher-ID: bciwhisper-android"     "http://127.0.0.1:$PORT/api/v1/publish/bciandroid-main"
)"
test "$code" = "401"

echo "BCIAndroid gateway smoke test passed"
