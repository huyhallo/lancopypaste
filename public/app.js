const statusEl = document.querySelector("#status");
const sendForm = document.querySelector("#sendForm");
const clipInput = document.querySelector("#clipInput");
const pasteBtn = document.querySelector("#pasteBtn");
const chooseImageBtn = document.querySelector("#chooseImageBtn");
const imageInput = document.querySelector("#imageInput");
const sendImagePreview = document.querySelector("#sendImagePreview");
const copyBtn = document.querySelector("#copyBtn");
const clearBtn = document.querySelector("#clearBtn");
const installBtn = document.querySelector("#installBtn");
const receivedText = document.querySelector("#receivedText");
const receivedImage = document.querySelector("#receivedImage");
const receivedMeta = document.querySelector("#receivedMeta");
const devicesEl = document.querySelector("#devices");
const deviceNameEl = document.querySelector("#deviceName");
const historyList = document.querySelector("#historyList");
const historyRefreshBtn = document.querySelector("#historyRefreshBtn");
const historyClearBtn = document.querySelector("#historyClearBtn");
const historySearch = document.querySelector("#historySearch");
const historyDevice = document.querySelector("#historyDevice");
const historyKind = document.querySelector("#historyKind");
const historyLimit = document.querySelector("#historyLimit");
const tabBtns = document.querySelectorAll(".tabBtn");
const tabPanels = document.querySelectorAll(".tabPanel");
const hostPairingPanel = document.querySelector("#hostPairingPanel");
const hostPairingCode = document.querySelector("#hostPairingCode");
const hostPairingQr = document.querySelector("#hostPairingQr");
const hostPairingMeta = document.querySelector("#hostPairingMeta");
const refreshPairingBtn = document.querySelector("#refreshPairingBtn");
const extendPairingBtn = document.querySelector("#extendPairingBtn");
const pairedDevicesRefreshBtn = document.querySelector("#pairedDevicesRefreshBtn");
const pairedDeviceStatusFilter = document.querySelector("#pairedDeviceStatusFilter");
const pairedDevicesList = document.querySelector("#pairedDevicesList");
const trashPanel = document.querySelector("#trashPanel");
const trashRefreshBtn = document.querySelector("#trashRefreshBtn");
const trashList = document.querySelector("#trashList");
const pairingPanel = document.querySelector("#pairingPanel");
const pairCodeInput = document.querySelector("#pairCodeInput");
const pairBtn = document.querySelector("#pairBtn");
const pairQr = document.querySelector("#pairQr");
const pairMeta = document.querySelector("#pairMeta");

const deviceName =
  localStorage.getItem("lan-copypaste-name") ||
  `${navigator.platform || "Device"} ${Math.floor(Math.random() * 900 + 100)}`;

localStorage.setItem("lan-copypaste-name", deviceName);
if (deviceNameEl) deviceNameEl.textContent = `Tên thiết bị của bạn: ${deviceName}`;

let socket;
let myId = null;
let latestText = "";
let latestClip = null;
let selectedImage = null;
let deferredInstallPrompt = null;
let historyDebounce = null;
let deviceToken = localStorage.getItem("lan-copypaste-token") || "";

const isHostBrowser = ["localhost", "127.0.0.1", "::1"].includes(location.hostname);

function setStatus(kind, text) {
  statusEl.className = `status ${kind}`;
  statusEl.querySelector("span:last-child").textContent = text;
}

function connect() {
  if (!deviceToken) {
    showPairing();
    return;
  }

  const protocol = location.protocol === "https:" ? "wss" : "ws";
  socket = new WebSocket(`${protocol}://${location.host}?token=${encodeURIComponent(deviceToken)}`);

  socket.addEventListener("open", () => {
    setStatus("online", "Đã kết nối");
    socket.send(JSON.stringify({ type: "hello", name: deviceName }));
    sendSharedTextFromUrl();
  });

  socket.addEventListener("close", (event) => {
    if (event.code === 1008) {
      deviceToken = "";
      localStorage.removeItem("lan-copypaste-token");
      showPairing();
      return;
    }
    setStatus("offline", "Mất kết nối");
    setTimeout(connect, 1200);
  });

  socket.addEventListener("message", (event) => {
    const message = JSON.parse(event.data);

    if (message.type === "welcome") {
      myId = message.id;
      if (message.latestClip) showClip(message.latestClip);
    }

    if (message.type === "presence") {
      renderDevices(message.devices);
      loadPairedDevices();
    }

    if (message.type === "clip" || message.type === "sent") {
      showClip(message.clip);
    }

    if (message.type === "history-cleared") {
      resetReceived();
      loadHistory();
      loadHistoryDevices();
    }

    if (message.type === "device-status") {
      loadPairedDevices();
    }
  });
}

async function showPairing() {
  pairingPanel.hidden = false;
  setStatus("offline", "Cần ghép nối");
  try {
    const pairing = await loadPairingInfo();
    pairQr.src = "/api/pairing/qr";
    pairMeta.textContent = `Mã hết hạn lúc ${new Date(pairing.expiresAt).toLocaleTimeString()}`;
    const urlCode = new URLSearchParams(location.search).get("pair");
    if (urlCode) pairCodeInput.value = urlCode;
  } catch {
    pairMeta.textContent = "Không tải được mã ghép nối.";
  }
}

async function showHostPairing() {
  if (!isHostBrowser || !hostPairingPanel) return;

  hostPairingPanel.hidden = false;
  trashPanel.hidden = false;
  try {
    const pairing = await loadPairingInfo();
    hostPairingCode.textContent = pairing.code;
    hostPairingQr.src = `/api/pairing/qr?t=${pairing.expiresAt}`;
    hostPairingMeta.textContent = `Hết hạn lúc ${new Date(pairing.expiresAt).toLocaleTimeString()}`;
  } catch {
    hostPairingCode.textContent = "------";
    hostPairingMeta.textContent = "Không tải được mã ghép nối.";
  }
}

function loadPairingInfo() {
  return apiFetch("/api/pairing", { skipAuth: true }).then((res) => res.json());
}

async function updatePairing(endpoint) {
  const response = await apiFetch(endpoint, { method: "POST", skipAuth: true });
  if (!response.ok) {
    hostPairingMeta.textContent = "Chỉ máy chủ mới được làm mới mã.";
    return;
  }

  const pairing = await response.json();
  hostPairingCode.textContent = pairing.code;
  hostPairingQr.src = `/api/pairing/qr?t=${pairing.expiresAt}`;
  hostPairingMeta.textContent = `Hết hạn lúc ${new Date(pairing.expiresAt).toLocaleTimeString()}`;
}

async function pairDevice() {
  const code = pairCodeInput.value.trim();
  if (!code) return;

  const response = await apiFetch("/api/pair", {
    skipAuth: true,
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ code, deviceName }),
  });

  if (!response.ok) {
    pairMeta.textContent = "Mã không đúng hoặc đã hết hạn.";
    return;
  }

  const data = await response.json();
  deviceToken = data.token;
  localStorage.setItem("lan-copypaste-token", deviceToken);
  pairingPanel.hidden = true;
  history.replaceState({}, "", location.pathname);
  connect();
}

function renderDevices(devices) {
  devicesEl.innerHTML = "";

  for (const device of devices) {
    const li = document.createElement("li");
    li.textContent = `${device.id === myId ? `${device.name} (bạn)` : device.name}${device.ip ? ` - ${device.ip}` : ""}`;
    devicesEl.appendChild(li);
  }
}

async function loadPairedDevices() {
  if (!pairedDevicesList || !deviceToken) return;

  const response = await apiFetch("/api/devices");
  if (!response.ok) {
    pairedDevicesList.textContent = "Không tải được danh sách thiết bị.";
    return;
  }

  const data = await response.json();
  const statusFilter = pairedDeviceStatusFilter?.value || "";
  const filteredDevices = data.devices.filter((device) => {
    if (statusFilter === "online") return device.online;
    if (statusFilter === "offline") return !device.online;
    return true;
  });

  if (!data.devices.length) {
    pairedDevicesList.textContent = "Chưa có thiết bị nào được ghép nối.";
    return;
  }

  if (!filteredDevices.length) {
    pairedDevicesList.textContent = statusFilter === "online"
      ? "Không có thiết bị nào đang kết nối."
      : "Không có thiết bị nào chưa kết nối.";
    return;
  }

  pairedDevicesList.innerHTML = "";
  const summary = document.createElement("div");
  summary.className = "deviceSummary";
  summary.textContent = `${data.devices.filter((device) => device.online).length} đang kết nối - ${
    data.devices.filter((device) => !device.online).length
  } chưa kết nối`;
  pairedDevicesList.appendChild(summary);

  for (const device of filteredDevices) {
    const row = document.createElement("div");
    row.className = `deviceRow ${device.online ? "online" : "offline"}`;

    const title = document.createElement("strong");
    const stateDot = document.createElement("span");
    stateDot.className = "deviceStateDot";
    stateDot.title = device.online ? "Đang kết nối" : "Chưa kết nối";
    title.append(stateDot, document.createTextNode(device.name));

    const meta = document.createElement("span");
    meta.textContent = [
      device.ip ? `IP hiện tại: ${device.ip}` : "",
      device.pairedIp ? `IP ghép nối: ${device.pairedIp}` : "",
      device.pairedAt ? `Ghép nối: ${new Date(device.pairedAt).toLocaleString()}` : "",
      device.updatedAt && device.updatedAt !== device.pairedAt ? `Cập nhật: ${new Date(device.updatedAt).toLocaleString()}` : "",
    ].filter(Boolean).join(" - ");

    row.append(title, meta);
    pairedDevicesList.appendChild(row);
  }
}

function showClip(clip) {
  latestClip = clip;
  latestText = clip.text || "";
  receivedText.hidden = clip.kind === "image";
  receivedImage.hidden = clip.kind !== "image";

  if (clip.kind === "image") {
    receivedImage.src = clip.dataUrl;
    receivedText.textContent = "";
  } else {
    receivedText.textContent = clip.text;
    receivedImage.removeAttribute("src");
  }

  receivedMeta.textContent = `Từ ${clip.fromName} lúc ${new Date(
    clip.createdAt
  ).toLocaleTimeString()}${clip.kind === "image" ? " - ảnh" : ""}`;
  copyBtn.disabled = false;
  loadHistory();
  loadHistoryDevices();
}

function resetReceived() {
  latestText = "";
  latestClip = null;
  receivedText.textContent = "Chưa có nội dung nào.";
  receivedText.hidden = false;
  receivedImage.hidden = true;
  receivedImage.removeAttribute("src");
  receivedMeta.textContent = "";
  copyBtn.disabled = true;
  copyBtn.textContent = "Sao chép";
}

function sendSharedTextFromUrl() {
  const params = new URLSearchParams(location.search);
  const sharedText = params.get("text");
  if (!sharedText || socket.readyState !== WebSocket.OPEN) return;

  clipInput.value = sharedText;
  socket.send(JSON.stringify({ type: "clip", text: sharedText }));
  history.replaceState({}, "", location.pathname);
}

sendForm.addEventListener("submit", (event) => {
  event.preventDefault();
  if (selectedImage) {
    sendImage(selectedImage);
    return;
  }

  const text = clipInput.value;
  if (!text.trim() || socket.readyState !== WebSocket.OPEN) return;

  socket.send(JSON.stringify({ type: "clip", text }));
  clipInput.select();
});

pasteBtn.addEventListener("click", async () => {
  try {
    clipInput.value = await navigator.clipboard.readText();
    clipInput.focus();
  } catch {
    clipInput.focus();
  }
});

clearBtn.addEventListener("click", () => {
  clipInput.value = "";
  clearSelectedImage();
  clipInput.focus();
});

copyBtn.addEventListener("click", async () => {
  if (!latestClip) return;

  if (latestClip.kind === "image") {
    await copyImageToClipboard(latestClip.dataUrl, latestClip.mimeType);
  } else {
    await navigator.clipboard.writeText(latestText);
  }

  copyBtn.textContent = "Đã sao chép";
  setTimeout(() => {
    copyBtn.textContent = "Sao chép";
  }, 1000);
});

chooseImageBtn?.addEventListener("click", () => imageInput.click());

imageInput?.addEventListener("change", () => {
  const file = imageInput.files?.[0];
  if (!file) return;
  if (!file.type.startsWith("image/")) return;

  selectedImage = file;
  sendImagePreview.src = URL.createObjectURL(file);
  sendImagePreview.hidden = false;
});

async function sendImage(file) {
  const dataUrl = await fileToDataUrl(file);
  const response = await apiFetch("/api/clip/image", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      dataUrl,
      mimeType: file.type || "image/png",
      fromName: deviceName,
    }),
  });

  if (!response.ok) return;
  const data = await response.json();
  showClip(data.clip);
  clearSelectedImage();
}

function clearSelectedImage() {
  selectedImage = null;
  imageInput.value = "";
  sendImagePreview.hidden = true;
  sendImagePreview.removeAttribute("src");
}

function fileToDataUrl(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result);
    reader.onerror = reject;
    reader.readAsDataURL(file);
  });
}

async function copyImageToClipboard(dataUrl, mimeType = "image/png") {
  const blob = await (await fetch(dataUrl)).blob();
  await navigator.clipboard.write([
    new ClipboardItem({ [mimeType]: blob }),
  ]);
}

async function loadHistory() {
  if (!historyList) return;

  try {
    const params = new URLSearchParams({
      limit: historyLimit?.value || "80",
    });
    if (historySearch?.value.trim()) params.set("q", historySearch.value.trim());
    if (historyDevice?.value) params.set("from", historyDevice.value);
    if (historyKind?.value) params.set("kind", historyKind.value);

    const response = await apiFetch(`/api/history?${params.toString()}`);
    const data = await response.json();

    if (!data.items.length) {
      historyList.textContent = "Chưa có lịch sử.";
      return;
    }

    historyList.innerHTML = "";
    for (const item of data.items) {
      const button = document.createElement("button");
      button.type = "button";
      button.className = "historyItem";
      button.dataset.id = item.id;

      const title = document.createElement("span");
      title.className = "historyPreview";
      title.textContent = item.preview;

      const meta = document.createElement("span");
      meta.className = "historyMeta";
      meta.textContent = `${item.kind === "image" ? "Ảnh" : "Văn bản"} - ${item.fromName} - ${new Date(
        item.createdAt
      ).toLocaleString()} - ${
        item.kind === "image" ? `${Math.round(item.length / 1024)} KB` : `${item.length} ký tự`
      }`;

      button.append(title, meta);
      historyList.appendChild(button);
    }
  } catch {
    historyList.textContent = "Không tải được lịch sử.";
  }
}

async function loadHistoryDevices() {
  if (!historyDevice) return;

  const selected = historyDevice.value;
  const response = await apiFetch("/api/history/devices");
  if (!response.ok) return;

  const data = await response.json();
  historyDevice.innerHTML = `<option value="">Tất cả thiết bị</option>`;

  for (const device of data.devices) {
    const option = document.createElement("option");
    option.value = device;
    option.textContent = device;
    historyDevice.appendChild(option);
  }

  historyDevice.value = data.devices.includes(selected) ? selected : "";
}

historyList?.addEventListener("click", async (event) => {
  const item = event.target.closest(".historyItem");
  if (!item) return;

  const response = await apiFetch(`/api/history/${item.dataset.id}`);
  if (!response.ok) return;

  const clip = await response.json();
  showClip(clip);
  receivedText.scrollIntoView({ behavior: "smooth", block: "center" });
});

historyRefreshBtn?.addEventListener("click", loadHistory);

historySearch?.addEventListener("input", () => {
  clearTimeout(historyDebounce);
  historyDebounce = setTimeout(loadHistory, 180);
});

historyDevice?.addEventListener("change", loadHistory);
historyKind?.addEventListener("change", loadHistory);
historyLimit?.addEventListener("change", loadHistory);

historyClearBtn?.addEventListener("click", async () => {
  if (!confirm("Chuyển toàn bộ lịch sử vào kho lưu trữ trên máy chủ?")) return;

  const response = await apiFetch("/api/history", { method: "DELETE" });
  if (!response.ok) {
    alert("Chỉ web mở trên máy chủ mới được chuyển lịch sử vào lưu trữ.");
    return;
  }
  resetReceived();
  await loadHistoryDevices();
  await loadTrash();
  loadHistory();
});

async function loadTrash() {
  if (!trashList || !isHostBrowser) return;

  const response = await apiFetch("/api/history/trash");
  if (!response.ok) {
    trashList.textContent = "Không tải được kho lưu trữ.";
    return;
  }

  const data = await response.json();
  if (!data.batches.length) {
    trashList.textContent = "Chưa có lần lưu trữ nào.";
    return;
  }

  trashList.innerHTML = "";
  for (const batch of data.batches) {
    const item = document.createElement("button");
    item.type = "button";
    item.className = "trashItem";
    item.dataset.batchId = batch.id;
    item.textContent = `${batch.count} mục - ${new Date(batch.deletedAt).toLocaleString()} - ${batch.deletedBy?.name || "Không rõ"} - ${batch.deletedBy?.ip || "không có IP"}`;
    trashList.appendChild(item);
  }
}

trashList?.addEventListener("click", async (event) => {
  const archivedItem = event.target.closest(".archiveItem");
  if (archivedItem) {
    const response = await apiFetch(`/api/history/trash/${archivedItem.dataset.batchId}/items/${archivedItem.dataset.itemId}`);
    if (!response.ok) return;
    const clip = await response.json();
    showClip(clip);
    document.querySelector('[data-tab="main"]')?.click();
    return;
  }

  const batchButton = event.target.closest(".trashItem");
  if (!batchButton) return;

  const response = await apiFetch(`/api/history/trash/${batchButton.dataset.batchId}`);
  if (!response.ok) return;
  const batch = await response.json();

  const panel = document.createElement("div");
  panel.className = "archiveItems";
  if (!batch.items.length) {
    panel.textContent = "Lần lưu trữ này không có mục nào.";
  } else {
    for (const item of batch.items) {
      const button = document.createElement("button");
      button.type = "button";
      button.className = "archiveItem";
      button.dataset.batchId = batch.id;
      button.dataset.itemId = item.id;
      button.textContent = `${item.kind === "image" ? "Ảnh" : "Văn bản"} - ${item.fromName} - ${new Date(item.createdAt).toLocaleString()} - ${item.preview}`;
      panel.appendChild(button);
    }
  }

  const oldPanel = batchButton.nextElementSibling;
  if (oldPanel?.classList.contains("archiveItems")) {
    oldPanel.remove();
  } else {
    batchButton.after(panel);
  }
});

window.addEventListener("beforeinstallprompt", (event) => {
  event.preventDefault();
  deferredInstallPrompt = event;
  installBtn.hidden = false;
});

installBtn.addEventListener("click", async () => {
  if (!deferredInstallPrompt) return;
  deferredInstallPrompt.prompt();
  await deferredInstallPrompt.userChoice;
  deferredInstallPrompt = null;
  installBtn.hidden = true;
});

if ("serviceWorker" in navigator) {
  navigator.serviceWorker.register("/sw.js").catch(() => {});
}

function apiFetch(url, options = {}) {
  const headers = new Headers(options.headers || {});
  if (!options.skipAuth && deviceToken) {
    headers.set("x-device-token", deviceToken);
  }
  return fetch(url, { ...options, headers });
}

pairBtn?.addEventListener("click", pairDevice);
refreshPairingBtn?.addEventListener("click", () => updatePairing("/api/pairing/refresh"));
extendPairingBtn?.addEventListener("click", () => updatePairing("/api/pairing/extend"));
pairedDevicesRefreshBtn?.addEventListener("click", loadPairedDevices);
pairedDeviceStatusFilter?.addEventListener("change", loadPairedDevices);
trashRefreshBtn?.addEventListener("click", loadTrash);

tabBtns.forEach((button) => {
  button.addEventListener("click", () => {
    tabBtns.forEach((item) => item.classList.toggle("active", item === button));
    tabPanels.forEach((panel) => panel.classList.toggle("active", panel.id === `tab-${button.dataset.tab}`));

    if (button.dataset.tab === "pairing") {
      showHostPairing();
      loadPairedDevices();
    }
    if (button.dataset.tab === "history") {
      loadHistory();
      loadTrash();
    }
  });
});

showHostPairing();
connect();
if (deviceToken) {
  loadHistory();
  loadHistoryDevices();
  loadPairedDevices();
  loadTrash();
}
