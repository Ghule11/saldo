const encoder = new TextEncoder();
const providerUrl = "https://api.enablebanking.com/application";

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === "GET" && url.pathname === "/api/health") {
      return json({ status: "ok" });
    }

    if (request.method !== "GET" ||
        (url.pathname !== "/api/probe" && url.pathname !== "/api/storage-probe")) {
      return json({ error: "Not found" }, 404);
    }

    if (!hasProbeToken(request, env.PROBE_TOKEN)) {
      return json({ error: "Unauthorized" }, 401);
    }

    if (!env.EB_APPLICATION_ID || !env.EB_PRIVATE_KEY) {
      return json({ error: "Sandbox credentials are not configured" }, 503);
    }

    try {
      if (url.pathname === "/api/storage-probe") {
        if (!env.DB || !env.DATA_ENCRYPTION_KEY) {
          return json({ error: "Encrypted storage is not configured" }, 503);
        }
        return json(await probeEncryptedStorage(env));
      }

      const token = await createEnableBankingJwt(env.EB_APPLICATION_ID, env.EB_PRIVATE_KEY);
      const response = await fetch(providerUrl, {
        headers: {
          Accept: "application/json",
          Authorization: `Bearer ${token}`
        }
      });

      if (!response.ok) {
        return json({ status: "provider_error", providerStatus: response.status }, 502);
      }

      const application = await response.json();
      const environment = String(application.environment || "").toUpperCase();
      return json({
        status: environment === env.EXPECTED_ENVIRONMENT ? "ok" : "wrong_environment",
        environment,
        active: application.active === true
      }, environment === env.EXPECTED_ENVIRONMENT ? 200 : 409);
    } catch {
      // Keep provider responses, key material and internal errors out of public replies.
      return json({ error: "Sandbox probe failed" }, 503);
    }
  }
};

async function probeEncryptedStorage(env) {
  const name = `probe:${crypto.randomUUID()}`;
  const marker = crypto.randomUUID();
  const keyBytes = decodeBase64Url(env.DATA_ENCRYPTION_KEY);
  if (keyBytes.length !== 32) throw new Error("Invalid encryption key");
  const key = await crypto.subtle.importKey("raw", keyBytes, "AES-GCM", false,
    ["encrypt", "decrypt"]);
  const nonce = crypto.getRandomValues(new Uint8Array(12));
  const encrypted = new Uint8Array(await crypto.subtle.encrypt(
    { name: "AES-GCM", iv: nonce }, key, encoder.encode(marker)));
  const combined = new Uint8Array(nonce.length + encrypted.length);
  combined.set(nonce);
  combined.set(encrypted, nonce.length);

  try {
    await env.DB.prepare("INSERT INTO app_state (name, encrypted_value) VALUES (?, ?)")
      .bind(name, base64Url(combined)).run();
    const row = await env.DB.prepare("SELECT encrypted_value FROM app_state WHERE name = ?")
      .bind(name).first();
    if (!row) throw new Error("Missing encrypted marker");
    const saved = decodeBase64Url(row.encrypted_value);
    const plain = await crypto.subtle.decrypt(
      { name: "AES-GCM", iv: saved.slice(0, 12) }, key, saved.slice(12));
    if (new TextDecoder().decode(plain) !== marker) throw new Error("Storage mismatch");
    return { status: "ok", storage: "encrypted_d1" };
  } finally {
    await env.DB.prepare("DELETE FROM app_state WHERE name = ?").bind(name).run();
  }
}

function hasProbeToken(request, expected) {
  if (typeof expected !== "string" || expected.length < 32) return false;
  const authorization = request.headers.get("Authorization") || "";
  const supplied = authorization.startsWith("Bearer ") ? authorization.slice(7) : "";
  let difference = supplied.length ^ expected.length;
  for (let index = 0; index < expected.length; index++) {
    difference |= expected.charCodeAt(index) ^ (supplied.charCodeAt(index) || 0);
  }
  return difference === 0;
}

async function createEnableBankingJwt(applicationId, privateKeyPem) {
  const now = Math.floor(Date.now() / 1000);
  const header = { typ: "JWT", alg: "RS256", kid: applicationId };
  const payload = {
    iss: "enablebanking.com",
    aud: "api.enablebanking.com",
    iat: now,
    exp: now + 300
  };
  const signingInput = `${base64Url(encoder.encode(JSON.stringify(header)))}.${base64Url(encoder.encode(JSON.stringify(payload)))}`;
  const key = await crypto.subtle.importKey(
    "pkcs8",
    decodePkcs8(privateKeyPem),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"]
  );
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    encoder.encode(signingInput)
  );
  return `${signingInput}.${base64Url(new Uint8Array(signature))}`;
}

function decodePkcs8(pem) {
  const match = pem.replace(/\\n/g, "\n").match(
    /-----BEGIN PRIVATE KEY-----\s*([A-Za-z0-9+/=\s]+?)\s*-----END PRIVATE KEY-----/
  );
  if (!match) throw new Error("Expected a PKCS#8 private key");
  const binary = atob(match[1].replace(/\s/g, ""));
  return Uint8Array.from(binary, character => character.charCodeAt(0));
}

function base64Url(bytes) {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function decodeBase64Url(value) {
  const binary = atob(value.replace(/-/g, "+").replace(/_/g, "/"));
  return Uint8Array.from(binary, character => character.charCodeAt(0));
}

function json(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
      "X-Content-Type-Options": "nosniff"
    }
  });
}
