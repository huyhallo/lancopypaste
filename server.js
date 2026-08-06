const express = require("express");
const fs = require("fs");
const http = require("http");
const os = require("os");
const path = require("path");
const crypto = require("crypto");
const { WebSocketServer } = require("ws");
const qrcode = require("qrcode-terminal");

const PORT = Number(process.env.PORT || 3000);
const DATA_DIR = path.join(__dirname, ".data");
const KEY_FILE = path.join(DATA_DIR, "history.key");
const HISTORY_FILE = path.join(DATA_DIR, "history.json");
const MAX_HISTORY_ITEMS = Number(process.env.MAX_HISTORY_ITEMS || 500);

const app = express();
app.use(express.json({ limit: "1mb" }));
app.use(express.urlencoded({ extended: false, limit: "1mb" }));
app.use(express.static(path.join(__dirname, "public")));

app.post("/share", (req, res) => {
  const parts = [req.body.title, req.body.text, req.body.url]
    .map((value) => String(value || "").trim())
    .filter(Boolean);
  const text = parts.join("\n");

  res.redirect(`/share.html?text=${encodeURIComponent(text)}`);
});

app.post("/api/clip", (req, res) => {
  const text = String(req.body.text || "").slice(0, 100_000);
  if (!text.trim()) {
    res.status(400).json({ error: "empty_text" });
    return;
  }

  const clip = createClip({
    text,
    fromId: "http-api",
    fromName: String(req.body.fromName || "Android").slice(0, 40),
  });

  broadcast({ type: "clip", clip });
  res.status(201).json({ clip });
});

app.get("/api/history", (req, res) => {
  const limit = Math.min(Number(req.query.limit || 100), MAX_HISTORY_ITEMS);
  const query = String(req.query.q || "").trim().toLowerCase();
  const from = String(req.query.from || "").trim();
  const items = loadHistory()
    .map((entry) => {
      const text = decryptText(entry.payload);
      return { entry, text };
    })
    .filter(({ entry, text }) => {
      const matchesDevice = !from || entry.fromName === from;
      const matchesQuery =
        !query ||
        text.toLowerCase().includes(query) ||
        entry.fromName.toLowerCase().includes(query);
      return matchesDevice && matchesQuery;
    })
    .reverse()
    .slice(0, limit)
    .map(({ entry, text }) => {
      return {
        id: entry.id,
        fromName: entry.fromName,
        createdAt: entry.createdAt,
        preview: text.length > 180 ? `${text.slice(0, 180)}...` : text,
        length: text.length,
      };
    });

  res.json({ items });
});

app.get("/api/history/devices", (req, res) => {
  const devices = Array.from(
    new Set(loadHistory().map((entry) => entry.fromName).filter(Boolean))
  ).sort((a, b) => a.localeCompare(b));

  res.json({ devices });
});

app.get("/api/latest", (req, res) => {
  res.json({ clip: latestClip });
});

app.get("/api/history/:id", (req, res) => {
  const entry = loadHistory().find((item) => item.id === req.params.id);
  if (!entry) {
    res.status(404).json({ error: "not_found" });
    return;
  }

  res.json({
    id: entry.id,
    fromName: entry.fromName,
    createdAt: entry.createdAt,
    text: decryptText(entry.payload),
  });
});

app.delete("/api/history", (req, res) => {
  saveHistory([]);
  latestClip = null;
  broadcast({ type: "history-cleared" });
  res.status(204).end();
});

const server = http.createServer(app);
const wss = new WebSocketServer({ server });
const clients = new Map();
let latestClip = null;
let encryptionKey = null;

function ensureDataFiles() {
  fs.mkdirSync(DATA_DIR, { recursive: true });

  if (!fs.existsSync(KEY_FILE)) {
    fs.writeFileSync(KEY_FILE, crypto.randomBytes(32).toString("base64"), {
      mode: 0o600,
    });
  }

  if (!fs.existsSync(HISTORY_FILE)) {
    fs.writeFileSync(HISTORY_FILE, "[]");
  }
}

function getEncryptionKey() {
  if (!encryptionKey) {
    ensureDataFiles();
    encryptionKey = Buffer.from(fs.readFileSync(KEY_FILE, "utf8").trim(), "base64");
  }

  return encryptionKey;
}

function encryptText(text) {
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv("aes-256-gcm", getEncryptionKey(), iv);
  const encrypted = Buffer.concat([cipher.update(text, "utf8"), cipher.final()]);

  return {
    alg: "aes-256-gcm",
    iv: iv.toString("base64"),
    tag: cipher.getAuthTag().toString("base64"),
    data: encrypted.toString("base64"),
  };
}

function decryptText(payload) {
  const decipher = crypto.createDecipheriv(
    "aes-256-gcm",
    getEncryptionKey(),
    Buffer.from(payload.iv, "base64")
  );
  decipher.setAuthTag(Buffer.from(payload.tag, "base64"));

  return Buffer.concat([
    decipher.update(Buffer.from(payload.data, "base64")),
    decipher.final(),
  ]).toString("utf8");
}

function loadHistory() {
  ensureDataFiles();

  try {
    const parsed = JSON.parse(fs.readFileSync(HISTORY_FILE, "utf8"));
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function saveHistory(items) {
  ensureDataFiles();
  fs.writeFileSync(HISTORY_FILE, JSON.stringify(items, null, 2));
}

function rememberClip(clip) {
  const history = loadHistory();
  history.push({
    id: clip.id,
    fromName: clip.fromName,
    createdAt: clip.createdAt,
    payload: encryptText(clip.text),
  });

  saveHistory(history.slice(-MAX_HISTORY_ITEMS));
}

function createClip({ text, fromId, fromName }) {
  latestClip = {
    id: crypto.randomUUID(),
    text,
    fromId,
    fromName,
    createdAt: Date.now(),
  };

  rememberClip(latestClip);
  return latestClip;
}

function getLanAddresses() {
  const interfaces = os.networkInterfaces();
  const addresses = [];

  for (const network of Object.values(interfaces)) {
    for (const item of network || []) {
      if (item.family === "IPv4" && !item.internal) {
        addresses.push(item.address);
      }
    }
  }

  return addresses;
}

function send(ws, payload) {
  if (ws.readyState === ws.OPEN) {
    ws.send(JSON.stringify(payload));
  }
}

function broadcast(payload, exceptId) {
  for (const [id, client] of clients.entries()) {
    if (id !== exceptId) {
      send(client.ws, payload);
    }
  }
}

function publishPresence() {
  const devices = Array.from(clients.values()).map((client) => ({
    id: client.id,
    name: client.name,
    connectedAt: client.connectedAt,
  }));

  broadcast({ type: "presence", devices });
}

wss.on("connection", (ws, req) => {
  const id = crypto.randomUUID();
  const ip = req.socket.remoteAddress || "unknown";
  const client = {
    id,
    ws,
    name: `Device ${clients.size + 1}`,
    connectedAt: Date.now(),
    ip,
  };

  clients.set(id, client);

  send(ws, {
    type: "welcome",
    id,
    latestClip,
  });
  publishPresence();

  ws.on("message", (raw) => {
    let message;
    try {
      message = JSON.parse(raw.toString());
    } catch {
      send(ws, { type: "error", message: "Tin nhan khong hop le." });
      return;
    }

    if (message.type === "hello") {
      client.name = String(message.name || client.name).slice(0, 40);
      publishPresence();
      return;
    }

    if (message.type === "clip") {
      const text = String(message.text || "").slice(0, 100_000);
      if (!text.trim()) return;

      latestClip = createClip({
        text,
        fromId: id,
        fromName: client.name,
      });

      broadcast({ type: "clip", clip: latestClip }, id);
      send(ws, { type: "sent", clip: latestClip });
    }
  });

  ws.on("close", () => {
    clients.delete(id);
    publishPresence();
  });
});

server.listen(PORT, "0.0.0.0", () => {
  const addresses = getLanAddresses();
  const localUrl = `http://localhost:${PORT}`;

  console.log("");
  console.log("LAN CopyPaste dang chay:");
  console.log(`- May nay: ${localUrl}`);
  for (const address of addresses) {
    const url = `http://${address}:${PORT}`;
    console.log(`- LAN: ${url}`);
  }

  if (addresses[0]) {
    console.log("");
    console.log("Quet QR tren dien thoai cung Wi-Fi:");
    qrcode.generate(`http://${addresses[0]}:${PORT}`, { small: true });
  }
});
