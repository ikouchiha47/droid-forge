# Blockchain — Feasibility Analysis

Status: **Tier 5 — documented only.** Too app-specific for skeleton. Add per-app when needed.

---

## What is feasible on mobile (no node, no mining)

| Capability | Library | Notes |
|---|---|---|
| Wallet keygen + signing | `web3j` | BIP39 mnemonic → HD derivation → Ethereum address |
| Read chain state | `web3j` JSON-RPC | `eth_call`, `eth_getBalance` via public RPC endpoint |
| IPFS content | `java-ipfs-http-client` | Store/fetch content by CID, pin via Pinata API |
| DID identity | `did:key` (no lib needed) | Ed25519 pubkey → `did:key:z...` |
| Signed messages | `web3j` | Prove wallet ownership without an on-chain transaction |
| NFT metadata | `web3j` ERC-721 call | Read tokenURI, fetch IPFS metadata |

---

## DID identity — already wired in skeleton

The `Ed25519Identity` keypair IS a DID. No extra library needed.

```kotlin
// Convert Ed25519 public key to did:key
fun toDid(identity: IIdentity): String {
    val pubKey = identity.publicKey()  // 32 bytes
    val multicodecPrefix = byteArrayOf(0xed.toByte(), 0x01.toByte())
    val bytes = multicodecPrefix + pubKey
    val base58 = Base58.encode(bytes)  // use any base58 lib or roll it (50 lines)
    return "did:key:z$base58"
}

// Example output: did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK
```

DID documents can be published as Nostr events (kind=0) or as static JSON on any HTTP server.
No blockchain transaction required for `did:key`.

---

## Ethereum wallet

```kotlin
// Add web3j (see docs/optional-deps.md)
val mnemonic = MnemonicUtils.generateMnemonic()   // BIP39, 12 or 24 words
val seed = MnemonicUtils.generateSeed(mnemonic, "")
val masterKeypair = Bip32ECKeyPair.generateKeyPair(seed)
val childKeypair = Bip32ECKeyPair.deriveKeyPair(masterKeypair, intArrayOf(44 or HARDENED, 60 or HARDENED, 0 or HARDENED, 0, 0))
val credentials = Credentials.create(childKeypair)
val address = credentials.address  // 0x...
```

### Reading balance (no node required)

```kotlin
val web3 = Web3j.build(HttpService("https://mainnet.infura.io/v3/YOUR_KEY"))
// Or use a free public RPC:
val web3 = Web3j.build(HttpService("https://rpc.ankr.com/eth"))

val balance = web3.ethGetBalance(address, DefaultBlockParameterName.LATEST).send()
val etherValue = Convert.fromWei(balance.balance.toBigDecimal(), Convert.Unit.ETHER)
```

Free public RPC endpoints:
- `https://rpc.ankr.com/eth` (Ethereum mainnet)
- `https://polygon-rpc.com` (Polygon)
- `https://api.avax.network/ext/bc/C/rpc` (Avalanche)

### Signing a message (prove wallet ownership)

```kotlin
val message = "I own this wallet at ${System.currentTimeMillis()}"
val prefix = "Ethereum Signed Message:\n${message.length}"
val hash = Hash.sha3((prefix + message).toByteArray())
val signature = Sign.signMessage(hash, childKeypair)
// Send (v, r, s) to verifier — they call ecRecover to get the address
```

### Sending a transaction

Requires gas (ETH). Not recommended for mobile apps serving general users — they need a funded
wallet. For dApps, use WalletConnect to delegate signing to the user's existing wallet app.

---

## IPFS content

```kotlin
// Add java-ipfs-http-client (see docs/optional-deps.md)
// Requires a running IPFS node or Pinata API gateway

// Via Pinata (free tier: 1GB):
val pinataClient = OkHttpClient()
val body = MultipartBody.Builder()
    .setType(MultipartBody.FORM)
    .addFormDataPart("file", filename, file.asRequestBody())
    .build()
val request = Request.Builder()
    .url("https://api.pinata.cloud/pinning/pinFileToIPFS")
    .header("Authorization", "Bearer $PINATA_JWT")
    .post(body)
    .build()
val response = pinataClient.newCall(request).execute()
val cid = JSONObject(response.body!!.string()).getString("IpfsHash")

// Fetch by CID (public gateway, no API key):
val content = OkHttpClient().newCall(
    Request.Builder().url("https://ipfs.io/ipfs/$cid").build()
).execute().body?.bytes()
```

---

## NFT metadata

```kotlin
// ERC-721 tokenURI call (read-only, no gas)
val function = Function("tokenURI", listOf(Uint256(tokenId.toBigInteger())), listOf(object: TypeReference<Utf8String>() {}))
val encoded = FunctionEncoder.encode(function)
val response = web3.ethCall(Transaction.createEthCallTransaction(null, contractAddress, encoded), DefaultBlockParameterName.LATEST).send()
val tokenUri = FunctionReturnDecoder.decode(response.value, function.outputParameters)[0].value as String
// tokenUri is usually ipfs://<CID> — fetch from gateway
```

---

## Payment channels (Layer 2)

For in-app micropayments without per-transaction gas costs:

- **Lightning Network** (Bitcoin): Breez SDK (`io.breez.sdk:breez-sdk-android`) — non-custodial
  Lightning node in the app. ~10MB. Not in optional-deps because it requires a node identity and
  LSP connection at startup.
- **State channels** (Ethereum): requires on-chain deposit transaction to open. Viable for games
  or apps where users expect to stake ETH.
- **Polygon / L2 transfers**: standard `web3j` transfers on Polygon cost <$0.001. Acceptable for
  content tipping without UX explanation overhead.

---

## ZK proofs (verify on-device)

Mobile can verify ZK proofs but not generate them (too slow). Verification is ~5ms on modern
Android.

Use case: prove a credential without revealing it (age > 18, membership in a group) using a proof
generated on a server or desktop.

Library: `snarkjs` proofs verified with Java wrapper. No production library exists for Android yet —
this is a roll-your-own area. Document the use case in the app spec and implement per-app.

---

## When NOT to use blockchain

- For any data that needs to be deleted (GDPR) — IPFS and on-chain data are permanent
- For real-time data — block time is 2-15 seconds, unsuitable for location or chat
- For private data — public chains are public; even "encrypted" data is visible as ciphertext
- When a simple database suffices — do not reach for blockchain to add credibility to a local app
