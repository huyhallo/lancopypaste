const statusEl = document.querySelector("#status");
const sendForm = document.querySelector("#sendForm");
const clipInput = document.querySelector("#clipInput");
const pasteBtn = document.querySelector("#pasteBtn");
const copyBtn = document.querySelector("#copyBtn");
const clearBtn = document.querySelector("#clearBtn");
const installBtn = document.querySelector("#installBtn");
const receivedText = document.querySelector("#receivedText");
const receivedMeta = document.querySelector("#receivedMeta");
const devicesEl = document.querySelector("#devices");
const deviceNameEl = document.querySelector("#deviceName");
const historyList = document.querySelector("#historyList");
const historyRefreshBtn = document.querySelector("#historyRefreshBtn");
const historyClearBtn = document.querySelector("#historyClearBtn");
const historySearch = document.querySelector("#historySearch");
const historyDevice = document.querySelector("#historyDevice");
const historyLimit = document.querySelector("#historyLimit");

const deviceName =
  localStorage.getItem("lan-copypaste-name") ||
  `${navigator.platform || "Device"} ${Math.floor(Math.random() * 900 + 100)}`;

localStorage.setItem("lan-copypaste-name", deviceName);
deviceNameEl.textContent = `Tên thiết bị của bạn: ${deviceName}`;

let socket;
let myId = null;
let latestText = "";
let deferredInstallPrompt = null;
let historyDebounce = null;

function setStatus(kind, text) {
  statusEl.className = `status ${kind}`;
  statusEl.querySelector("span:last-child").textContent = text;
}

function connect() {
  const protocol = location.protocol === "https:" ? "wss" : "ws";
  socket = new WebSocket(`${protocol}://${location.host}`);

  socket.addEventListener("open", () => {
    setStatus("online", "Đã kết nối");
    socket.send(JSON.stringify({ type: "hello", name: deviceName }));
    sendSharedTextFromUrl();
  });

  socket.addEventListener("close", () => {
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
    }

    if (message.type === "clip" || message.type === "sent") {
      showClip(message.clip);
    }

    if (message.type === "history-cleared") {
      resetReceived();
      loadHistory();
      loadHistoryDevices();
    }
  });
}

function renderDevices(devices) {
  devicesEl.innerHTML = "";

  for (const device of devices) {
    const li = document.createElement("li");
    li.textContent = device.id === myId ? `${device.name} (bạn)` : device.name;
    devicesEl.appendChild(li);
  }
}

function showClip(clip) {
  latestText = clip.text;
  receivedText.textContent = clip.text;
  receivedMeta.textContent = `Từ ${clip.fromName} lúc ${new Date(
    clip.createdAt
  ).toLocaleTimeString()}`;
  copyBtn.disabled = false;
  loadHistory();
  loadHistoryDevices();
}

function resetReceived() {
  latestText = "";
  receivedText.textContent = "Chưa có nội dung nào.";
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
  clipInput.focus();
});

copyBtn.addEventListener("click", async () => {
  if (!latestText) return;
  await navigator.clipboard.writeText(latestText);
  copyBtn.textContent = "Đã sao chép";
  setTimeout(() => {
    copyBtn.textContent = "Sao chép";
  }, 1000);
});

async function loadHistory() {
  if (!historyList) return;

  try {
    const params = new URLSearchParams({
      limit: historyLimit?.value || "80",
    });
    if (historySearch?.value.trim()) params.set("q", historySearch.value.trim());
    if (historyDevice?.value) params.set("from", historyDevice.value);

    const response = await fetch(`/api/history?${params.toString()}`);
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
      meta.textContent = `${item.fromName} - ${new Date(
        item.createdAt
      ).toLocaleString()} - ${item.length} ký tự`;

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
  const response = await fetch("/api/history/devices");
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

  const response = await fetch(`/api/history/${item.dataset.id}`);
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
historyLimit?.addEventListener("change", loadHistory);

historyClearBtn?.addEventListener("click", async () => {
  if (!confirm("Xóa toàn bộ lịch sử đã lưu trên máy server?")) return;

  await fetch("/api/history", { method: "DELETE" });
  resetReceived();
  await loadHistoryDevices();
  loadHistory();
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

connect();
loadHistory();
loadHistoryDevices();
