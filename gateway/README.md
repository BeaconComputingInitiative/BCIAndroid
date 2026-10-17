# BCIAndroid Live Gateway

This service is the dedicated broadcast gateway for the native BCIAndroid application.

It is independent of the desktop BCIWhisper gateway while retaining the same `bci-broadcast-v1` wire protocol.

## Data path

```text
BCIAndroid AudioRecord
        ↓ PCM16 44.1 kHz mono
BroadcastPublisher
        ↓ outbound authenticated WSS
BCIAndroid Live Gateway
        ↓ ffmpeg MP3 transcode
/live/bciandroid-main
        ↓
BCIAndroid StreamPlayer
```

The Android device never exposes an inbound port.

## Required environment

```text
BCI_PUBLISHER_TOKEN
BCI_ADMIN_TOKEN
```

The two tokens must be different.

Optional settings:

```text
BCI_HOST=0.0.0.0
BCI_PORT=8776
BCI_STREAM_ID=bciandroid-main
BCI_EXPECTED_PUBLISHER_ID=bciwhisper-android
BCI_PUBLIC_BASE_URL=https://your-public-gateway.example
BCI_DB_PATH=./data/bciandroid_gateway.db
BCI_MAX_LISTENERS=128
BCI_REPLAY_BUFFER_BYTES=1048576
```

## Local development

```bash
export BCI_PUBLISHER_TOKEN='replace-me'
export BCI_ADMIN_TOKEN='replace-with-different-token'

bun run gateway/src/server.ts
```

Health:

```bash
curl http://127.0.0.1:8776/health
```

Public status:

```bash
curl http://127.0.0.1:8776/api/v1/status
```

Admin status:

```bash
curl \
  -H "Authorization: Bearer $BCI_ADMIN_TOKEN" \
  http://127.0.0.1:8776/api/v1/admin/status
```

## Android publisher URL

For the default stream ID the app publishes to:

```text
wss://YOUR_GATEWAY_HOST/api/v1/publish/bciandroid-main
```

The gateway requires the Android publisher identity header:

```text
X-BCI-Publisher-ID: bciwhisper-android
```

BCIAndroid already sends that identity.

## TLS

The Android application intentionally accepts only `wss://` gateway URLs. Run this service behind a TLS-terminating reverse proxy or hosting platform and set `BCI_PUBLIC_BASE_URL` to the corresponding `https://` public origin.

Do not expose the publisher or admin tokens in source control, Docker images, APK resources, or client-visible configuration.

## Container

```bash
docker build -t bciandroid-live-gateway gateway

docker run --rm \
  -p 8776:8776 \
  -e BCI_PUBLISHER_TOKEN="$BCI_PUBLISHER_TOKEN" \
  -e BCI_ADMIN_TOKEN="$BCI_ADMIN_TOKEN" \
  -e BCI_PUBLIC_BASE_URL="https://YOUR_GATEWAY_HOST" \
  -v "$PWD/gateway-data:/srv/bciandroid-gateway/data" \
  bciandroid-live-gateway
```

The service requires `ffmpeg` for the Android PCM16-to-MP3 live path. The supplied image includes it.
