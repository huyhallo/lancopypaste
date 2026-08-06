const { execFile } = require("child_process");
const os = require("os");
const { WebSocket } = require("ws");

const SERVER_URL = process.env.SERVER_URL || "ws://localhost:3000";
const DEVICE_NAME = process.env.DEVICE_NAME || `${os.hostname()} Auto`;
const POLL_MS = Number(process.env.POLL_MS || 800);

let socket;
let lastSeenText = "";
let lastNetworkText = "";
let connected = false;
let pollTimer = null;
let writingClipboard = false;

function runPowerShell(args, input) {
  return new Promise((resolve, reject) => {
    const child = execFile(
      "powershell.exe",
      ["-NoProfile", "-ExecutionPolicy", "Bypass", ...args],
      { windowsHide: true, maxBuffer: 1024 * 1024 },
      (error, stdout, stderr) => {
        if (error) {
          reject(new Error(stderr || error.message));
          return;
        }
        resolve(stdout);
      }
    );

    if (input !== undefined) {
      child.stdin.end(input);
    }
  });
}

async function readClipboard() {
  const output = await runPowerShell(["-Command", "Get-Clipboard -Raw"]);
  return output.replace(/\r?\n$/, "");
}

async function writeClipboard(text) {
  writingClipboard = true;
  try {
    await runPowerShell(
      [
        "-Command",
        "[Console]::In.ReadToEnd() | Set-Clipboard",
      ],
      text
    );
    lastSeenText = text;
    lastNetworkText = text;
  } finally {
    setTimeout(() => {
      writingClipboard = false;
    }, 250);
  }
}

function sendClip(text) {
  if (!connected || socket.readyState !== WebSocket.OPEN) return;
  socket.send(JSON.stringify({ type: "clip", text }));
}

async function pollClipboard() {
  if (writingClipboard) return;

  try {
    const text = await readClipboard();
    if (!text.trim()) return;
    if (text === lastSeenText || text === lastNetworkText) return;

    lastSeenText = text;
    sendClip(text);
    console.log(`[sent] ${preview(text)}`);
  } catch (error) {
    console.error(`[clipboard read error] ${error.message}`);
  }
}

function preview(text) {
  const oneLine = text.replace(/\s+/g, " ").trim();
  return oneLine.length > 80 ? `${oneLine.slice(0, 80)}...` : oneLine;
}

function connect() {
  socket = new WebSocket(SERVER_URL);

  socket.on("open", async () => {
    connected = true;
    console.log(`[connected] ${SERVER_URL} as "${DEVICE_NAME}"`);
    socket.send(JSON.stringify({ type: "hello", name: DEVICE_NAME }));

    try {
      lastSeenText = await readClipboard();
    } catch {
      lastSeenText = "";
    }

    if (!pollTimer) {
      pollTimer = setInterval(pollClipboard, POLL_MS);
    }
  });

  socket.on("message", async (raw) => {
    let message;
    try {
      message = JSON.parse(raw.toString());
    } catch {
      return;
    }

    if (message.type !== "clip" || !message.clip?.text) return;

    const text = String(message.clip.text);
    if (text === lastSeenText) return;

    try {
      await writeClipboard(text);
      console.log(`[received from ${message.clip.fromName}] ${preview(text)}`);
    } catch (error) {
      console.error(`[clipboard write error] ${error.message}`);
    }
  });

  socket.on("close", () => {
    connected = false;
    console.log("[disconnected] reconnecting...");
    setTimeout(connect, 1200);
  });

  socket.on("error", (error) => {
    console.error(`[socket error] ${error.message}`);
  });
}

console.log("LAN CopyPaste desktop agent");
console.log(`Watching Windows clipboard every ${POLL_MS}ms`);
connect();
