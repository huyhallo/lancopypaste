const express = require("express");
const fs = require("fs");
const http = require("http");
const os = require("os");
const path = require("path");
const crypto = require("crypto");
const { WebSocketServer } = require("ws");
const qrcode = require("qrcode-terminal");
const QRCode = require("qrcode");

const PORT = Number(process.env.PORT || 3000);
const DATA_DIR = path.join(__dirname, ".data");
const KEY_FILE = path.join(DATA_DIR, "history.key");
const HISTORY_FILE = path.join(DATA_DIR, "history.json");
const TRASH_FILE = path.join(DATA_DIR, "history-trash.json");
const AUTH_FILE = path.join(DATA_DIR, "auth.json");
const BLOB_DIR = path.join(DATA_DIR, "blobs");
const MAX_HISTORY_ITEMS = Number(process.env.MAX_HISTORY_ITEMS || 500);
const MAX_IMAGE_BYTES = Number(process.env.MAX_IMAGE_BYTES || 8 * 1024 * 1024);

const app = express();
app.use(express.json({ limit: "12mb" }));
app.use(express.urlencoded({ extended: false, limit: "1mb" }));
app.use(express.static(path.join(__dirname, "public")));

app.get("/api/discovery", (req, res) => {
  res.json({
    name: "LAN CopyPaste",
    port: PORT,
    addresses: getLanAddresses(),
    authRequired: true,
  });
});

app.get("/api/pairing", (req, res) => {
  const pairing = getPairingCode();
  res.json({
    code: pairing.code,
    expiresAt: pairing.expiresAt,
    pairingUrl: `${getPrimaryLanUrl()}/?pair=${pairing.code}`,
  });
});

app.post("/api/pairing/refresh", (req, res) => {
  if (!isHostRequest(req)) {
    res.status(403).json({ error: "host_only" });
    return;
  }

  const pairing = resetPairingCode();
  res.json({
    code: pairing.code,
    expiresAt: pairing.expiresAt,
    pairingUrl: `${getPrimaryLanUrl()}/?pair=${pairing.code}`,
  });
});

app.post("/api/pairing/extend", (req, res) => {
  if (!isHostRequest(req)) {
    res.status(403).json({ error: "host_only" });
    return;
  }

  const pairing = extendPairingCode();
  res.json({
    code: pairing.code,
    expiresAt: pairing.expiresAt,
    pairingUrl: `${getPrimaryLanUrl()}/?pair=${pairing.code}`,
  });
});

app.get("/api/pairing/qr", async (req, res) => {
  const pairing = getPairingCode();
  const url = `${getPrimaryLanUrl()}/?pair=${pairing.code}`;
  const png = await QRCode.toBuffer(url, { type: "png", margin: 1, width: 280 });
  res.type("png").send(png);
});

app.post("/api/pair", (req, res) => {
  const code = String(req.body.code || "").trim();
  const deviceName = String(req.body.deviceName || "Device").slice(0, 40);
  const auth = loadAuth();

  if (!auth.pairing || auth.pairing.code !== code || Date.now() > auth.pairing.expiresAt) {
    res.status(401).json({ error: "invalid_or_expired_code" });
    return;
  }

  const token = crypto.randomBytes(32).toString("base64url");
  const pairedIp = getRequestIp(req);
  const existing = auth.devices.find((device) => getDeviceKey(device) === getDeviceKey({ name: deviceName, pairedIp }));
  const record = {
    id: existing?.id || crypto.randomUUID(),
    tokenHash: hashToken(token),
    name: deviceName,
    createdAt: existing?.createdAt || Date.now(),
    updatedAt: Date.now(),
    pairedIp,
  };

  if (existing) {
    Object.assign(existing, record);
  } else {
    auth.devices.push(record);
  }

  auth.pairing = null;
  saveAuth({ ...auth, devices: dedupeDevices(auth.devices) });

  res.status(201).json({ token });
});

app.use("/api", (req, res, next) => {
  if (req.path === "/discovery" || req.path === "/pairing" || req.path === "/pairing/qr" || req.path === "/pair") {
    next();
    return;
  }

  if (isHostRequest(req) && (req.path === "/history" || req.path.startsWith("/history/trash"))) {
    next();
    return;
  }

  if (!isAuthorized(req)) {
    res.status(401).json({ error: "pairing_required" });
    return;
  }

  next();
});

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
    kind: "text",
    text,
    fromId: "http-api",
    fromName: String(req.body.fromName || "Android").slice(0, 40),
  });

  broadcast({ type: "clip", clip });
  res.status(201).json({ clip });
});

app.post("/api/clip/image", (req, res) => {
  const dataUrl = String(req.body.dataUrl || "");
  const fromName = String(req.body.fromName || "Web").slice(0, 40);
  const mimeType = String(req.body.mimeType || "image/png").slice(0, 80);

  if (!mimeType.startsWith("image/")) {
    res.status(400).json({ error: "invalid_image_type" });
    return;
  }

  const base64 = dataUrl.includes(",") ? dataUrl.split(",").pop() : dataUrl;
  let buffer;
  try {
    buffer = Buffer.from(base64, "base64");
  } catch {
    res.status(400).json({ error: "invalid_image_data" });
    return;
  }

  if (!buffer.length || buffer.length > MAX_IMAGE_BYTES) {
    res.status(400).json({ error: "invalid_image_size", maxBytes: MAX_IMAGE_BYTES });
    return;
  }

  const clip = createClip({
    kind: "image",
    imageBuffer: buffer,
    mimeType,
    fromId: "http-api",
    fromName,
  });

  broadcast({ type: "clip", clip });
  res.status(201).json({ clip });
});

app.get("/api/history", (req, res) => {
  const limit = Math.min(Number(req.query.limit || 100), MAX_HISTORY_ITEMS);
  const query = String(req.query.q || "").trim().toLowerCase();
  const from = String(req.query.from || "").trim();
  const kind = String(req.query.kind || "").trim();
  const items = loadHistory()
    .map((entry) => ({ entry, text: getEntrySearchText(entry) }))
    .filter(({ entry, text }) => {
      const matchesDevice = !from || entry.fromName === from;
      const matchesKind = !kind || getEntryKind(entry) === kind;
      const matchesQuery =
        !query ||
        text.toLowerCase().includes(query) ||
        entry.fromName.toLowerCase().includes(query);
      return matchesDevice && matchesKind && matchesQuery;
    })
    .reverse()
    .slice(0, limit)
    .map(({ entry, text }) => {
      return {
        id: entry.id,
        kind: getEntryKind(entry),
        fromName: entry.fromName,
        createdAt: entry.createdAt,
        preview: getEntryPreview(entry, text),
        length: entry.length || text.length,
        mimeType: entry.mimeType,
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

app.get("/api/devices", (req, res) => {
  const auth = loadAuth();
  const deduped = dedupeDevices(auth.devices);
  if (deduped.length !== auth.devices.length) {
    saveAuth({ ...auth, devices: deduped });
  }

  const devices = deduped.map((device) => {
    const online = Array.from(clients.values()).find((client) => client.tokenHash === device.tokenHash);
    const apiOnline = device.status === "online";
    return {
      id: device.id || device.tokenHash.slice(0, 12),
      name: online?.name || device.name,
      pairedAt: device.createdAt,
      updatedAt: device.updatedAt || device.createdAt,
      pairedIp: device.pairedIp || "",
      online: Boolean(online) || apiOnline,
      connectedAt: online?.connectedAt,
      ip: online?.ip || device.lastIp || "",
      statusUpdatedAt: device.statusUpdatedAt || null,
      statusSource: online ? "websocket" : device.statusSource || "",
    };
  });

  res.json({ devices });
});

app.post("/api/device/status", (req, res) => {
  const actor = getAuthorizedDevice(req);
  if (!actor) {
    res.status(401).json({ error: "pairing_required" });
    return;
  }

  const online = Boolean(req.body.online);
  const auth = loadAuth();
  const device = auth.devices.find((item) => item.tokenHash === actor.tokenHash);
  if (!device) {
    res.status(404).json({ error: "device_not_found" });
    return;
  }

  device.status = online ? "online" : "offline";
  device.statusUpdatedAt = Date.now();
  device.statusSource = String(req.body.source || "android").slice(0, 40);
  device.lastIp = getRequestIp(req);
  saveAuth(auth);

  broadcast({ type: "device-status" });
  res.json({ ok: true, online });
});

app.get("/api/latest", (req, res) => {
  res.json({ clip: latestClip });
});

app.get("/api/history/trash", (req, res) => {
  if (!isHostRequest(req)) {
    res.status(403).json({ error: "host_only" });
    return;
  }

  const batches = loadTrash()
    .slice()
    .reverse()
    .map((batch) => ({
      id: batch.id,
      deletedAt: batch.deletedAt,
      deletedBy: batch.deletedBy,
      count: batch.count || batch.items?.length || 0,
    }));

  res.json({ batches });
});

app.get("/api/history/trash/:batchId", (req, res) => {
  if (!isHostRequest(req)) {
    res.status(403).json({ error: "host_only" });
    return;
  }

  const batch = loadTrash().find((item) => item.id === req.params.batchId);
  if (!batch) {
    res.status(404).json({ error: "not_found" });
    return;
  }

  const items = (batch.items || [])
    .slice()
    .reverse()
    .map((entry) => {
      const text = getEntrySearchText(entry);
      return {
        id: entry.id,
        kind: getEntryKind(entry),
        fromName: entry.fromName,
        createdAt: entry.createdAt,
        preview: getEntryPreview(entry, text),
        length: entry.length || text.length,
        mimeType: entry.mimeType,
      };
    });

  res.json({
    id: batch.id,
    deletedAt: batch.deletedAt,
    deletedBy: batch.deletedBy,
    count: batch.count || batch.items?.length || 0,
    items,
  });
});

app.get("/api/history/trash/:batchId/items/:itemId", (req, res) => {
  if (!isHostRequest(req)) {
    res.status(403).json({ error: "host_only" });
    return;
  }

  const batch = loadTrash().find((item) => item.id === req.params.batchId);
  const entry = batch?.items?.find((item) => item.id === req.params.itemId);
  if (!entry) {
    res.status(404).json({ error: "not_found" });
    return;
  }

  res.json({
    id: entry.id,
    kind: getEntryKind(entry),
    fromName: entry.fromName,
    createdAt: entry.createdAt,
    text: entry.payload ? decryptText(entry.payload) : undefined,
    dataUrl: entry.blobFile ? readImageDataUrl(entry) : undefined,
    mimeType: entry.mimeType,
    length: entry.length,
  });
});

app.get("/api/history/:id", (req, res) => {
  const entry = loadHistory().find((item) => item.id === req.params.id);
  if (!entry) {
    res.status(404).json({ error: "not_found" });
    return;
  }

  res.json({
    id: entry.id,
    kind: getEntryKind(entry),
    fromName: entry.fromName,
    createdAt: entry.createdAt,
    text: entry.payload ? decryptText(entry.payload) : undefined,
    dataUrl: entry.blobFile ? readImageDataUrl(entry) : undefined,
    mimeType: entry.mimeType,
    length: entry.length,
  });
});

app.delete("/api/history", (req, res) => {
  if (!isHostRequest(req)) {
    res.status(403).json({ error: "host_only" });
    return;
  }

  const items = loadHistory();
  if (items.length) {
    const actor = getAuthorizedDevice(req);
    const trash = loadTrash();
    trash.push({
      id: crypto.randomUUID(),
      deletedAt: Date.now(),
      deletedBy: actor
        ? { id: actor.id || "", name: actor.name || "Thiết bị đã ghép nối", ip: getRequestIp(req) }
        : { id: "host-browser", name: "Máy chủ", ip: getRequestIp(req) },
      count: items.length,
      items,
    });
    saveTrash(trash);
  }

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
  fs.mkdirSync(BLOB_DIR, { recursive: true });

  if (!fs.existsSync(KEY_FILE)) {
    fs.writeFileSync(KEY_FILE, crypto.randomBytes(32).toString("base64"), {
      mode: 0o600,
    });
  }

  if (!fs.existsSync(HISTORY_FILE)) {
    fs.writeFileSync(HISTORY_FILE, "[]");
  }

  if (!fs.existsSync(TRASH_FILE)) {
    fs.writeFileSync(TRASH_FILE, "[]");
  }

  if (!fs.existsSync(AUTH_FILE)) {
    fs.writeFileSync(AUTH_FILE, JSON.stringify({ devices: [], pairing: null }, null, 2));
  }
}

function migrateAuthDevices() {
  const auth = loadAuth();
  const deduped = dedupeDevices(auth.devices);
  if (deduped.length !== auth.devices.length) {
    saveAuth({ ...auth, devices: deduped });
    console.log(`Da gop ${auth.devices.length - deduped.length} thiet bi ghep noi trung.`);
  }
}

function loadAuth() {
  ensureDataFiles();
  try {
    const auth = JSON.parse(fs.readFileSync(AUTH_FILE, "utf8"));
    return {
      devices: Array.isArray(auth.devices) ? auth.devices : [],
      pairing: auth.pairing || null,
    };
  } catch {
    return { devices: [], pairing: null };
  }
}

function saveAuth(auth) {
  ensureDataFiles();
  fs.writeFileSync(AUTH_FILE, JSON.stringify(auth, null, 2));
}

function getDeviceKey(device) {
  const name = String(device.name || "Device").trim().toLowerCase();
  const ip = String(device.pairedIp || "").trim();
  return `${name}|${ip}`;
}

function dedupeDevices(devices) {
  const grouped = new Map();

  for (const device of devices) {
    const normalized = {
      id: device.id || crypto.randomUUID(),
      tokenHash: device.tokenHash,
      name: device.name || "Device",
      createdAt: device.createdAt || device.updatedAt || Date.now(),
      updatedAt: device.updatedAt || device.createdAt || Date.now(),
      pairedIp: device.pairedIp || "",
      status: device.status || "offline",
      statusUpdatedAt: device.statusUpdatedAt || null,
      statusSource: device.statusSource || "",
      lastIp: device.lastIp || "",
    };
    const key = getDeviceKey(normalized);
    const existing = grouped.get(key);
    if (!existing || (normalized.updatedAt || 0) > (existing.updatedAt || 0)) {
      grouped.set(key, {
        ...normalized,
        createdAt: Math.min(existing?.createdAt || normalized.createdAt, normalized.createdAt),
        status: newerStatus(existing, normalized).status,
        statusUpdatedAt: newerStatus(existing, normalized).statusUpdatedAt,
        statusSource: newerStatus(existing, normalized).statusSource,
        lastIp: newerStatus(existing, normalized).lastIp,
      });
    }
  }

  return Array.from(grouped.values());
}

function newerStatus(a, b) {
  if (!a) return b;
  return (b.statusUpdatedAt || 0) >= (a.statusUpdatedAt || 0) ? b : a;
}

function hashToken(token) {
  return crypto.createHash("sha256").update(token).digest("base64url");
}

function isAuthorized(req) {
  const token = req.get("x-device-token") || req.query.token;
  if (!token) return false;
  return Boolean(getAuthorizedDevice(req));
}

function getAuthorizedDevice(req) {
  const token = req.get("x-device-token") || req.query.token;
  if (!token) return null;
  const tokenHash = hashToken(token);
  return loadAuth().devices.find((device) => device.tokenHash === tokenHash) || null;
}

function isHostRequest(req) {
  const host = String(req.hostname || "").toLowerCase();
  const remoteIp = getRequestIp(req);
  return host === "localhost" || host === "127.0.0.1" || host === "::1" || remoteIp === "127.0.0.1";
}

function getRequestIp(req) {
  return String(req.ip || req.socket?.remoteAddress || "")
    .replace(/^::ffff:/, "")
    .replace(/^::1$/, "127.0.0.1");
}

function getPairingCode() {
  const auth = loadAuth();
  if (!auth.pairing || Date.now() > auth.pairing.expiresAt) {
    auth.pairing = {
      code: String(crypto.randomInt(100000, 999999)),
      expiresAt: Date.now() + 5 * 60 * 1000,
    };
    saveAuth(auth);
  }
  return auth.pairing;
}

function resetPairingCode() {
  const auth = loadAuth();
  auth.pairing = {
    code: String(crypto.randomInt(100000, 999999)),
    expiresAt: Date.now() + 5 * 60 * 1000,
  };
  saveAuth(auth);
  return auth.pairing;
}

function extendPairingCode() {
  const auth = loadAuth();
  const current = getPairingCode();
  auth.pairing = {
    code: current.code,
    expiresAt: Date.now() + 5 * 60 * 1000,
  };
  saveAuth(auth);
  return auth.pairing;
}

function getPrimaryLanUrl() {
  const address = getLanAddresses().find((item) => item.startsWith("192.168.")) || getLanAddresses()[0] || "localhost";
  return `http://${address}:${PORT}`;
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

function encryptBuffer(buffer) {
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv("aes-256-gcm", getEncryptionKey(), iv);
  const encrypted = Buffer.concat([cipher.update(buffer), cipher.final()]);

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

function decryptBuffer(payload) {
  const decipher = crypto.createDecipheriv(
    "aes-256-gcm",
    getEncryptionKey(),
    Buffer.from(payload.iv, "base64")
  );
  decipher.setAuthTag(Buffer.from(payload.tag, "base64"));

  return Buffer.concat([
    decipher.update(Buffer.from(payload.data, "base64")),
    decipher.final(),
  ]);
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

function loadTrash() {
  ensureDataFiles();

  try {
    const parsed = JSON.parse(fs.readFileSync(TRASH_FILE, "utf8"));
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function saveTrash(items) {
  ensureDataFiles();
  fs.writeFileSync(TRASH_FILE, JSON.stringify(items, null, 2));
}

function rememberClip(clip) {
  const history = loadHistory();
  const entry = {
    id: clip.id,
    kind: clip.kind,
    fromName: clip.fromName,
    createdAt: clip.createdAt,
  };

  if (clip.kind === "image") {
    const blobFile = `${clip.id}.json`;
    fs.writeFileSync(
      path.join(BLOB_DIR, blobFile),
      JSON.stringify(encryptBuffer(clip.imageBuffer), null, 2)
    );
    entry.blobFile = blobFile;
    entry.mimeType = clip.mimeType;
    entry.length = clip.imageBuffer.length;
    entry.width = clip.width;
    entry.height = clip.height;
  } else {
    entry.payload = encryptText(clip.text);
  }

  history.push(entry);

  saveHistory(history.slice(-MAX_HISTORY_ITEMS));
}

function createClip({ kind = "text", text = "", imageBuffer, mimeType, fromId, fromName }) {
  latestClip = {
    id: crypto.randomUUID(),
    kind,
    text,
    fromId,
    fromName,
    createdAt: Date.now(),
  };

  if (kind === "image") {
    latestClip.text = "";
    latestClip.mimeType = mimeType;
    latestClip.length = imageBuffer.length;
    latestClip.dataUrl = `data:${mimeType};base64,${imageBuffer.toString("base64")}`;
    latestClip.imageBuffer = imageBuffer;
  }

  rememberClip(latestClip);
  delete latestClip.imageBuffer;
  return latestClip;
}

function getEntryKind(entry) {
  return entry.kind || "text";
}

function getEntrySearchText(entry) {
  if (getEntryKind(entry) === "image") {
    return `${entry.mimeType || "image"} ${entry.fromName || ""}`;
  }

  return decryptText(entry.payload);
}

function getEntryPreview(entry, text) {
  if (getEntryKind(entry) === "image") {
    const kb = Math.round((entry.length || 0) / 1024);
    return `Ảnh ${entry.mimeType || ""} - ${kb} KB`;
  }

  return text.length > 180 ? `${text.slice(0, 180)}...` : text;
}

function readImageDataUrl(entry) {
  const payload = JSON.parse(fs.readFileSync(path.join(BLOB_DIR, entry.blobFile), "utf8"));
  const buffer = decryptBuffer(payload);
  return `data:${entry.mimeType || "image/png"};base64,${buffer.toString("base64")}`;
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
    ip: client.ip,
  }));

  broadcast({ type: "presence", devices });
}

wss.on("connection", (ws, req) => {
  const token = new URL(req.url, "http://localhost").searchParams.get("token");
  const tokenHash = token ? hashToken(token) : "";
  if (!tokenHash || !loadAuth().devices.some((device) => device.tokenHash === tokenHash)) {
    ws.close(1008, "pairing required");
    return;
  }

  const id = crypto.randomUUID();
  const ip = getRequestIp({ socket: req.socket });
  const client = {
    id,
    ws,
    tokenHash,
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
        kind: "text",
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

migrateAuthDevices();

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
