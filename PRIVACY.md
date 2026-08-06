# Privacy

LAN CopyPaste is designed to run locally on your own machine and LAN.

## Data Collected

The app does not send clipboard data to a third-party cloud service.

The local server stores clipboard history in:

```text
.data/history.json
```

The encryption key is stored in:

```text
.data/history.key
```

## Data Retention

History remains on the server machine until you delete it from the web UI or remove `.data/history.json`.

## Data Sharing

Clipboard data is broadcast to connected LAN clients. Only run the server on networks and devices you trust.

## Sensitive Data

Clipboard content often contains secrets. Avoid sending passwords, OTP codes, private keys, recovery phrases, API keys and confidential documents through this tool.
