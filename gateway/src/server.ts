#!/usr/bin/env bun
import { Database } from "bun:sqlite";
import { timingSafeEqual } from "node:crypto";
import { mkdirSync } from "node:fs";
import { dirname, resolve } from "node:path";

const VERSION = "1.0.0";
const PROTOCOL = "bci-broadcast-v1";
const SERVICE = "BCIAndroid Live Gateway";

const host = Bun.env.BCI_HOST || "0.0.0.0";
const port = Number.parseInt(Bun.env.BCI_PORT || "8776", 10);
const streamId = Bun.env.BCI_STREAM_ID || "bciandroid-main";
const expectedPublisherId =
  Bun.env.BCI_EXPECTED_PUBLISHER_ID || "bciwhisper-android";
const publisherToken = Bun.env.BCI_PUBLISHER_TOKEN || "";
const adminToken = Bun.env.BCI_ADMIN_TOKEN || "";
const maxListeners = Number.parseInt(
  Bun.env.BCI_MAX_LISTENERS || "128",
  10
);
const publicBase =
  Bun.env.BCI_PUBLIC_BASE_URL || `http://127.0.0.1:${port}`;
const dbPath = resolve(
  Bun.env.BCI_DB_PATH || "./data/bciandroid_gateway.db"
);

if (!publisherToken || !adminToken) {
  console.error("[BCIAndroid] FATAL: publisher/admin token missing");
  process.exit(2);
}

if (publisherToken === adminToken) {
  console.error(
    "[BCIAndroid] FATAL: publisher/admin tokens must differ"
  );
  process.exit(2);
}

if (!Number.isFinite(port) || port < 1 || port > 65535) {
  console.error("[BCIAndroid] FATAL: invalid BCI_PORT");
  process.exit(2);
}

if (
  !Number.isFinite(maxListeners) ||
  maxListeners < 1 ||
  maxListeners > 10000
) {
  console.error(
    "[BCIAndroid] FATAL: invalid BCI_MAX_LISTENERS"
  );
  process.exit(2);
}

mkdirSync(dirname(dbPath), { recursive: true });

const db = new Database(dbPath, { create: true });

db.run("PRAGMA journal_mode=WAL;");
db.run(`
CREATE TABLE IF NOT EXISTS gateway_events(
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  ts TEXT NOT NULL,
  type TEXT NOT NULL,
  detail TEXT NOT NULL
)
`);

const qEvent = db.query(
  "INSERT INTO gateway_events(ts,type,detail) VALUES($ts,$type,$detail)"
);

function event(type: string, detail: unknown) {
  const ts = new Date().toISOString();
  const body =
    typeof detail === "string"
      ? detail
      : JSON.stringify(detail);

  qEvent.run({
    $ts: ts,
    $type: type,
    $detail: body,
  });

  console.log(
    `[BCIAndroid] ${ts} ${type} ${body}`
  );
}

function safe(a: string, b: string) {
  const aa = Buffer.from(a);
  const bb = Buffer.from(b);

  return (
    aa.length === bb.length &&
    timingSafeEqual(aa, bb)
  );
}

function bearer(req: Request) {
  const value =
    req.headers.get("authorization") || "";

  return value
    .toLowerCase()
    .startsWith("bearer ")
    ? value.slice(7).trim()
    : "";
}

type PublisherCodec =
  | "mp3"
  | "pcm_s16le";

type WSData = {
  role: "publisher";
  publisherId: string;
  hello: boolean;
  inputCodec: PublisherCodec | null;
};

type Listener = {
  id: string;
  controller:
    ReadableStreamDefaultController<Uint8Array>;
};

const started = Date.now();

let publisher:
  ServerWebSocket<WSData> | null =
  null;

let streamLive = false;
let bytesReceived = 0;
let chunksReceived = 0;
let rejectedPublishers = 0;
let rejectedListeners = 0;

let publisherInputCodec:
  PublisherCodec | null =
  null;

let metadata = {
  codec: "mp3",
  mimeType: "audio/mpeg",
  sampleRate: 44100,
  channels: 1,
  bitrateKbps: 128,
};

const listeners =
  new Map<string, Listener>();

const buffer:
  Uint8Array[] =
  [];

let bufferBytes = 0;

const bufferLimit =
  Number.parseInt(
    Bun.env.BCI_REPLAY_BUFFER_BYTES ||
      String(1024 * 1024),
    10
  );

let transcoder:
  ReturnType<typeof Bun.spawn> | null =
  null;

function pushBuffer(
  chunk: Uint8Array
) {
  buffer.push(chunk);
  bufferBytes += chunk.byteLength;

  while (
    bufferBytes > bufferLimit &&
    buffer.length > 1
  ) {
    const old = buffer.shift();

    if (old) {
      bufferBytes -= old.byteLength;
    }
  }
}

function fanout(
  chunk: Uint8Array
) {
  for (
    const [id, listener]
    of listeners
  ) {
    try {
      listener.controller.enqueue(
        chunk
      );
    } catch {
      listeners.delete(id);
    }
  }
}

function closeListeners() {
  for (
    const [, listener]
    of listeners
  ) {
    try {
      listener.controller.close();
    } catch {
    }
  }

  listeners.clear();
}

function stopTranscoder() {
  const proc = transcoder;
  transcoder = null;

  if (!proc) {
    return;
  }

  try {
    proc.stdin.end();
  } catch {
  }

  try {
    proc.kill();
  } catch {
  }
}

function startPcmTranscoder(
  sampleRate: number,
  channels: number,
  bitrateKbps: number
) {
  stopTranscoder();

  const ffmpeg =
    Bun.which("ffmpeg");

  if (!ffmpeg) {
    throw new Error(
      "ffmpeg is required for pcm_s16le publishing"
    );
  }

  const proc =
    Bun.spawn(
      [
        ffmpeg,
        "-hide_banner",
        "-loglevel",
        "error",
        "-f",
        "s16le",
        "-ar",
        String(sampleRate),
        "-ac",
        String(channels),
        "-i",
        "pipe:0",
        "-vn",
        "-c:a",
        "libmp3lame",
        "-b:a",
        `${bitrateKbps}k`,
        "-f",
        "mp3",
        "pipe:1",
      ],
      {
        stdin: "pipe",
        stdout: "pipe",
        stderr: "pipe",
      }
    );

  transcoder = proc;

  void (async () => {
    const reader =
      proc.stdout.getReader();

    try {
      while (true) {
        const {
          done,
          value,
        } = await reader.read();

        if (done) {
          break;
        }

        if (
          !value ||
          !value.byteLength
        ) {
          continue;
        }

        streamLive = true;
        pushBuffer(value);
        fanout(value);
      }
    } catch (err) {
      event(
        "transcoder_stdout_error",
        {
          error: String(err),
        }
      );
    }
  })();

  void (async () => {
    const reader =
      proc.stderr.getReader();

    const decoder =
      new TextDecoder();

    try {
      while (true) {
        const {
          done,
          value,
        } = await reader.read();

        if (done) {
          break;
        }

        if (
          !value ||
          !value.byteLength
        ) {
          continue;
        }

        const line =
          decoder
            .decode(value)
            .trim();

        if (line) {
          event(
            "transcoder_stderr",
            line
          );
        }
      }
    } catch {
    }
  })();

  event(
    "transcoder_started",
    {
      inputCodec: "pcm_s16le",
      outputCodec: "mp3",
      sampleRate,
      channels,
      bitrateKbps,
    }
  );
}

const playerHtml =
  `<!doctype html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width">
<title>BCIAndroid StreamPlayer</title>
<style>
body{margin:0;min-height:100vh;display:grid;place-items:center;background:#05070a;color:#dce8ee;font-family:system-ui}
main{width:min(720px,90vw);padding:28px;border:1px solid #30363d;border-radius:16px;background:#0c1117}
h1{color:#00d8e6}.brand{color:#7a1733;font-weight:700;letter-spacing:.08em}audio{width:100%}
.meta{opacity:.72}
</style>
</head>
<body>
<main>
<div class="brand">BEACON COMPUTING INITIATIVE</div>
<h1>BCIANDROID LIVE</h1>
<p id="s">Checking stream…</p>
<p class="meta">Dedicated Android broadcast gateway</p>
<audio controls src="/live/${streamId}"></audio>
<script>
async function tick(){
  try{
    const r=await fetch('/api/v1/status',{cache:'no-store'});
    const j=await r.json();
    document.getElementById('s').textContent=
      j.stream.live
        ? 'LIVE · '+j.stream.listeners+' listener(s)'
        : 'OFFLINE';
  }catch{}
}
tick();
setInterval(tick,2000);
</script>
</main>
</body>
</html>`;

const server =
  Bun.serve<WSData>({
    hostname: host,
    port,
    idleTimeout: 30,

    fetch(req, server) {
      const u =
        new URL(req.url);

      if (
        u.pathname ===
        "/health"
      ) {
        return Response.json({
          ok: true,
          service: SERVICE,
          version: VERSION,
          uptimeSeconds:
            Math.floor(
              (
                Date.now() -
                started
              ) / 1000
            ),
        });
      }

      if (
        u.pathname ===
        "/api/v1/status"
      ) {
        return Response.json({
          service: SERVICE,
          version: VERSION,
          protocol: PROTOCOL,
          stream: {
            id: streamId,
            live: streamLive,
            listeners:
              listeners.size,
            maxListeners,
            publisherInputCodec,
            ...metadata,
          },
          playerUrl:
            `${publicBase.replace(/\/$/, "")}/player`,
        });
      }

      if (
        u.pathname ===
        "/player"
      ) {
        return new Response(
          playerHtml,
          {
            headers: {
              "Content-Type":
                "text/html; charset=utf-8",
              "Cache-Control":
                "no-store",
            },
          }
        );
      }

      if (
        u.pathname ===
        `/live/${streamId}`
      ) {
        if (
          !streamLive ||
          !publisher
        ) {
          return Response.json(
            {
              error:
                "stream_offline",
            },
            {
              status: 503,
            }
          );
        }

        if (
          listeners.size >=
          maxListeners
        ) {
          rejectedListeners += 1;

          return Response.json(
            {
              error:
                "listener_capacity",
            },
            {
              status: 503,
            }
          );
        }

        server.timeout(
          req,
          0
        );

        const id =
          crypto.randomUUID();

        const rs =
          new ReadableStream<
            Uint8Array
          >({
            start(controller) {
              listeners.set(
                id,
                {
                  id,
                  controller,
                }
              );

              for (
                const chunk
                of buffer
              ) {
                controller.enqueue(
                  chunk
                );
              }

              event(
                "listener_connected",
                {
                  id,
                  count:
                    listeners.size,
                }
              );
            },

            cancel() {
              listeners.delete(
                id
              );

              event(
                "listener_disconnected",
                {
                  id,
                  count:
                    listeners.size,
                }
              );
            },
          });

        return new Response(
          rs,
          {
            headers: {
              "Content-Type":
                metadata.mimeType,
              "Cache-Control":
                "no-store",
              "X-Content-Type-Options":
                "nosniff",
            },
          }
        );
      }

      if (
        u.pathname ===
        `/api/v1/publish/${streamId}`
      ) {
        if (
          !safe(
            bearer(req),
            publisherToken
          )
        ) {
          rejectedPublishers += 1;

          return new Response(
            "unauthorized",
            {
              status: 401,
            }
          );
        }

        const publisherId =
          req.headers.get(
            "x-bci-publisher-id"
          ) || "";

        if (
          publisherId !==
          expectedPublisherId
        ) {
          rejectedPublishers += 1;

          return new Response(
            "publisher identity rejected",
            {
              status: 403,
            }
          );
        }

        if (publisher) {
          return new Response(
            "publisher already connected",
            {
              status: 409,
            }
          );
        }

        const upgraded =
          server.upgrade(
            req,
            {
              data: {
                role: "publisher",
                publisherId,
                hello: false,
                inputCodec: null,
              },
            }
          );

        return upgraded
          ? undefined
          : new Response(
              "upgrade failed",
              {
                status: 400,
              }
            );
      }

      if (
        u.pathname ===
        "/api/v1/admin/status"
      ) {
        if (
          !safe(
            bearer(req),
            adminToken
          )
        ) {
          return new Response(
            "unauthorized",
            {
              status: 401,
            }
          );
        }

        return Response.json({
          server: {
            online: true,
            uptimeSeconds:
              Math.floor(
                (
                  Date.now() -
                  started
                ) / 1000
              ),
          },
          publisher: {
            connected:
              Boolean(publisher),
            expectedPublisherId,
            inputCodec:
              publisherInputCodec,
            rejectedPublishers,
          },
          stream: {
            id: streamId,
            live: streamLive,
            bytesReceived,
            chunksReceived,
            bufferBytes,
            listeners:
              listeners.size,
            maxListeners,
            rejectedListeners,
          },
        });
      }

      return new Response(
        "not found",
        {
          status: 404,
        }
      );
    },

    websocket: {
      data:
        {} as WSData,

      maxPayloadLength:
        1024 * 1024,

      open(ws) {
        publisher = ws;
        publisherInputCodec = null;
        streamLive = false;

        buffer.length = 0;
        bufferBytes = 0;

        event(
          "publisher_connected",
          {
            publisherId:
              ws.data.publisherId,
          }
        );
      },

      message(ws, message) {
        if (
          typeof message ===
          "string"
        ) {
          let body:
            Record<string, unknown>;

          try {
            body =
              JSON.parse(
                message
              );
          } catch {
            ws.close(
              1003,
              "invalid_json"
            );
            return;
          }

          if (
            body.type ===
            "heartbeat"
          ) {
            return;
          }

          if (
            body.type ===
            "hello"
          ) {
            const codec =
              String(
                body.codec || ""
              )
                .toLowerCase()
                as PublisherCodec;

            if (
              body.protocol !==
                PROTOCOL ||
              (
                codec !== "mp3" &&
                codec !==
                  "pcm_s16le"
              )
            ) {
              ws.close(
                1008,
                "protocol_or_codec_mismatch"
              );
              return;
            }

            const sampleRate =
              Number(
                body.sampleRate
              ) || 44100;

            const channels =
              Number(
                body.channels
              ) || 1;

            const bitrateKbps =
              Number(
                body.bitrateKbps
              ) || 128;

            if (
              sampleRate < 8000 ||
              sampleRate > 192000 ||
              channels < 1 ||
              channels > 2 ||
              bitrateKbps < 32 ||
              bitrateKbps > 320
            ) {
              ws.close(
                1008,
                "invalid_audio_parameters"
              );
              return;
            }

            ws.data.inputCodec =
              codec;

            publisherInputCodec =
              codec;

            metadata = {
              codec: "mp3",
              mimeType:
                "audio/mpeg",
              sampleRate,
              channels,
              bitrateKbps,
            };

            if (
              codec ===
              "pcm_s16le"
            ) {
              try {
                startPcmTranscoder(
                  sampleRate,
                  channels,
                  bitrateKbps
                );
              } catch (err) {
                event(
                  "transcoder_start_failed",
                  {
                    error:
                      String(err),
                  }
                );

                ws.close(
                  1011,
                  "transcoder_unavailable"
                );

                return;
              }
            } else {
              stopTranscoder();
            }

            ws.data.hello =
              true;

            ws.send(
              JSON.stringify({
                type:
                  "hello_ack",
                protocol:
                  PROTOCOL,
                streamId,
                inputCodec:
                  codec,
                outputCodec:
                  "mp3",
              })
            );

            event(
              "publisher_hello",
              {
                inputCodec:
                  codec,
                ...metadata,
              }
            );

            return;
          }

          return;
        }

        if (
          !ws.data.hello
        ) {
          ws.close(
            1008,
            "hello_required"
          );
          return;
        }

        const chunk =
          message instanceof
            ArrayBuffer
            ? new Uint8Array(
                message.slice(0)
              )
            : Uint8Array.from(
                message
              );

        if (
          !chunk.byteLength
        ) {
          return;
        }

        bytesReceived +=
          chunk.byteLength;

        chunksReceived += 1;

        if (
          ws.data.inputCodec ===
          "pcm_s16le"
        ) {
          const proc =
            transcoder;

          if (!proc) {
            ws.close(
              1011,
              "transcoder_missing"
            );
            return;
          }

          try {
            proc.stdin.write(
              chunk
            );
          } catch (err) {
            event(
              "transcoder_stdin_error",
              {
                error:
                  String(err),
              }
            );

            ws.close(
              1011,
              "transcoder_write_failed"
            );
          }

          return;
        }

        streamLive = true;
        pushBuffer(chunk);
        fanout(chunk);
      },

      close(
        ws,
        code,
        reason
      ) {
        if (
          publisher === ws
        ) {
          publisher = null;
        }

        publisherInputCodec =
          null;

        streamLive = false;
        stopTranscoder();
        closeListeners();

        event(
          "publisher_disconnected",
          {
            code,
            reason:
              String(
                reason || ""
              ),
          }
        );
      },
    },
  });

console.log(
  "=============================================================================="
);
console.log(
  "BEACON COMPUTING INITIATIVE"
);
console.log(
  `BCIAndroid Live Gateway v${VERSION}`
);
console.log(
  "Developer Seal: CJB-DEV-2026"
);
console.log(
  "=============================================================================="
);
console.log(
  `[BCIAndroid] Listening: ${server.url}`
);
console.log(
  `[BCIAndroid] Stream ID: ${streamId}`
);
console.log(
  `[BCIAndroid] Expected publisher: ${expectedPublisherId}`
);
console.log(
  `[BCIAndroid] Player: ${publicBase.replace(/\/$/, "")}/player`
);

event(
  "gateway_started",
  {
    host,
    port,
    streamId,
    expectedPublisherId,
    maxListeners,
  }
);
