const encoder = new TextEncoder();
const providerUrl = "https://api.enablebanking.com/application";
const providerBaseUrl = "https://api.enablebanking.com";
const callbackPath = "/api/banking/callback";
const validMode = env => ["SANDBOX", "PRODUCTION"].includes(env.EXPECTED_ENVIRONMENT);
const sessionName = env => `${env.EXPECTED_ENVIRONMENT.toLowerCase()}_session`;
const refreshStatusName = env => `${env.EXPECTED_ENVIRONMENT.toLowerCase()}_refresh_status`;

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === "GET" && url.pathname === "/api/health") {
      return json({ status: "ok" });
    }

    if (request.method === "GET" && url.pathname === callbackPath) {
      return finishAuthorization(url, env);
    }

    if (request.method === "POST" && url.pathname === "/api/pairing/claim") {
      try { return json(await claimPairingCode(request, env)); }
      catch (error) {
        return error instanceof SandboxError ? json({ detail: error.message }, error.status)
          : json({ error: "Pairing failed" }, 503);
      }
    }

    const knownGet = request.method === "GET" && [
      "/api/probe", "/api/storage-probe", "/api/banking/status", "/api/banking/institutions",
      "/api/banking/data", "/api/pairing/devices", "/api/history"
    ].includes(url.pathname);
    const knownPost = request.method === "POST" &&
      ["/api/banking/authorization", "/api/pairing/code", "/api/pairing/revoke",
        "/api/pairing/self-revoke", "/api/history/import",
        "/api/banking/authorization/bank-austria"]
        .includes(url.pathname);
    if (!knownGet && !knownPost) {
      return json({ error: "Not found" }, 404);
    }

    const isAdmin = hasProbeToken(request, env.PROBE_TOKEN);
    const deviceAllowed = ["/api/banking/status", "/api/banking/data", "/api/history",
      "/api/pairing/self-revoke", "/api/banking/authorization/bank-austria"]
      .includes(url.pathname);
    if (!isAdmin && (!deviceAllowed || !await hasDeviceToken(request, env))) {
      return json({ error: "Unauthorized" }, 401);
    }

    if (!env.EB_APPLICATION_ID || !env.EB_PRIVATE_KEY) {
      return json({ error: "Banking credentials are not configured" }, 503);
    }

    try {
      if (url.pathname === "/api/pairing/code") {
        return json(await createPairingCode(env));
      }
      if (url.pathname === "/api/pairing/devices") {
        const result = await env.DB.prepare(
          "SELECT id, created_at, revoked_at FROM devices ORDER BY created_at DESC"
        ).all();
        return json({ devices: result.results || [] });
      }
      if (url.pathname === "/api/pairing/revoke") {
        return json(await revokeDevice(request, env));
      }
      if (url.pathname === "/api/pairing/self-revoke") {
        return json(await selfRevokeDevice(request, env));
      }
      if (url.pathname === "/api/history") {
        return json(env.EXPECTED_ENVIRONMENT === "PRODUCTION"
          ? await readEncryptedState(env, "production_history") || { available: false }
          : { available: false });
      }
      if (url.pathname === "/api/history/import") {
        return json(await importHistory(request, env));
      }
      if (url.pathname === "/api/storage-probe") {
        if (!env.DB || !env.DATA_ENCRYPTION_KEY) {
          return json({ error: "Encrypted storage is not configured" }, 503);
        }
        return json(await probeEncryptedStorage(env));
      }

      if (url.pathname.startsWith("/api/banking/")) {
        if (!env.DB || !env.DATA_ENCRYPTION_KEY || !validMode(env)) {
          return json({ error: "Banking storage is not configured" }, 503);
        }
        const provider = createProviderClient(env, request);
        if (url.pathname === "/api/banking/status") {
          return json(await bankingStatus(env));
        }
        if (url.pathname === "/api/banking/institutions") {
          return json(await bankingInstitutions(env, provider, url.searchParams.get("country")));
        }
        if (url.pathname === "/api/banking/authorization") {
          return json(await startAuthorization(request, url, env, provider));
        }
        if (url.pathname === "/api/banking/authorization/bank-austria") {
          return json(await startBankAustriaAuthorization(url, env, provider));
        }
        if (url.pathname === "/api/banking/data") {
          return json(await bankingData(env, provider, url.searchParams.get("allTransactions") === "true"));
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
      return json({ error: "Banking request failed" }, 503);
    }
  },

  async scheduled(controller, env) {
    if (env.EXPECTED_ENVIRONMENT !== "PRODUCTION" || !env.DB ||
        !env.DATA_ENCRYPTION_KEY || !env.EB_APPLICATION_ID || !env.EB_PRIVATE_KEY) return;

    const scheduledAt = new Date(controller.scheduledTime);
    const parts = Object.fromEntries(new Intl.DateTimeFormat("en-GB", {
      timeZone: "Europe/Vienna", year: "numeric", month: "2-digit", day: "2-digit",
      hour: "2-digit", hourCycle: "h23"
    }).formatToParts(scheduledAt).map(part => [part.type, part.value]));
    const localHour = Number(parts.hour);
    if (![8, 17, 21].includes(localHour)) return;

    const session = await readEncryptedState(env, sessionName(env));
    if (!session?.sessionId) return;

    const slot = `production_refresh:${parts.year}-${parts.month}-${parts.day}:${parts.hour}`;
    if (!await claimRefreshSlot(env, slot)) return;

    const attemptedAt = scheduledAt.toISOString();
    const previous = await readEncryptedState(env, refreshStatusName(env));
    let result;
    try {
      result = await bankingData(env, createProviderClient(env), false, true);
    } catch (error) {
      const failure = {
        at: attemptedAt,
        stage: error?.refreshStage || "preparation",
        code: refreshFailureCode(error)
      };
      try {
        await writeEncryptedState(env, refreshStatusName(env), {
          lastAttemptAt: attemptedAt,
          lastSuccessAt: previous?.lastSuccessAt || null,
          lastOutcome: "failed",
          lastFailure: failure
        });
      } catch {
        console.warn("Scheduled refresh status could not be saved");
      }
      console.warn("Scheduled banking refresh failed", failure.stage, failure.code);
      return;
    }
    try {
      await writeEncryptedState(env, refreshStatusName(env), {
        lastAttemptAt: attemptedAt,
        lastSuccessAt: result.fetchedAt,
        lastOutcome: "success",
        lastFailure: previous?.lastFailure || null
      });
    } catch {
      console.warn("Scheduled refresh success could not be saved");
    }
  }
};

function refreshFailureCode(error) {
  if (error?.diagnosticCode) return error.diagnosticCode;
  if (error?.status === 429) return "rate_limited";
  if (error?.status === 409) return "session_unavailable";
  if (error?.status === 502) return "invalid_response";
  return "internal_error";
}

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

async function createPairingCode(env) {
  if (!env.DB || !validMode(env)) {
    throw new SandboxError(503, "Kopplung ist nicht eingerichtet.");
  }
  const code = base64Url(crypto.getRandomValues(new Uint8Array(16)));
  const codeHash = await sha256(code);
  const expiresAt = Date.now() + 10 * 60 * 1000;
  await env.DB.prepare("INSERT INTO pairing_codes (code_hash, expires_at) VALUES (?, ?)")
    .bind(codeHash, expiresAt).run();
  return { code, expiresAt: new Date(expiresAt).toISOString() };
}

async function claimPairingCode(request, env) {
  if (!env.DB || !validMode(env)) {
    throw new SandboxError(503, "Kopplung ist nicht eingerichtet.");
  }
  let body;
  try { body = await request.json(); } catch {
    throw new SandboxError(400, "Ungültige Anfrage.");
  }
  const code = String(body?.code || "").trim();
  if (!/^[A-Za-z0-9_-]{22}$/.test(code)) {
    throw new SandboxError(400, "Ungültiger Kopplungscode.");
  }
  const pending = await env.DB.prepare(
    "DELETE FROM pairing_codes WHERE code_hash = ? RETURNING expires_at"
  ).bind(await sha256(code)).first();
  if (!pending || pending.expires_at < Date.now()) {
    throw new SandboxError(400, "Kopplungscode abgelaufen oder bereits verwendet.");
  }
  const token = base64Url(crypto.getRandomValues(new Uint8Array(32)));
  const deviceId = crypto.randomUUID();
  await env.DB.prepare("INSERT INTO devices (id, token_hash, created_at) VALUES (?, ?, ?)")
    .bind(deviceId, await sha256(token), Date.now()).run();
  return { deviceId, token };
}

async function revokeDevice(request, env) {
  if (!env.DB) throw new SandboxError(503, "Kopplung ist nicht eingerichtet.");
  let body;
  try { body = await request.json(); } catch {
    throw new SandboxError(400, "Ungültige Anfrage.");
  }
  const deviceId = String(body?.deviceId || "");
  if (!/^[0-9a-f-]{36}$/i.test(deviceId)) {
    throw new SandboxError(400, "Ungültiges Gerät.");
  }
  const result = await env.DB.prepare("UPDATE devices SET revoked_at = ? WHERE id = ? AND revoked_at IS NULL")
    .bind(Date.now(), deviceId).run();
  return { revoked: result.meta?.changes === 1 };
}

async function selfRevokeDevice(request, env) {
  const token = request.headers.get("Authorization").slice(7);
  const result = await env.DB.prepare(
    "UPDATE devices SET revoked_at = ? WHERE token_hash = ? AND revoked_at IS NULL"
  ).bind(Date.now(), await sha256(token)).run();
  return { revoked: result.meta?.changes === 1 };
}

async function hasDeviceToken(request, env) {
  if (!env.DB || !validMode(env)) return false;
  const authorization = request.headers.get("Authorization") || "";
  const token = authorization.startsWith("Bearer ") ? authorization.slice(7) : "";
  if (!/^[A-Za-z0-9_-]{43}$/.test(token)) return false;
  const device = await env.DB.prepare(
    "SELECT id FROM devices WHERE token_hash = ? AND revoked_at IS NULL"
  ).bind(await sha256(token)).first();
  return !!device;
}

async function sha256(value) {
  return base64Url(new Uint8Array(await crypto.subtle.digest("SHA-256", encoder.encode(value))));
}

async function importHistory(request, env) {
  if (!env.DB || !env.DATA_ENCRYPTION_KEY || env.EXPECTED_ENVIRONMENT !== "PRODUCTION") {
    throw new SandboxError(503, "Historienimport ist nicht eingerichtet.");
  }
  const raw = await request.text();
  if (raw.length > 5_000_000) throw new SandboxError(413, "Umsatzliste ist zu groß.");
  let input;
  try { input = JSON.parse(raw); } catch {
    throw new SandboxError(400, "Ungültige Umsatzliste.");
  }
  if (!Array.isArray(input?.transactions) || input.transactions.length === 0 ||
      input.transactions.length > 20_000 || !/^[A-Z]{3}$/.test(input.currency || "")) {
    throw new SandboxError(400, "Ungültige Umsatzliste.");
  }
  const transactions = input.transactions.map(item => {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(item?.bookingDate || "") ||
        !Number.isSafeInteger(item?.amountCents) ||
        typeof item?.merchant !== "string" || item.merchant.length > 500 ||
        typeof item?.detail !== "string" || item.detail.length > 4000 ||
        typeof item?.identity !== "string" || item.identity.length > 500) {
      throw new SandboxError(400, "Ungültige Buchung in der Umsatzliste.");
    }
    return {
      bookingDate: item.bookingDate,
      amountCents: item.amountCents,
      merchant: item.merchant,
      detail: item.detail,
      identity: item.identity
    };
  });
  await writeEncryptedState(env, "production_history", {
    currency: input.currency,
    importedAt: new Date().toISOString(),
    firstBookingDate: transactions.reduce((date, item) =>
      item.bookingDate < date ? item.bookingDate : date, transactions[0].bookingDate),
    lastBookingDate: transactions.reduce((date, item) =>
      item.bookingDate > date ? item.bookingDate : date, transactions[0].bookingDate),
    transactions
  });
  return { imported: transactions.length };
}

function createProviderClient(env, request = null) {
  let jwt;
  return async (method, path, body) => {
    jwt ||= createEnableBankingJwt(env.EB_APPLICATION_ID, env.EB_PRIVATE_KEY);
    const psuHeaders = {};
    if (path.startsWith("/accounts/") && request && env.EXPECTED_ENVIRONMENT === "PRODUCTION") {
      const userAgent = request.headers.get("Psu-User-Agent") || "Saldo/1.0 (Android)";
      const ipAddress = request.headers.get("CF-Connecting-IP") || "";
      if (userAgent.length <= 256 && !/[\r\n]/.test(userAgent)) {
        psuHeaders["Psu-User-Agent"] = userAgent;
      }
      if (/^[0-9a-fA-F:.]{2,45}$/.test(ipAddress)) {
        psuHeaders["Psu-Ip-Address"] = ipAddress;
      }
    }
    let response;
    try {
      response = await fetch(`${providerBaseUrl}${path}`, {
        method,
        headers: {
          Accept: "application/json",
          Authorization: `Bearer ${await jwt}`,
          ...psuHeaders,
          ...(body ? { "Content-Type": "application/json" } : {})
        },
        ...(body ? { body: JSON.stringify(body) } : {})
      });
    } catch {
      throw new SandboxError(503, "Enable Banking ist derzeit nicht erreichbar.", "provider_network");
    }
    const result = await response.json().catch(() => ({}));
    if (!response.ok) {
      console.warn("Banking provider status", response.status);
      throw new SandboxError(response.status === 429 ? 429 : 502,
        "Enable Banking konnte die Anfrage nicht abschließen.", `provider_http_${response.status}`);
    }
    return result;
  };
}

async function requireActiveApplication(env, provider) {
  const application = await provider("GET", "/application");
  if (String(application.environment || "").toUpperCase() !== env.EXPECTED_ENVIRONMENT) {
    throw new SandboxError(409, "Die hinterlegte Anwendung hat die falsche Umgebung.");
  }
  await writeEncryptedState(env, `${env.EXPECTED_ENVIRONMENT.toLowerCase()}_application_status`, {
    active: application.active === true,
    checkedAt: new Date().toISOString()
  });
  if (application.active !== true) {
    throw new SandboxError(409, "Die Enable-Banking-Anwendung ist nicht aktiv.");
  }
  return application;
}

async function bankingStatus(env) {
  const session = await readEncryptedState(env, sessionName(env));
  const applicationStatus = await readEncryptedState(env,
    `${env.EXPECTED_ENVIRONMENT.toLowerCase()}_application_status`);
  const rateLimit = await readEncryptedState(env, `${env.EXPECTED_ENVIRONMENT.toLowerCase()}_rate_limit`);
  const refresh = env.EXPECTED_ENVIRONMENT === "PRODUCTION"
    ? await readEncryptedState(env, refreshStatusName(env)) : null;
  return {
    isConfigured: true,
    isSandbox: env.EXPECTED_ENVIRONMENT === "SANDBOX",
    environment: env.EXPECTED_ENVIRONMENT,
    institutionName: session?.institutionName || null,
    institutionCountry: session?.institutionCountry || null,
    accountCount: session?.accountUids?.length || 0,
    isActive: applicationStatus?.active ?? true,
    retryAfter: rateLimit?.until > Date.now() ? new Date(rateLimit.until).toISOString() : null,
    refresh
  };
}

async function bankingInstitutions(env, provider, country) {
  await requireActiveApplication(env, provider);
  const result = await provider("GET", "/aspsps");
  const institutions = Array.isArray(result.aspsps) ? result.aspsps : [];
  return institutions.filter(item => !country ||
    String(item.country || "").toUpperCase() === country.toUpperCase());
}

async function startAuthorization(request, url, env, provider) {
  let body;
  try { body = await request.json(); } catch {
    throw new SandboxError(400, "Ungültige Anfrage.");
  }
  const name = String(body?.institutionName || "").trim();
  const country = String(body?.country || "").trim().toUpperCase();
  if (!name || name.length > 160 || !/^[A-Z]{2}$/.test(country)) {
    throw new SandboxError(400, "Name der Bank und Land sind erforderlich.");
  }
  const institutions = await bankingInstitutions(env, provider, country);
  const institution = institutions.find(item =>
    String(item.name || "").toLowerCase() === name.toLowerCase() &&
    String(item.country || "").toUpperCase() === country);
  if (!institution) throw new SandboxError(400, "Diese Bank ist nicht verfügbar.");

  return createAuthorization(url, env, provider, institution);
}

async function startBankAustriaAuthorization(url, env, provider) {
  if (env.EXPECTED_ENVIRONMENT !== "PRODUCTION") {
    throw new SandboxError(409, "Bank Austria benötigt die Production-Anwendung.");
  }
  const matches = (await bankingInstitutions(env, provider, "AT"))
    .filter(item => String(item.name || "").includes("Bank Austria"));
  if (matches.length !== 1) {
    throw new SandboxError(409, "Bank Austria wurde nicht eindeutig gefunden.");
  }
  return createAuthorization(url, env, provider, matches[0]);
}

async function createAuthorization(url, env, provider, institution) {
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
    aspsp: { name: institution.name, country: institution.country },
    state,
    redirect_url: redirectUrl,
    psu_type: "personal"
  });
  if (!safeAuthorizationUrl(authorization.url)) {
    throw new SandboxError(502, "Enable Banking hat einen ungültigen Freigabe-Link geliefert.");
  }

  await env.DB.prepare(
    "INSERT INTO pending_authorizations (state, institution_name, institution_country, expires_at) VALUES (?, ?, ?, ?)"
  ).bind(state, institution.name, institution.country, Date.now() + 10 * 60 * 1000).run();
  return { url: authorization.url, state, redirectUrl };
}

async function finishAuthorization(url, env) {
  if (!env.DB || !env.DATA_ENCRYPTION_KEY || !env.EB_APPLICATION_ID ||
      !env.EB_PRIVATE_KEY || !validMode(env)) {
    return callbackPage("Bankverbindung ist nicht eingerichtet.", 503);
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
      return callbackPage("Bankfreigabe abgelaufen. Starte sie erneut.", 400);
    }
    if (url.searchParams.has("error")) return callbackPage("Bankfreigabe abgebrochen.", 400);
    const code = url.searchParams.get("code") || "";
    if (!code || code.length > 2048) {
      return callbackPage("Bankfreigabe nicht abgeschlossen.", 400);
    }
    const provider = createProviderClient(env);
    await requireActiveApplication(env, provider);
    const result = await provider("POST", "/sessions", { code });
    const accountUids = [...new Set((Array.isArray(result.accounts) ? result.accounts : [])
      .map(account => account?.uid).filter(uid => typeof uid === "string" && uid))];
    if (!result.session_id || accountUids.length === 0) {
      return callbackPage("Die Bank hat kein nutzbares Konto geliefert.", 502);
    }
    await writeEncryptedState(env, sessionName(env), {
      sessionId: result.session_id,
      institutionName: pending.institution_name,
      institutionCountry: pending.institution_country,
      accountUids
    });
    if (env.EXPECTED_ENVIRONMENT === "PRODUCTION") {
      try {
        const previous = await readEncryptedState(env, refreshStatusName(env));
        await writeEncryptedState(env, refreshStatusName(env), {
          lastAttemptAt: null,
          lastSuccessAt: null,
          lastOutcome: null,
          lastFailure: previous?.lastFailure || null
        });
      } catch {
        console.warn("Banking refresh status could not be reset");
      }
    }
    return callbackPage("Konto verbunden. Kehre zu Saldo zurück.", 200);
  } catch (error) {
    console.warn("Banking callback failed", error?.name || "UnknownError");
    return callbackPage("Konto konnte nicht verbunden werden.", 502);
  }
}

async function bankingData(env, provider, allTransactions, scheduledRefresh = false) {
  const session = await readEncryptedState(env, sessionName(env));
  if (!session?.sessionId || !Array.isArray(session.accountUids) || session.accountUids.length === 0) {
    throw new SandboxError(409, "Zuerst ein Konto verbinden.");
  }

  const sessionHash = base64Url(new Uint8Array(await crypto.subtle.digest(
    "SHA-256", encoder.encode(session.sessionId))));
  const cacheName = `${env.EXPECTED_ENVIRONMENT.toLowerCase()}_data:${sessionHash}:${allTransactions ? "all" : "recent"}`;
  const cached = await readEncryptedState(env, cacheName);
  const cacheAge = Date.now() - Date.parse(cached?.fetchedAt || "");
  if (env.EXPECTED_ENVIRONMENT === "PRODUCTION" && !scheduledRefresh) {
    if (cached) return { ...cached, isCached: true };
    throw new SandboxError(409, "Bankdaten werden beim nächsten geplanten Abruf geladen.");
  }
  if (!scheduledRefresh && cached && cacheAge >= 0 && cacheAge < 12 * 60 * 60 * 1000) {
    return { ...cached, isCached: true };
  }

  const rateLimitName = `${env.EXPECTED_ENVIRONMENT.toLowerCase()}_rate_limit`;
  const rateLimit = await readEncryptedState(env, rateLimitName);
  if (rateLimit?.until > Date.now()) {
    if (!scheduledRefresh && cached && cacheAge >= 0 && cacheAge < 24 * 60 * 60 * 1000) {
      return { ...cached, isCached: true };
    }
    throw new SandboxError(429, "Die Bank erlaubt derzeit keinen weiteren Abruf.");
  }

  let refreshStage = "balances";
  try {
    const accounts = [];
    for (const accountUid of session.accountUids) {
      const path = `/accounts/${encodeURIComponent(accountUid)}`;
      refreshStage = "balances";
      const balances = await provider("GET", `${path}/balances`);
      const collected = [];
      const seenKeys = new Set();
      let continuationKey = null;
      const maxPages = allTransactions ? 100 : env.EXPECTED_ENVIRONMENT === "PRODUCTION" ? 1 : 10;
      for (let page = 0; page < maxPages; page++) {
        refreshStage = "transactions";
        const suffix = continuationKey
          ? `?continuation_key=${encodeURIComponent(continuationKey)}` : "";
        const response = await provider("GET", `${path}/transactions${suffix}`);
        if (!Array.isArray(response.transactions)) {
          throw new SandboxError(502, "Die Bank hat ungültige Umsätze geliefert.");
        }
        collected.push(...response.transactions);
        continuationKey = typeof response.continuation_key === "string" &&
          response.continuation_key ? response.continuation_key : null;
        if (!continuationKey) break;
        if (seenKeys.has(continuationKey)) {
          throw new SandboxError(502, "Die Umsätze konnten nicht vollständig geladen werden.");
        }
        if (page === maxPages - 1 && env.EXPECTED_ENVIRONMENT === "PRODUCTION" &&
            !allTransactions) break;
        if (page === maxPages - 1) {
          throw new SandboxError(502, "Die Umsätze konnten nicht vollständig geladen werden.");
        }
        seenKeys.add(continuationKey);
      }
      accounts.push({ balances, transactions: { transactions: collected, continuation_key: continuationKey } });
    }

    const result = {
      institutionName: session.institutionName,
      institutionCountry: session.institutionCountry,
      accounts,
      fetchedAt: new Date().toISOString(),
      isCached: false
    };
    refreshStage = "cache_write";
    await writeEncryptedState(env, cacheName, result);
    return result;
  } catch (error) {
    if (error && typeof error === "object") error.refreshStage = refreshStage;
    if (error instanceof SandboxError && error.status === 429) {
      await writeEncryptedState(env, rateLimitName, { until: Date.now() + 6 * 60 * 60 * 1000 });
      if (!scheduledRefresh && cached && cacheAge >= 0 && cacheAge < 24 * 60 * 60 * 1000) {
        return { ...cached, isCached: true };
      }
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
  const encryptedValue = await encryptStateValue(env, value);
  await env.DB.prepare(
    "INSERT INTO app_state (name, encrypted_value) VALUES (?, ?) ON CONFLICT(name) DO UPDATE SET encrypted_value = excluded.encrypted_value"
  ).bind(name, encryptedValue).run();
}

async function claimRefreshSlot(env, name) {
  const encryptedValue = await encryptStateValue(env, { claimedAt: new Date().toISOString() });
  const claimed = await env.DB.prepare(
    "INSERT OR IGNORE INTO app_state (name, encrypted_value) VALUES (?, ?) RETURNING name"
  ).bind(name, encryptedValue).first();
  return claimed !== null;
}

async function encryptStateValue(env, value) {
  const key = await importEncryptionKey(env.DATA_ENCRYPTION_KEY);
  const nonce = crypto.getRandomValues(new Uint8Array(12));
  const encrypted = new Uint8Array(await crypto.subtle.encrypt(
    { name: "AES-GCM", iv: nonce }, key, encoder.encode(JSON.stringify(value))));
  const combined = new Uint8Array(nonce.length + encrypted.length);
  combined.set(nonce);
  combined.set(encrypted, nonce.length);
  return base64Url(combined);
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
  constructor(status, message, diagnosticCode = null) {
    super(message);
    this.status = status;
    this.diagnosticCode = diagnosticCode;
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
