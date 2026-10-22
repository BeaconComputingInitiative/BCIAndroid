/*
 * BEACON COMPUTING INITIATIVE
 * BCIAndroid Gateway Transcript Integration Probe
 * Developer Seal: CJB-DEV-2026
 */

const port =
  Bun.env.BCI_PORT ||
  "18776";

const token =
  Bun.env.BCI_PUBLISHER_TOKEN ||
  "";

const publisherId =
  Bun.env.BCI_EXPECTED_PUBLISHER_ID ||
  "bciwhisper-android";

if (!token) {
  throw new Error(
    "BCI_PUBLISHER_TOKEN is required"
  );
}

await new Promise<void>(
  (resolve, reject) => {
    let acknowledged =
      false;

    let transcriptSent =
      false;

    const timer =
      setTimeout(
        () => {
          reject(
            new Error(
              "Transcript WebSocket probe timed out"
            )
          );
        },
        5_000
      );

    const ws =
      new WebSocket(
        `ws://127.0.0.1:${port}/api/v1/publish/bciandroid-main`,
        {
          headers: {
            Authorization:
              `Bearer ${token}`,
            "X-BCI-Publisher-ID":
              publisherId,
          },
        }
      );

    ws.addEventListener(
      "open",
      () => {
        ws.send(
          JSON.stringify({
            type:
              "hello",
            protocol:
              "bci-broadcast-v1",
            codec:
              "mp3",
            sampleRate:
              44100,
            channels:
              1,
            bitrateKbps:
              128,
          })
        );
      }
    );

    ws.addEventListener(
      "message",
      (event) => {
        const body =
          JSON.parse(
            String(
              event.data
            )
          );

        if (
          body.type !==
          "hello_ack"
        ) {
          return;
        }

        acknowledged =
          true;

        ws.send(
          JSON.stringify({
            type:
              "transcript",
            sequence:
              1,
            startMs:
              1200,
            endMs:
              2800,
            speaker:
              "Speaker 1",
            text:
              "BCI live caption integration test",
            decodeMs:
              42,
            final:
              true,
          })
        );

        transcriptSent =
          true;

        setTimeout(
          () => {
            clearTimeout(
              timer
            );

            ws.close(
              1000,
              "probe_complete"
            );

            resolve();
          },
          150
        );
      }
    );

    ws.addEventListener(
      "error",
      () => {
        clearTimeout(
          timer
        );

        reject(
          new Error(
            "Transcript WebSocket probe failed"
          )
        );
      }
    );

    ws.addEventListener(
      "close",
      () => {
        if (
          !acknowledged ||
          !transcriptSent
        ) {
          clearTimeout(
            timer
          );

          reject(
            new Error(
              "Publisher closed before transcript was sent"
            )
          );
        }
      }
    );
  }
);
