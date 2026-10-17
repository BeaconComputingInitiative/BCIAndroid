FROM oven/bun:1.3.1-debian

RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /srv/bciandroid-gateway

COPY package.json ./
COPY src ./src

RUN mkdir -p /srv/bciandroid-gateway/data

ENV BCI_HOST=0.0.0.0
ENV BCI_PORT=8776
ENV BCI_STREAM_ID=bciandroid-main
ENV BCI_EXPECTED_PUBLISHER_ID=bciwhisper-android
ENV BCI_DB_PATH=/srv/bciandroid-gateway/data/bciandroid_gateway.db

EXPOSE 8776

CMD ["bun", "run", "src/server.ts"]
