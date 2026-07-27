# Crypto — Reference

Covers what's in the skeleton and what to add per-app when you need more.

---

## What's in the skeleton

### AesGcmCipher — stateless

AES-256-GCM. Each call to `encrypt` generates a fresh 12-byte random nonce prepended to the
ciphertext. `decrypt` strips the nonce and decrypts. No state between calls.

Use when the symmetric key is established out-of-band: a pre-shared group key, or a key you
derived from a Noise handshake and stored separately.

Wire with `P2PSession`:
```kotlin
val key: ByteArray = // 32-byte symmetric key, pre-agreed
P2PSession(cipher = AesGcmCipher(key), ...)
```

### NoiseCipher — stateful (Noise_XX)

Implements `ICipher` but runs a three-message Noise_XX handshake on first use. After the handshake,
uses the derived session keys for all subsequent encrypt/decrypt. The handshake is invisible to
`P2PSession` — it just calls encrypt/decrypt.

**Noise_XX handshake**:

```
Alice → Bob:  E_a                             // Alice's ephemeral public key, cleartext
Bob → Alice:  E_b, AEAD(S_b, DH(e_b, E_a))   // Bob's ephemeral + static, encrypted
Alice → Bob:  AEAD(S_a, DH(e_a, E_b))        // Alice's static, encrypted under accumulated DH
--- handshake done ---
Both derive two session keys (send/receive) from the accumulated DH outputs.
```

Properties:
- **Mutual authentication**: both sides prove possession of their static keypair
- **Forward secrecy**: ephemeral keys are discarded after the handshake — compromising static keys
  later does not decrypt past sessions
- **Identity hiding**: Alice's static key is never sent in cleartext; Bob's is encrypted after the
  first DH exchange

What Noise_XX does NOT provide:
- Per-message forward secrecy (the Double Ratchet does this)
- Offline messaging (that needs X3DH prekeys)

Use `NoiseCipher` whenever both parties are online simultaneously.

```kotlin
val identity = Ed25519Identity(context.filesDir)
P2PSession(cipher = NoiseCipher(identity), ...)
```

### NoopCipher — no encryption

Returns plaintext unchanged. Use when the transport already encrypts — WebRTC DataChannel uses
DTLS-SRTP, encrypting again wastes CPU and adds overhead with no security benefit.

```kotlin
P2PSession(
    transport = WebRtcTransport(stunServers),
    cipher    = NoopCipher(),  // DTLS-SRTP handles it
    ...
)
```

### Ed25519Identity — the universal key

Generates an Ed25519 keypair on first run, stores it in `filesDir/identity.key`. The same keypair
serves three roles:

```
Ed25519 keypair
  ├── Nostr identity — pubkey = Nostr npub, signs relay events
  ├── Noise static key — used in Noise_XX handshake
  └── DID key — did:key:z<base58(pubkey)> — decentralised identity, no registry
```

---

## Signal / X3DH + Double Ratchet (add per-app when needed)

### When you need it

Both online → use Noise_XX, simpler and sufficient.

Offline messaging (send a message to someone who is not currently online) → need X3DH + Double
Ratchet. Without prekeys, you cannot establish a session with an offline peer.

### How X3DH works

Bob publishes a **prekey bundle** before going offline:
- `IK_B` — Bob's identity key (long-term Ed25519)
- `SPK_B` — Bob's signed prekey (medium-term, rotated weekly), signed by `IK_B`
- `OPK_B` — one-time prekeys (optional, consumed once)

Alice fetches the bundle and computes four DH values:
```
DH1 = DH(IK_A, SPK_B)
DH2 = DH(EK_A, IK_B)   // EK_A = ephemeral key Alice generates for this session
DH3 = DH(EK_A, SPK_B)
DH4 = DH(EK_A, OPK_B)  // optional
```

Master secret = `KDF(DH1 || DH2 || DH3 || DH4)`. Alice sends a first message to Bob containing
her identity key, ephemeral key, and the first encrypted message. Bob recomputes the same DH values
and derives the same master secret.

### Prekey distribution without a server

**Option 1 — Nostr event (recommended for personal apps)**

Bob publishes kind=10002 (or a custom kind) with his prekey bundle JSON, signed by his identity key.
Alice fetches it from any Nostr relay. One-time prekeys aren't truly single-use (multiple Alices can
fetch the same event) but signed prekeys rotate weekly, which is acceptable.

```json
{
  "kind": 10050,
  "pubkey": "<bob's npub>",
  "content": "{\"ik\":\"<base64>\",\"spk\":\"<base64>\",\"spk_sig\":\"<base64>\"}",
  "tags": [["t", "prekey-bundle"]]
}
```

**Option 2 — static file server**

Bob serves `https://example.com/keys/bob.json`. Alice fetches it. Bob rotates it weekly. Stateless,
no compute required. GitHub Pages works.

**Option 3 — per-app decision**

The skeleton documents the interface. Implement what makes sense for the app's threat model.

### Double Ratchet (after X3DH)

Once a session is established, the Double Ratchet provides per-message forward secrecy. Each
message uses a fresh symmetric key derived from a ratchet chain. Compromising message N's key
does not compromise messages 1..N-1 or N+1..∞.

**Library**: `libsignal-client` (~5MB AAR). See `docs/optional-deps.md`.

```kotlin
// Add to build.gradle:
// implementation "org.signal:libsignal-android:<version>"

// Use SignalProtocolStore to persist sessions.
// SessionCipher wraps the Double Ratchet.
```

---

## Key hierarchy summary

```
Ed25519 keypair (IIdentity)
  ├── Nostr: npub = bech32(pubkey), events signed with privkey
  ├── Noise_XX: static key pair for handshake
  ├── X3DH: identity key (IK), if implementing offline messaging
  └── DID: did:key:z<base58btc(0xed01 || pubkey)>
```

One keypair. Multiple protocol roles. No key management per protocol.

---

## ProGuard notes

If using `libsignal-client`, add to `proguard-rules.pro`:
```
-keep class org.signal.libsignal.** { *; }
-keep class org.whispersystems.** { *; }
```

AES/GCM and Ed25519 are in `javax.crypto` / `java.security` — no ProGuard rules needed.
