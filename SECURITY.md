# Security Policy

## Supported Versions

This project is an early LAN-first utility. Security fixes are expected on the `main` branch.

## Reporting A Vulnerability

Please do not open a public issue with sensitive exploit details. Contact the repository owner privately first, then provide:

- Affected version or commit.
- Steps to reproduce.
- Impact and affected data.
- Suggested mitigation if known.

## Security Model

LAN CopyPaste assumes the local network is trusted. The server should not be exposed directly to the public Internet.

Current protections:

- Clipboard history is encrypted at rest with `AES-256-GCM`.
- Encryption key and history live under `.data/`, which is ignored by git.
- Android and web clients talk to the server over LAN HTTP/WebSocket by default.

Known limitations:

- No user authentication yet.
- No end-to-end encryption between devices yet.
- Anyone who can reach the server port on the LAN can attempt to send clipboard data.
- Anyone with both `.data/history.json` and `.data/history.key` can decrypt local history.

Recommended usage:

- Run only on trusted private networks.
- Keep Windows Firewall restricted to private LAN profiles.
- Do not expose port `3000` to the Internet.
- Avoid syncing passwords, OTPs, private keys, API tokens or other secrets.
