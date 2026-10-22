#!/usr/bin/env bun
/*
 * BEACON COMPUTING INITIATIVE
 * BCIAndroid Live Gateway
 * Developer Seal: CJB-DEV-2026
 * Integrity: SHA-256 emitted by gateway CI for each committed revision.
 */

import { Database } from "bun:sqlite";
import { timingSafeEqual } from "node:crypto";
import { mkdirSync } from "node:fs";
import { dirname, resolve } from "node:path";

const VERSION = "1.1.0";
const PROTOCOL = "bci-broadcast-v1";
const SERVICE = "BCIAndroid Live Gateway";

const host = Bun.env.BCI_HOST || "0.0.0.0";
const port = Number.parseInt(Bun.env.PORT || Bun.env.BCI_PORT || "8776", 10);
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
  Bun.env.BCI_PUBLIC_BASE_URL ||
  Bun.env.RENDER_EXTERNAL_URL ||
  `http://127.0.0.1:${port}`;
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

type TranscriptSegment = {
  id: string;
  sequence: number;
  startMs: number;
  endMs: number;
  speaker: string | null;
  text: string;
  decodeMs: number;
  final: boolean;
  receivedAt: string;
};

type TranscriptListener = {
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
let rejectedTranscriptListeners = 0;

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

const transcriptListeners =
  new Map<string, TranscriptListener>();

const transcriptHistory:
  TranscriptSegment[] =
  [];

const transcriptEncoder =
  new TextEncoder();

const transcriptHistoryLimit =
  Math.min(
    500,
    Math.max(
      20,
      Number.parseInt(
        Bun.env.BCI_TRANSCRIPT_HISTORY_LIMIT ||
          "120",
        10
      ) || 120
    )
  );

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

function encodeTranscriptEvent(
  name: string,
  payload: unknown
) {
  return transcriptEncoder.encode(
    "event: " +
      name +
      "\ndata: " +
      JSON.stringify(payload) +
      "\n\n"
  );
}

function fanoutTranscriptEvent(
  name: string,
  payload: unknown
) {
  const encoded =
    encodeTranscriptEvent(
      name,
      payload
    );

  for (
    const [id, listener]
    of transcriptListeners
  ) {
    try {
      listener.controller.enqueue(
        encoded
      );
    } catch {
      transcriptListeners.delete(
        id
      );
    }
  }
}

function pushTranscript(
  segment: TranscriptSegment
) {
  transcriptHistory.push(
    segment
  );

  while (
    transcriptHistory.length >
    transcriptHistoryLimit
  ) {
    transcriptHistory.shift();
  }

  fanoutTranscriptEvent(
    "transcript",
    segment
  );
}

function resetTranscriptHistory() {
  transcriptHistory.length =
    0;

  fanoutTranscriptEvent(
    "reset",
    {
      streamId,
    }
  );
}

const transcriptHeartbeat =
  setInterval(
    () => {
      fanoutTranscriptEvent(
        "heartbeat",
        {
          ts:
            new Date()
              .toISOString(),
        }
      );
    },
    15_000
  );

transcriptHeartbeat.unref?.();

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
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="color-scheme" content="dark">
<title>BCIWhisper Live Stream</title>
<style>
:root{--bg:#05070a;--panel:#0c1117;--panel2:#101821;--line:#26323d;--text:#e5eef3;--muted:#8fa2ad;--cyan:#00d8e6;--green:#21d86b}
*{box-sizing:border-box}body{margin:0;min-height:100vh;background:radial-gradient(circle at 30% 0,#0c1c28 0,#05070a 40%);color:var(--text);font-family:system-ui,-apple-system,Segoe UI,sans-serif}
header{height:72px;padding:0 28px;display:flex;align-items:center;justify-content:space-between;border-bottom:1px solid var(--line);background:#071018dd;backdrop-filter:blur(12px)}
.brand{font-weight:800;letter-spacing:.08em}.brand strong{color:var(--cyan)}.sub{color:var(--muted);font-weight:500;letter-spacing:0;margin-left:8px}
.pill{display:inline-flex;align-items:center;gap:8px;padding:8px 12px;border:1px solid var(--line);border-radius:999px;color:var(--muted)}.dot{width:10px;height:10px;border-radius:50%;background:#5a6570}.dot.live{background:var(--green);box-shadow:0 0 16px #21d86b88}
main{width:min(1320px,96vw);margin:24px auto;display:grid;grid-template-columns:minmax(0,1.7fr) minmax(320px,.8fr);gap:18px}.card{border:1px solid var(--line);border-radius:16px;background:linear-gradient(180deg,#0d151d,#091017);overflow:hidden}.cardhead{padding:16px 18px;border-bottom:1px solid var(--line);display:flex;align-items:center;justify-content:space-between;gap:12px}
h1,h2,p{margin:0}h1{font-size:20px}h2{font-size:17px}.meta{color:var(--muted);font-size:13px}.playerbody{padding:18px}
canvas{display:block;width:100%;height:260px;border-radius:12px;background:linear-gradient(180deg,#06101a,#07121a);border:1px solid #152634}
audio{width:100%;margin-top:16px}.stats{display:grid;grid-template-columns:repeat(4,1fr);gap:10px;margin-top:14px}.stat{padding:12px;border-radius:10px;background:var(--panel2);border:1px solid #192631}.stat b{display:block;margin-top:4px;font-size:15px}.transcript{height:610px;display:flex;flex-direction:column}.captions{padding:8px 16px 18px;overflow:auto;flex:1}.empty{color:var(--muted);padding:22px 4px}.caption{padding:13px 0;border-bottom:1px solid #18232c}.captiontop{display:flex;gap:10px;align-items:center;color:var(--muted);font-size:12px}.speaker{color:var(--cyan);font-weight:700}.captiontext{font-size:16px;line-height:1.45;margin-top:5px}.footer{grid-column:1/-1;padding:14px 18px;display:flex;justify-content:space-between;gap:12px;color:var(--muted);font-size:12px}
@media(max-width:820px){header{padding:0 16px}.sub{display:none}main{grid-template-columns:1fr;margin:12px auto}.transcript{height:440px}.stats{grid-template-columns:1fr 1fr}canvas{height:190px}}
</style>
</head>
<body>
<header>
  <div class="brand"><strong>BCI</strong> WHISPER <span class="sub">Live Stream Player</span></div>
  <div class="pill"><span id="dot" class="dot"></span><span id="status">Checking stream…</span></div>
</header>
<main>
  <section class="card">
    <div class="cardhead">
      <div><h1>Live Audio</h1><div class="meta">Public BCIAndroid broadcast</div></div>
      <div id="format" class="meta">44.1 kHz · mono · MP3</div>
    </div>
    <div class="playerbody">
      <canvas id="wave" width="1000" height="260" aria-label="Live audio waveform"></canvas>
      <audio id="audio" controls preload="none" src="/live/${streamId}"></audio>
      <div class="stats">
        <div class="stat"><span class="meta">State</span><b id="streamState">OFFLINE</b></div>
        <div class="stat"><span class="meta">Listeners</span><b id="listeners">0</b></div>
        <div class="stat"><span class="meta">Captions</span><b id="captionCount">0</b></div>
        <div class="stat"><span class="meta">Output</span><b id="codec">MP3</b></div>
      </div>
    </div>
  </section>

  <aside class="card transcript">
    <div class="cardhead">
      <div><h2>Live Transcription</h2><div class="meta">whisper.cpp captions</div></div>
      <div class="pill"><span class="dot live"></span><span id="ccState">Connected</span></div>
    </div>
    <div id="captions" class="captions">
      <div id="empty" class="empty">Waiting for live speech…</div>
    </div>
  </aside>

  <section class="card footer">
    <span>BEACON COMPUTING INITIATIVE · BCIWhisper</span>
    <span id="captionMeta">Captions reconnect automatically</span>
  </section>
</main>
<script>
const audio=document.getElementById('audio');
const statusEl=document.getElementById('status');
const dot=document.getElementById('dot');
const streamState=document.getElementById('streamState');
const listenerEl=document.getElementById('listeners');
const formatEl=document.getElementById('format');
const codecEl=document.getElementById('codec');
const captionsEl=document.getElementById('captions');
const captionCountEl=document.getElementById('captionCount');
const ccState=document.getElementById('ccState');
const captionMeta=document.getElementById('captionMeta');
const seen=new Set();
let captionCount=0;
let lastLive=null;
let audioContext=null;
let analyser=null;
let source=null;

function formatTime(ms){
  const total=Math.max(0,Math.floor(Number(ms||0)/1000));
  const m=Math.floor(total/60);
  const sec=String(total%60).padStart(2,'0');
  return String(m).padStart(2,'0')+':'+sec;
}

function resetCaptions(){
  seen.clear();
  captionCount=0;
  captionCountEl.textContent='0';
  captionsEl.innerHTML='<div id="empty" class="empty">Waiting for live speech…</div>';
}

function addCaption(segment){
  if(!segment||!segment.id||seen.has(segment.id))return;
  seen.add(segment.id);
  const placeholder=document.getElementById('empty');
  if(placeholder)placeholder.remove();
  const row=document.createElement('div');
  row.className='caption';
  const top=document.createElement('div');
  top.className='captiontop';
  const time=document.createElement('span');
  time.textContent=formatTime(segment.startMs);
  const speaker=document.createElement('span');
  speaker.className='speaker';
  speaker.textContent=segment.speaker||'Live';
  const body=document.createElement('div');
  body.className='captiontext';
  body.textContent=segment.text||'';
  top.append(time,speaker);
  row.append(top,body);
  captionsEl.appendChild(row);
  while(captionsEl.children.length>80)captionsEl.firstElementChild.remove();
  captionCount+=1;
  captionCountEl.textContent=String(captionCount);
  captionsEl.scrollTop=captionsEl.scrollHeight;
  if(segment.decodeMs!==undefined)captionMeta.textContent='Last Whisper decode: '+segment.decodeMs+' ms';
}

const events=new EventSource('/api/v1/transcript/${streamId}');
events.addEventListener('open',function(){ccState.textContent='Connected';});
events.addEventListener('error',function(){ccState.textContent='Reconnecting…';});
events.addEventListener('reset',function(){resetCaptions();});
events.addEventListener('transcript',function(event){
  try{addCaption(JSON.parse(event.data));}catch{}
});

async function tick(){
  try{
    const response=await fetch('/api/v1/status',{cache:'no-store'});
    const data=await response.json();
    const live=Boolean(data.stream&&data.stream.live);
    statusEl.textContent=live?'LIVE':'OFFLINE';
    streamState.textContent=live?'LIVE':'OFFLINE';
    dot.classList.toggle('live',live);
    listenerEl.textContent=String(data.stream.listeners||0);
    codecEl.textContent=String((data.stream.codec||'mp3').toUpperCase());
    formatEl.textContent=String(data.stream.sampleRate||44100)+' Hz · '+String(data.stream.channels||1)+' ch · '+String(data.stream.bitrateKbps||128)+' kbps';
    if(live&&lastLive===false){
      audio.src='/live/${streamId}?t='+Date.now();
      audio.load();
    }
    lastLive=live;
  }catch{
    statusEl.textContent='UNAVAILABLE';
    dot.classList.remove('live');
  }
}
tick();
setInterval(tick,1500);

function initVisualizer(){
  if(audioContext)return;
  try{
    audioContext=new (window.AudioContext||window.webkitAudioContext)();
    source=audioContext.createMediaElementSource(audio);
    analyser=audioContext.createAnalyser();
    analyser.fftSize=1024;
    source.connect(analyser);
    analyser.connect(audioContext.destination);
    draw();
  }catch{}
}

function draw(){
  if(!analyser)return;
  const canvas=document.getElementById('wave');
  const ctx=canvas.getContext('2d');
  const data=new Uint8Array(analyser.fftSize);
  analyser.getByteTimeDomainData(data);
  ctx.fillStyle='#06101a';
  ctx.fillRect(0,0,canvas.width,canvas.height);
  ctx.strokeStyle='#00d8e6';
  ctx.lineWidth=2;
  ctx.beginPath();
  for(let i=0;i<data.length;i++){
    const x=i/(data.length-1)*canvas.width;
    const y=data[i]/255*canvas.height;
    if(i===0)ctx.moveTo(x,y);else ctx.lineTo(x,y);
  }
  ctx.stroke();
  requestAnimationFrame(draw);
}
audio.addEventListener('play',initVisualizer);
</script>
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
            transcriptListeners:
              transcriptListeners.size,
            transcriptSegments:
              transcriptHistory.length,
            publisherInputCodec,
            ...metadata,
          },
          playerUrl:
            `${publicBase.replace(/\/$/, "")}/`,
        });
      }

      if (
        u.pathname === "/" ||
        u.pathname === "/player"
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
        `/api/v1/transcript/${streamId}/history`
      ) {
        return Response.json({
          streamId,
          segments:
            transcriptHistory,
        }, {
          headers: {
            "Cache-Control":
              "no-store",
          },
        });
      }

      if (
        u.pathname ===
        `/api/v1/transcript/${streamId}`
      ) {
        if (
          transcriptListeners.size >=
          maxListeners
        ) {
          rejectedTranscriptListeners +=
            1;

          return Response.json(
            {
              error:
                "transcript_listener_capacity",
            },
            {
              status:
                503,
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
          new ReadableStream<Uint8Array>({
            start(controller) {
              transcriptListeners.set(
                id,
                { id, controller }
              );

              controller.enqueue(
                transcriptEncoder.encode(
                  "retry: 2000\\n\\n"
                )
              );

              for (
                const segment
                of transcriptHistory
              ) {
                controller.enqueue(
                  encodeTranscriptEvent(
                    "transcript",
                    segment
                  )
                );
              }
            },

            cancel() {
              transcriptListeners.delete(
                id
              );
            },
          });

        return new Response(
          rs,
          {
            headers: {
              "Content-Type":
                "text/event-stream; charset=utf-8",
              "Cache-Control":
                "no-store",
              "Connection":
                "keep-alive",
              "X-Accel-Buffering":
                "no",
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
            transcriptListeners:
              transcriptListeners.size,
            transcriptSegments:
              transcriptHistory.length,
            rejectedTranscriptListeners,
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
        resetTranscriptHistory();

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
              ).toLowerCase() as PublisherCodec;

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

          if (
            body.type ===
            "transcript"
          ) {
            if (
              !ws.data.hello
            ) {
              ws.close(
                1008,
                "hello_required"
              );
              return;
            }

            const text =
              String(
                body.text ||
                  ""
              )
                .trim()
                .slice(
                  0,
                  1000
                );

            if (!text) {
              return;
            }

            const sequence =
              Math.max(
                0,
                Number(
                  body.sequence
                ) || 0
              );

            const startMs =
              Math.max(
                0,
                Number(
                  body.startMs
                ) || 0
              );

            const endMs =
              Math.max(
                startMs,
                Number(
                  body.endMs
                ) || startMs
              );

            const decodeMs =
              Math.max(
                0,
                Number(
                  body.decodeMs
                ) || 0
              );

            const rawSpeaker =
              body.speaker == null
                ? ""
                : String(
                    body.speaker
                  )
                    .trim()
                    .slice(
                      0,
                      64
                    );

            const receivedAt =
              new Date()
                .toISOString();

            const segment:
              TranscriptSegment = {
              id:
                ws.data.publisherId +
                ":" +
                String(
                  sequence
                ) +
                ":" +
                receivedAt,
              sequence,
              startMs,
              endMs,
              speaker:
                rawSpeaker ||
                null,
              text,
              decodeMs,
              final:
                body.final !==
                false,
              receivedAt,
            };

            pushTranscript(
              segment
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
  `[BCIAndroid] Player: ${publicBase.replace(/\/$/, "")}/`
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
