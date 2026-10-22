const encoder = new TextEncoder();
const providerUrl = "https://api.enablebanking.com/application";
const providerBaseUrl = "https://api.enablebanking.com";
const callbackPath = "/api/banking/callback";

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === "GET" && url.pathname === "/api/health") {
      return json({ status: "ok" });
    }

    if (request.method === "GET" && url.pathname === callbackPath) {
      return finishSandboxAuthorization(url, env);
    }

    const knownGet = request.method === "GET" && [
      "/api/probe", "/api/storage-probe", "/api/banking/status", "/api/banking/institutions",
      "/api/banking/data"
    ].includes(url.pathname);
    const knownPost = request.method === "POST" &&
      url.pathname === "/api/banking/authorization";
    if (!knownGet && !knownPost) {
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

      if (url.pathname.startsWith("/api/banking/")) {
        if (!env.DB || !env.DATA_ENCRYPTION_KEY || env.EXPECTED_ENVIRONMENT !== "SANDBOX") {
          return json({ error: "Sandbox storage is not configured" }, 503);
        }
        const provider = createProviderClient(env);
        if (url.pathname === "/api/banking/status") {
          return json(await sandboxStatus(env, provider));
        }
        if (url.pathname === "/api/banking/institutions") {
          return json(await sandboxInstitutions(provider, url.searchParams.get("country")));
        }
        if (url.pathname === "/api/banking/authorization") {
          return json(await startSandboxAuthorization(request, url, env, provider));
        }
        if (url.pathname === "/api/banking/data") {
          return json(await sandboxData(env, provider, url.searchParams.get("allTransactions") === "true"));
        }
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
    } catch (error) {
      if (error instanceof SandboxError) {
        return json({ detail: error.message }, error.status);
      }
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

function createProviderClient(env) {
  let jwt;
  return async (method, path, body) => {
    jwt ||= createEnableBankingJwt(env.EB_APPLICATION_ID, env.EB_PRIVATE_KEY);
    const response = await fetch(`${providerBaseUrl}${path}`, {
      method,
      headers: {
        Accept: "application/json",
        Authorization: `Bearer ${await jwt}`,
        ...(body ? { "Content-Type": "application/json" } : {})
      },
      ...(body ? { body: JSON.stringify(body) } : {})
    });
    const result = await response.json().catch(() => ({}));
    if (!response.ok) {
      console.warn("Sandbox provider status", response.status,
        typeof result.error === "string" ? result.error : "PROVIDER_ERROR");
      throw new SandboxError(response.status === 429 ? 429 : 502,
        "Enable Banking konnte die Testanfrage nicht abschließen.");
    }
    return result;
  };
}

async function requireActiveSandbox(provider) {
  const application = await provider("GET", "/application");
  if (String(application.environment || "").toUpperCase() !== "SANDBOX") {
    throw new SandboxError(409, "Die hinterlegte Anwendung gehört nicht zur Sandbox.");
  }
  if (application.active !== true) {
    throw new SandboxError(409, "Die Sandbox-Anwendung ist nicht aktiv.");
  }
  return application;
}

async function sandboxStatus(env, provider) {
  const application = await provider("GET", "/application");
  if (String(application.environment || "").toUpperCase() !== "SANDBOX") {
    throw new SandboxError(409, "Die hinterlegte Anwendung gehört nicht zur Sandbox.");
  }
  const session = await readEncryptedState(env, "sandbox_session");
  return {
    isConfigured: true,
    isSandbox: true,
    environment: "SANDBOX",
    institutionName: session?.institutionName || null,
    institutionCountry: session?.institutionCountry || null,
    accountCount: session?.accountUids?.length || 0,
    isActive: application.active === true,
    retryAfter: null
  };
}

async function sandboxInstitutions(provider, country) {
  await requireActiveSandbox(provider);
  const result = await provider("GET", "/aspsps");
  const institutions = Array.isArray(result.aspsps) ? result.aspsps : [];
  return institutions.filter(item => !country ||
    String(item.country || "").toUpperCase() === country.toUpperCase());
}

async function startSandboxAuthorization(request, url, env, provider) {
  let body;
  try { body = await request.json(); } catch {
    throw new SandboxError(400, "Ungültige Anfrage.");
  }
  const name = String(body?.institutionName || "").trim();
  const country = String(body?.country || "").trim().toUpperCase();
  if (!name || name.length > 160 || !/^[A-Z]{2}$/.test(country)) {
    throw new SandboxError(400, "Name der Testbank und Land sind erforderlich.");
  }
  const institutions = await sandboxInstitutions(provider, country);
  const institution = institutions.find(item =>
    String(item.name || "").toLowerCase() === name.toLowerCase() &&
    String(item.country || "").toUpperCase() === country);
  if (!institution) throw new SandboxError(400, "Diese Testbank ist nicht verfügbar.");

  const consentSeconds = Math.min(30 * 24 * 60 * 60,
    Number(institution.maximum_consent_validity) > 0
      ? Number(institution.maximum_consent_validity) : 30 * 24 * 60 * 60);
  const state = crypto.randomUUID();
  const redirectUrl = `${url.origin}${callbackPath}`;
  const authorization = await provider("POST", "/auth", {
    access: {
      balances: true,
      transactions: true,
      valid_until: new Date(Date.now() + consentSeconds * 1000).toISOString()
    },
    aspsp: { name: institution.name, country },
    state,
    redirect_url: redirectUrl,
    psu_type: "personal"
  });
  if (!safeAuthorizationUrl(authorization.url)) {
    throw new SandboxError(502, "Enable Banking hat einen ungültigen Freigabe-Link geliefert.");
  }

  await env.DB.prepare(
    "INSERT INTO pending_authorizations (state, institution_name, institution_country, expires_at) VALUES (?, ?, ?, ?)"
  ).bind(state, institution.name, country, Date.now() + 10 * 60 * 1000).run();
  return { url: authorization.url, state, redirectUrl };
}

async function finishSandboxAuthorization(url, env) {
  if (!env.DB || !env.DATA_ENCRYPTION_KEY || !env.EB_APPLICATION_ID ||
      !env.EB_PRIVATE_KEY || env.EXPECTED_ENVIRONMENT !== "SANDBOX") {
    return callbackPage("Testverbindung ist nicht eingerichtet.", 503);
  }
  const state = url.searchParams.get("state") || "";
  if (!/^[0-9a-f-]{36}$/i.test(state)) {
    return callbackPage("Ungültiger Rücksprung. Starte die Testfreigabe erneut.", 400);
  }
  try {
    const pending = await env.DB.prepare(
      "DELETE FROM pending_authorizations WHERE state = ? RETURNING institution_name, institution_country, expires_at"
    ).bind(state).first();
    if (!pending || pending.expires_at < Date.now()) {
      return callbackPage("Testfreigabe abgelaufen. Starte sie erneut.", 400);
    }
    if (url.searchParams.has("error")) return callbackPage("Testfreigabe abgebrochen.", 400);
    const code = url.searchParams.get("code") || "";
    if (!code || code.length > 2048) {
      return callbackPage("Testfreigabe nicht abgeschlossen.", 400);
    }
    const provider = createProviderClient(env);
    await requireActiveSandbox(provider);
    const result = await provider("POST", "/sessions", { code });
    const accountUids = [...new Set((Array.isArray(result.accounts) ? result.accounts : [])
      .map(account => account?.uid).filter(uid => typeof uid === "string" && uid))];
    if (!result.session_id || accountUids.length === 0) {
      return callbackPage("Die Testbank hat kein nutzbares Konto geliefert.", 502);
    }
    await writeEncryptedState(env, "sandbox_session", {
      sessionId: result.session_id,
      institutionName: pending.institution_name,
      institutionCountry: pending.institution_country,
      accountUids
    });
    return callbackPage("Testkonto verbunden. Kehre zu Saldo zurück.", 200);
  } catch (error) {
    console.warn("Sandbox callback failed", error?.name || "UnknownError");
    return callbackPage("Testkonto konnte nicht verbunden werden.", 502);
  }
}

async function sandboxData(env, provider, allTransactions) {
  const session = await readEncryptedState(env, "sandbox_session");
  if (!session?.sessionId || !Array.isArray(session.accountUids) || session.accountUids.length === 0) {
    throw new SandboxError(409, "Zuerst ein Testkonto verbinden.");
  }

  const sessionHash = base64Url(new Uint8Array(await crypto.subtle.digest(
    "SHA-256", encoder.encode(session.sessionId))));
  const cacheName = `sandbox_data:${sessionHash}:${allTransactions ? "all" : "recent"}`;
  const cached = await readEncryptedState(env, cacheName);
  const cacheAge = Date.now() - Date.parse(cached?.fetchedAt || "");
  if (cached && cacheAge >= 0 && cacheAge < 12 * 60 * 60 * 1000) {
    return { ...cached, isCached: true };
  }

  try {
    const accounts = [];
    for (const accountUid of session.accountUids) {
      const path = `/accounts/${encodeURIComponent(accountUid)}`;
      const balances = await provider("GET", `${path}/balances`);
      const collected = [];
      const seenKeys = new Set();
      let continuationKey = null;
      const maxPages = allTransactions ? 100 : 10;
      for (let page = 0; page < maxPages; page++) {
        const suffix = continuationKey
          ? `?continuation_key=${encodeURIComponent(continuationKey)}` : "";
        const response = await provider("GET", `${path}/transactions${suffix}`);
        if (!Array.isArray(response.transactions)) {
          throw new SandboxError(502, "Die Testbank hat ungültige Umsätze geliefert.");
        }
        collected.push(...response.transactions);
        continuationKey = typeof response.continuation_key === "string" &&
          response.continuation_key ? response.continuation_key : null;
        if (!continuationKey) break;
        if (seenKeys.has(continuationKey) || page === maxPages - 1) {
          throw new SandboxError(502, "Die Testumsätze konnten nicht vollständig geladen werden.");
        }
        seenKeys.add(continuationKey);
      }
      accounts.push({ balances, transactions: { transactions: collected, continuation_key: null } });
    }

    const result = {
      institutionName: session.institutionName,
      institutionCountry: session.institutionCountry,
      accounts,
      fetchedAt: new Date().toISOString(),
      isCached: false
    };
    await writeEncryptedState(env, cacheName, result);
    return result;
  } catch (error) {
    if (error instanceof SandboxError && error.status === 429 &&
        cached && cacheAge >= 0 && cacheAge < 24 * 60 * 60 * 1000) {
      return { ...cached, isCached: true };
    }
    throw error;
  }
}

async function readEncryptedState(env, name) {
  const row = await env.DB.prepare("SELECT encrypted_value FROM app_state WHERE name = ?")
    .bind(name).first();
  if (!row) return null;
  const stored = decodeBase64Url(row.encrypted_value);
  const key = await importEncryptionKey(env.DATA_ENCRYPTION_KEY);
  const plaintext = await crypto.subtle.decrypt(
    { name: "AES-GCM", iv: stored.slice(0, 12) }, key, stored.slice(12));
  return JSON.parse(new TextDecoder().decode(plaintext));
}

async function writeEncryptedState(env, name, value) {
  const key = await importEncryptionKey(env.DATA_ENCRYPTION_KEY);
  const nonce = crypto.getRandomValues(new Uint8Array(12));
  const encrypted = new Uint8Array(await crypto.subtle.encrypt(
    { name: "AES-GCM", iv: nonce }, key, encoder.encode(JSON.stringify(value))));
  const combined = new Uint8Array(nonce.length + encrypted.length);
  combined.set(nonce);
  combined.set(encrypted, nonce.length);
  await env.DB.prepare(
    "INSERT INTO app_state (name, encrypted_value) VALUES (?, ?) ON CONFLICT(name) DO UPDATE SET encrypted_value = excluded.encrypted_value"
  ).bind(name, base64Url(combined)).run();
}

async function importEncryptionKey(encoded) {
  const keyBytes = decodeBase64Url(encoded);
  if (keyBytes.length !== 32) throw new Error("Invalid encryption key");
  return crypto.subtle.importKey("raw", keyBytes, "AES-GCM", false, ["encrypt", "decrypt"]);
}

function safeAuthorizationUrl(value) {
  try {
    const url = new URL(value);
    return url.protocol === "https:" && !!url.hostname && !url.username && !url.password;
  } catch { return false; }
}

function callbackPage(message, status) {
  const escaped = message.replace(/[&<>"']/g, character => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
  })[character]);
  return new Response(`<!doctype html><html lang="de"><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Saldo</title><body style="font-family:system-ui;max-width:32rem;margin:3rem auto;padding:1rem"><h1>Saldo</h1><p>${escaped}</p></body></html>`, {
    status,
    headers: { "Content-Type": "text/html; charset=utf-8", "Cache-Control": "no-store" }
  });
}

class SandboxError extends Error {
  constructor(status, message) { super(message); this.status = status; }
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
