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

const deviceName =
  localStorage.getItem("lan-copypaste-name") ||
  `${navigator.platform || "Device"} ${Math.floor(Math.random() * 900 + 100)}`;

localStorage.setItem("lan-copypaste-name", deviceName);
deviceNameEl.textContent = `Tên thiết bị của bạn: ${deviceName}`;

let socket;
let myId = null;
let latestText = "";
let latestClip = null;
let selectedImage = null;
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
  const response = await fetch("/api/clip/image", {
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
historyKind?.addEventListener("change", loadHistory);
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
