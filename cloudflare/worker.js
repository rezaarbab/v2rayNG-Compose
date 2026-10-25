/**
 * ARBAB Admin Worker — Cloudflare Workers + D1
 *
 * ------------------------------------------------------------------ SETUP (Console)
 *  1) npm i -g wrangler && wrangler login
 *  2) wrangler d1 create god-db
 *     -> database_id را در wrangler.toml (کنار همین فایل) وارد کنید:
 *        [[d1_databases]]
 *        binding = "DB"
 *        database_name = "god-db"
 *        database_id = "<کپی شده>"
 *  3) cd cloudflare && wrangler deploy
 *  4) رمز پنل در داشبورد: Workers & Pages > god-panel > Settings >
 *     Variables and Secrets > Add > ADMIN_PASSWORD (نوع: Secret)
 *     یا کنسول:  wrangler secret put ADMIN_PASSWORD
 *  5) پنل: https://god-panel.lastofanarchy.workers.dev
 *  6) دیباگ (از هر مرورگری، داخل کنسول همان دامنه):
 *     fetch('/admin/api',{method:'POST',headers:{'content-type':'application/json'},
 *       body:JSON.stringify({action:'ver',pw:'رمز_پنل'})}).then(r=>r.text()).then(console.log)
 *     => build (نسخهٔ واقعیِ مستقر)، cols (ستون‌های واقعی جدول servers)، count، dbErr، pwSet
 *     اگر build آن رشتهٔ پایینِ سرِ این فایل نبود، یعنی deploy روی همین worker ننشسته است.
 * ------------------------------------------------------------------ /SETUP
 */

/** Bump this string when you deploy: the panel header and every srv.all reply carry it, so a
 *  stuck panel immediately says whether the worker running in the cloud is this code or an old
 *  one (that is the single most common cause of "همیشه در حال دریافت"). */
const WORKER_BUILD = "panel-2026-10-25-3";

export default {
  async fetch(request, env, ctx) {
    // don't block requests on schema checks - tables are created once after deploy
    const url = new URL(request.url);
    const path = url.pathname.replace(/\/+$/, "") || "/";

    // ---------------- admin panel (HTML shell public, data password-gated) --
    if (path === "/" || path === "/admin" || path === "/panel") {
      return panelPage(env);
    }
    if (path.startsWith("/admin/api") && request.method === "POST") {
      const body = await request.json().catch(() => ({}));
      if (!pwOk(env, body)) {
        return new Response(JSON.stringify({ ok: false, error: env.ADMIN_PASSWORD ? "wrong password" : "ADMIN_PASSWORD secret is not set on the worker" }), { status: 401, headers: jsonHeaders() });
      }
      try {
        return await adminApi(env, body);
      } catch (e) {
        // very first hit right after deploy - create missing tables then retry once
        try { await ensureSchema(env); } catch (e2) {}
        try {
          return await adminApi(env, body);
        } catch (e3) {
          // never hand the panel an opaque 500: say what the worker actually hit
          return new Response(JSON.stringify({
            ok: false, build: WORKER_BUILD, action: String((body && body.action) || ""),
            error: "worker: " + String((e3 && e3.message) || e3).slice(0, 200),
          }), { headers: jsonHeaders() });
        }
      }
    }

    // ---------------- app api ----------------
    // Only two endpoints exist:
    //   /api/key  - key issuance (device + 60-minute bucket, one-shot token)
    //   /api/sync - the single data channel, ALWAYS encrypted (no plaintext fallback)
    if (path === "/api/key") return withDb(env, () => blobKeyIssue(env, request));
    if (path === "/api/sync") return withDb(env, () => appSync(env, request));

    return new Response("not found", { status: 404 });
  },
};

/** Run a DB-backed handler; on a table error, create the schema once and retry. */
async function withDb(env, f) {
  try {
    return await f();
  } catch (e) {
    try { await ensureSchema(env); } catch (e2) {}
    return await f();
  }
}

/* ============================ app api ============================ */

async function appAuthOk(env, request) {
  if (!env.APP_TOKEN) return true; // optional lock
  const h = request.headers.get("x-app-token") || "";
  return h === env.APP_TOKEN;
}

async function appSync(env, request) {
  const b = await request.json().catch(() => null);
  if (!b || !b.device) return new Response(JSON.stringify({ ok: false, error: "need device" }), { status: 400, headers: jsonHeaders() });
  if (!(await appAuthOk(env, request))) return new Response("unauthorized", { status: 401, headers: jsonHeaders() });
  // encrypted protocol ONLY - servers/admin data never cross the wire in plaintext.
  // A caller without a valid blob session gets enc:"h" (issue keys via /api/key first);
  // there is deliberately NO plaintext fallback anymore.
  if (request.headers.get("x-blob") === "v1" && env.BLOB_ROOT) {
    return blobSyncProtected(env, request, b);
  }
  return new Response(JSON.stringify({ ok: true, v: 2, enc: "h" }), { headers: jsonHeaders() });
}

/** Core device registration + admin payload. Used by the encrypted v2 sync only -
 *  ack receipts arrive inside the encrypted body (b.acks). */
async function syncPayloadObj(env, request, b) {
  const now = Date.now();
  if (b.connected) {
    await env.DB.prepare(
      "INSERT INTO devices (id, model, app_ver, first_seen, last_seen, ip, connected, info) VALUES (?1,?2,?3,?4,?4,?5,1,?6) " +
        "ON CONFLICT(id) DO UPDATE SET last_seen=?4, model=?2, app_ver=?3, ip=?5, connected=1, info=?6",
    )
      .bind(String(b.device), String(b.model || "").slice(0, 64), String(b.ver || 0), now, clientIp(request), JSON.stringify(b.info || {}))
      .run();
  } else {
    await env.DB.prepare(
      "INSERT INTO devices (id, model, app_ver, first_seen, last_seen, ip) VALUES (?1,?2,?3,?4,?4,?5) " +
        "ON CONFLICT(id) DO UPDATE SET last_seen=?4, model=?2, app_ver=?3, ip=?5",
    )
      .bind(String(b.device), String(b.model || "").slice(0, 64), String(b.ver || 0), now, clientIp(request))
      .run();
  }
  await env.DB.prepare("INSERT INTO events (device, type, ts) VALUES (?1,?2,?3)").bind(
    String(b.device),
    b.type || "sync",
    now,
  ).run();

  // consume the app's ack receipts delivered INSIDE the encrypted body (no plaintext
  // /api/ack exists anymore): connect/disconnect events, popup/notif seen, payload received.
  try {
    const ackArr = Array.isArray(b.acks) ? b.acks.slice(0, 64) : [];
    for (const a of ackArr) {
      const kind = String((a && a.kind) || "");
      if (kind === "event" && a.name) {
        await env.DB.prepare("INSERT INTO events (device, type, ts) VALUES (?1,?2,?3)")
          .bind(String(b.device), String(a.name).slice(0, 32), now).run();
      } else if ((kind === "popup" || kind === "notif") && a.id) {
        const ins = await env.DB.prepare("INSERT OR IGNORE INTO acks (device, popup, kind, ts) VALUES (?1,?2,?3,?4)")
          .bind(String(b.device), String(a.id), kind, now).run();
        // count popup shows once per device (INSERT OR IGNORE + changes check = idempotent on retry)
        if (kind === "popup" && ((ins.meta && ins.meta.changes) || 0) === 1) {
          const cur = Number((await getSettings(env, ["popup_shows"])).popup_shows || 0);
          await setSetting(env, "popup_shows", String(cur + 1));
        }
      } else if (kind === "payload" && a.id) {
        await env.DB.prepare("INSERT OR IGNORE INTO payload_seen (device, pid, ts) VALUES (?1,?2,?3)")
          .bind(String(b.device), Number(a.id), now).run();
      }
    }
  } catch (e) {}

  const cfg = await getSettings(env, [
    "app_enabled", "min_version", "latest_version", "latest_url",
    "popup_active", "popup_id", "popup_title", "popup_body", "popup_btn_text",
    "popup_btn_url", "popup_btn_enabled", "popup_cancel_enabled", "popup_max_shows", "popup_shows",
  ]);

  const appEnabled = cfg.app_enabled !== "0";
  const ver = Number(b.ver || 0);
  const min = Number(cfg.min_version || 0);
  const latest = Number(cfg.latest_version || 0);

  // admin block rules: by device-id / ip / device model
  const blk = await getSettings(env, ["blocked_devices", "blocked_ips", "blocked_models"]);
  const inList = (csv, v) => String(csv || "").split(",").map((x) => x.trim().toLowerCase()).filter(Boolean).includes(String(v || "").toLowerCase());
  const devId = String(b.device);
  const ipAddr = clientIp(request);
  const model = String(b.model || "");
  let notEnabled = false;
  let blockReason = "";
  if (inList(blk.blocked_devices, devId)) { notEnabled = true; blockReason = "device"; }
  else if (inList(blk.blocked_ips, ipAddr)) { notEnabled = true; blockReason = "ip"; }
  else if (inList(blk.blocked_models, model)) { notEnabled = true; blockReason = "model"; }

  let out = {
    ok: true,
    now,
    enabled: appEnabled && !notEnabled,
    blocked: blockReason,
    servers: await listServers(env),
  };
  // admin-driven server behavior flags ("1"/"0" force; "" = follow user's own toggle)
  const pf = await getSettings(env, ["ping_on_start", "flag_on_start", "auto_ping_new", "auto_flag_new"]);
  out.pflags = {
    ping_start: pf.ping_on_start || "auto",
    flag_start: pf.flag_on_start || "auto",
    auto_ping_new: pf.auto_ping_new || "auto",
    auto_flag_new: pf.auto_flag_new || "auto",
  };
  if (min > 0 && ver < min) {
    out.update = { forced: true, text: cfg.latest_version && latest > ver ? "نسخه جدید" : "برای ادامه باید اپدیت کنید", btnText: "Update", url: cfg.latest_url || "" };
  } else if (latest > ver && (cfg.latest_url || "") !== "") {
    out.update = { forced: false, text: "نسخه جدید در دسترس است", btnText: "Update", url: cfg.latest_url };
  }
  const pid = Number(cfg.popup_id || 0);
  if (cfg.popup_active === "1" && pid > 0) {
    const max = Number(cfg.popup_max_shows || 999999);
    const shown = Number(cfg.popup_shows || 0);
    const seen = await env.DB.prepare("SELECT 1 FROM popup_seen WHERE device=?1 AND popup=?2").bind(String(b.device), pid).first();
    if (shown < max && !seen) {
      out.popup = {
        id: pid,
        title: cfg.popup_title || "پیام",
        body: cfg.popup_body || "",
        btnText: cfg.popup_btn_text || "",
        btnUrl: cfg.popup_btn_url || "",
        btnEnabled: cfg.popup_btn_enabled !== "0",
        cancelEnabled: cfg.popup_cancel_enabled !== "0",
      };
    }
  }
  const nots = await env.DB.prepare("SELECT id, title, body, ts FROM notifs WHERE active=1 ORDER BY id DESC LIMIT 10").all();
  out.notifications = (nots.results || []).map((r) => ({ id: r.id, title: r.title, body: r.body, ts: r.ts }));
  const ackSeen = await env.DB.prepare("SELECT popup, kind FROM acks WHERE device=?1").bind(String(b.device)).all();
  const dismissedPopup = new Set((ackSeen.results || []).filter((r) => r.kind === "popup").map((r) => String(r.popup)));
  if (out.popup && dismissedPopup.has(String(pid))) out.popup = null;
  const dismissedNotifs = new Set((ackSeen.results || []).filter((r) => r.kind === "notif").map((r) => String(r.popup)));
  out.notifications = out.notifications.filter((n) => !dismissedNotifs.has(String(n.id)));

  // admin file/image payload - delivered once per device inside the same encrypted
  // stego carrier as everything else (raw bytes are only ever addressed by the app
  // *after* AES-GCM verified them; nothing crosses the wire in plaintext).
  try {
    const pRows = await env.DB.prepare("SELECT id, name, mime, title FROM payload_files WHERE active=1 ORDER BY id DESC LIMIT 1").all();
    const p = (pRows.results || [])[0];
    if (p) {
      const seen = await env.DB.prepare("SELECT 1 FROM payload_seen WHERE device=?1 AND pid=?2").bind(String(b.device), p.id).first();
      if (!seen) {
        const full = await env.DB.prepare("SELECT b64 FROM payload_files WHERE id=?1").bind(p.id).first();
        if (full && full.b64) {
          out.file = { id: p.id, kind: "f", title: p.title || "", name: p.name || "", mime: p.mime || "", b64: full.b64 };
        }
      }
    }
  } catch (e) {}
  return out;
}

async function listServers(env) {
  const rows = await env.DB.prepare(
    "SELECT id, name, address, port, link, category, msg FROM servers WHERE active=1 ORDER BY ord ASC, id ASC LIMIT 500",
  ).all();
  return (rows.results || []).map((r) => ({
    id: String(r.id),
    name: r.name,          //admin-assigned - never from the config (#NAME tag)
    address: r.address,
    port: r.port || 443,
    link: r.link,
    category: r.category || "",
    msg: r.msg || "",
  }));
}

function clientIp(request) {
  return request.headers.get("cf-connecting-ip") || "";
}

/* ============================ settings store ============================ */

const DEFAULTS = { app_enabled: "1" };

async function getSettings(env, keys) {
  const out = { ...DEFAULTS };
  const ph = keys.map((k, i) => `?${i + 1}`).join(",");
  try {
    const rows = await env.DB.prepare(`SELECT key, value FROM settings WHERE key IN (${ph})`).bind(...keys).all();
    for (const r of rows.results || []) out[r.key] = r.value;
  } catch (e) {}
  return out;
}

async function setSetting(env, key, value) {
  await env.DB.prepare(
    "INSERT INTO settings (key, value, updated_at) VALUES (?1,?2,?3) ON CONFLICT(key) DO UPDATE SET value=?2, updated_at=?3",
  ).bind(key, value, Date.now()).run();
}

async function getCategories(env) {
  const s = await getSettings(env, ["categories"]);
  let list;
  try { list = JSON.parse(s.categories || "[]"); } catch (e) { list = []; }
  list = (Array.isArray(list) ? list : []).filter((c) => typeof c === "string").map((c) => c.trim()).filter(Boolean).slice(0, 64);
  if (list.length === 0) list = ["VIP", "Normal"];
  return list;
}

/* ============================ admin actions ============================ */

async function adminApi(env, b) {
  const act = b.action;
  try {
    const cats = await getCategories(env);
    switch (act) {
      case "srv.add": {
        const name = String(b.name || "").trim().slice(0, 64) || `srv-${(b.seq ?? Date.now()) % 10000}`;
        await env.DB.prepare(
          "INSERT INTO servers (name, address, port, link, category, ord, active, msg) VALUES (?1,?2,?3,?4,?5,?6,1,?7)",
        ).bind(name, String(b.address || "").slice(0, 256), Number(b.port || 443), String(b.link || "").slice(0, 4096),
          (String(b.category || "").trim() || "").slice(0, 64), Number(b.ord || 99), String(b.msg || "").slice(0, 200)).run();
        srvAllInvalidate();
        return okJson();
      }
      case "srv.bulk": {
        let seq = Number((await env.DB.prepare("SELECT COUNT(*) c FROM servers").first()).c) + 1;
        const defCat = String(b.category || "").trim().slice(0, 64) || (cats[0] || "Normal");
        const defMsg = String(b.msg || "").trim().slice(0, 200);
        const INS = "INSERT INTO servers (name, address, port, link, category, ord, active, msg) VALUES (?1,?2,?3,?4,?5,?6,1,?7)";
        const rows = [];
        const endpointOf = (l) => {
          const at = l.indexOf("@");
          if (at < 0) return ["0.0.0.0", 443];
          const seg = l.slice(at + 1).split(/[/?#]/)[0];
          const c = seg.lastIndexOf(":");
          if (c < 0) return [seg, 443];
          return [seg.slice(0, c), Number(seg.slice(c + 1)) || 443];
        };
        for (const rawPiece of String(b.text || "").split("\n")) {
          const parts = rawPiece.split("|").map((x) => x.trim());
          let name = "", link = "", cat = "", msg = "";
          if (parts.length >= 4) { name = parts[0]; link = parts[1]; cat = parts[2]; msg = parts[3]; }
          else if (parts.length === 3) { name = parts[0]; link = parts[1]; cat = parts[2]; }
          else if (parts.length === 2) { name = parts[0]; link = parts[1]; }
          else { link = parts[0] || ""; }
          if (!link) continue;
          // the link already carries its endpoint: keep it in address/port so the panel can
          // list servers without shipping the whole link string
          const ep = endpointOf(link);
          rows.push([name.slice(0, 64) || `srv-${rows.length + seq}`, ep[0].slice(0, 256), ep[1], link.slice(0, 4096),
            (cat || defCat).slice(0, 64), seq++, (msg || defMsg).slice(0, 200)]);
        }
        // one await per server turned a 500-line paste into 500 sequential D1 round trips
        // (minutes, and the worker can hit its wall-clock limit mid-import); batch in chunks
        let added = 0;
        for (let i = 0; i < rows.length; i += 95) {
          const chunk = rows.slice(i, i + 95);
          try {
            await env.DB.batch(chunk.map((v) => env.DB.prepare(INS).bind(...v)));
            added += chunk.length;
          } catch (e) {
            for (const v of chunk) {
              try { await env.DB.prepare(INS).bind(...v).run(); added++; } catch (e2) {}
            }
          }
        }
        srvAllInvalidate();
        return okJson({ added });
      }
      case "srv.del":
        await env.DB.prepare("DELETE FROM servers WHERE id=?1").bind(Number(b.id)).run();
        srvAllInvalidate();
        return okJson();
      case "srv.active":
        await env.DB.prepare("UPDATE servers SET active=?2 WHERE id=?1").bind(Number(b.id), b.on ? 1 : 0).run();
        srvAllInvalidate();
        return okJson();
      case "srv.rename":
        await env.DB.prepare("UPDATE servers SET name=?2, category=?3, msg=?4 WHERE id=?1").bind(
          Number(b.id), String(b.name).slice(0, 64), String(b.category || "").slice(0, 64),
          String(b.msg == null ? "" : b.msg).slice(0, 200)).run();
        srvAllInvalidate();
        return okJson();
      case "srv.ord": {
        const rows = await env.DB.prepare("SELECT id, ord FROM servers ORDER BY ord ASC, id ASC LIMIT 500").all();
        const list = rows.results || [];
        const idx = list.findIndex((r) => Number(r.id) === Number(b.id));
        if (idx < 0) return okJson();
        const swap = b.up ? idx - 1 : idx + 1;
        if (swap < 0 || swap >= list.length) return okJson();
        await env.DB.prepare("UPDATE servers SET ord=?2 WHERE id=?1").bind(list[idx].id, swap + 1).run();
        await env.DB.prepare("UPDATE servers SET ord=?2 WHERE id=?1").bind(list[swap].id, idx + 1).run();
        srvAllInvalidate();
        return okJson();
      }
      case "srv.catmove":
        await env.DB.prepare("UPDATE servers SET category=?2 WHERE category=?1").bind(
          String(b.from || "").slice(0, 64), String(b.to || "").slice(0, 64)).run();
        srvAllInvalidate();
        return okJson();
      case "cats.set": {
        const list = (Array.isArray(b.cats) ? b.cats : []).map(String).map((c) => c.trim().slice(0, 64)).filter(Boolean).slice(0, 64);
        await setSetting(env, "categories", JSON.stringify(list));
        srvAllInvalidate();
        return okJson();
      }
      case "set.app":
        await setSetting(env, "app_enabled", b.on ? "1" : "0");
        srvAllInvalidate();
        return okJson();
      case "set.flags": {
        const legal = { ping_on_start: 1, flag_on_start: 1, auto_ping_new: 1, auto_flag_new: 1 };
        for (const [k, v] of Object.entries(b.flags || {})) {
          if (legal[k] && ["1", "0", "auto"].includes(String(v))) {
            await setSetting(env, k, String(v));
          }
        }
        srvAllInvalidate();
        return okJson();
      }
      case "dev.block": {
        // kind: device | ip | model  — toggles membership in the respective block list
        const kind = String(b.kind || "device");
        const keyMap = { device: "blocked_devices", ip: "blocked_ips", model: "blocked_models" };
        const skey = keyMap[kind];
        if (!skey) return okJson({ ok: false, error: "bad kind" });
        const val = String(b.id || "").trim().toLowerCase().slice(0, 128);
        if (!val) return okJson({ ok: false, error: "empty id" });
        const c = await getSettings(env, [skey]);
        const set = new Set(String(c[skey] || "").split(",").map((x) => x.trim().toLowerCase()).filter(Boolean));
        if (b.on) set.add(val); else set.delete(val);
        // caller may update the friendly display name of the device record
        if (b.model && kind === "device") {
          await env.DB.prepare("UPDATE devices SET model=?2 WHERE id=?1").bind(String(b.model).slice(0, 64), val).run();
        }
        await setSetting(env, skey, [...set].join(","));
        return okJson();
      }
      case "dev.blocklists": {
        const clean = (t) => [...new Set(String(t || "").split(",").map((x) => x.trim().toLowerCase()).filter(Boolean))].slice(0, 500).join(",");
        await setSetting(env, "blocked_devices", clean(b.devices));
        await setSetting(env, "blocked_ips", clean(b.ips));
        await setSetting(env, "blocked_models", clean(b.models));
        return okJson();
      }
      case "dev.del":
        await env.DB.prepare("DELETE FROM devices WHERE id=?1").bind(String(b.id || "").slice(0, 128)).run();
        return okJson();
      case "set.update":
        await setSetting(env, "min_version", String(Number(b.minVersion || 0)));
        await setSetting(env, "latest_version", String(Number(b.latestVersion || 0)));
        await setSetting(env, "latest_url", String(b.latestUrl || "").slice(0, 512));
        srvAllInvalidate();
        return okJson();
      case "set.popup": {
        const id = Date.now();
        await setSetting(env, "popup_id", String(id));
        await setSetting(env, "popup_active", b.active ? "1" : "0");
        await setSetting(env, "popup_title", String(b.title || "").slice(0, 120));
        await setSetting(env, "popup_body", String(b.body || "").slice(0, 2000));
        await setSetting(env, "popup_btn_text", String(b.btnText || "").slice(0, 64));
        await setSetting(env, "popup_btn_url", String(b.btnUrl || "").slice(0, 512));
        await setSetting(env, "popup_btn_enabled", b.btnEnabled ? "1" : "0");
        await setSetting(env, "popup_cancel_enabled", b.cancelEnabled ? "1" : "0");
        await setSetting(env, "popup_max_shows", String(Number(b.maxShows || 0)));
        await setSetting(env, "popup_shows", "0");
        return okJson();
      }
      case "popup.state": {
        const c = await getSettings(env, ["popup_active", "popup_title", "popup_body", "popup_btn_text",
          "popup_btn_url", "popup_btn_enabled", "popup_cancel_enabled", "popup_shows", "popup_max_shows", "popup_id"]);
        return okJson({ popup: c });
      }
      case "cfg.load": {
        const cfg = await getSettings(env, ["min_version", "latest_version", "latest_url", "app_enabled", "ping_on_start", "flag_on_start", "auto_ping_new", "auto_flag_new"]);
        return okJson({
          cfg, cats,
          pflags: {
            ping_on_start: cfg.ping_on_start || "auto",
            flag_on_start: cfg.flag_on_start || "auto",
            auto_ping_new: cfg.auto_ping_new || "auto",
            auto_flag_new: cfg.auto_flag_new || "auto",
          },
        });
      }
      case "notif.add":
        await env.DB.prepare("INSERT INTO notifs (title, body, ts, active) VALUES (?1,?2,?3,1)")
          .bind(String(b.title || "").slice(0, 120), String(b.body || "").slice(0, 2000), Date.now()).run();
        return okJson();
      case "notif.off":
        await env.DB.prepare("UPDATE notifs SET active=0 WHERE id=?1").bind(Number(b.id)).run();
        return okJson();
      case "notif.list": {
        const rows = await env.DB.prepare("SELECT * FROM notifs ORDER BY id DESC LIMIT 50").all();
        return new Response(JSON.stringify({ ok: true, notifs: rows.results }), { headers: jsonHeaders() });
      }
      case "stats": {
        const dayStart = new Date().setHours(0, 0, 0, 0);
        const onlineCut = Date.now() - 150 * 1000;
        const total = await env.DB.prepare("SELECT COUNT(*) c FROM devices").first();
        const online = await env.DB.prepare("SELECT COUNT(*) c FROM devices WHERE last_seen>?1").bind(onlineCut).first();
        const onlineConn = await env.DB.prepare("SELECT COUNT(*) c FROM devices WHERE last_seen>?1 AND connected=1").bind(onlineCut).first();
        const connectsToday = await env.DB.prepare("SELECT COUNT(*) c FROM events WHERE ts>?1 AND type='connect'").bind(dayStart).first();
        const syncsToday = await env.DB.prepare("SELECT COUNT(*) c FROM events WHERE ts>?1").bind(dayStart).first();
        const act1 = await env.DB.prepare("SELECT COUNT(*) c FROM servers WHERE active=1").first();
        const deact = await env.DB.prepare("SELECT COUNT(*) c FROM servers WHERE active=0").first();
        return new Response(JSON.stringify({ ok: true, stats: {
          devices: total.c,
          online: online.c,
          onlineConnected: onlineConn.c,
          offline: total.c - online.c,
          connectsToday: connectsToday ? connectsToday.c : 0,
          syncsToday: syncsToday ? syncsToday.c : 0,
          activeServers: act1.c,
          offServers: deact.c,
        } }), { headers: jsonHeaders() });
      }
      case "payload.add": {
        // raw admin input (any file: image/pdf/mp3/...) -> worker packs it itself:
        // JSON -> zlib -> AES-256-GCM(Kc per device+bucket) -> homoglyph stego carrier.
        const b64 = String(b.b64 || "").replace(/\s/g, "");
        const size = Number(b.size || Math.floor(b64.length * 3 / 4));
        if (b64.length < 8 || size <= 0) return okJson({ ok: false, error: "empty file" });
        if (b64.length > 61440) return okJson({ ok: false, error: "file too large - max 45KB (D1 row limit)" });
        const content = bl_b64uDecode(b64);
        if (!content || content.length === 0) return okJson({ ok: false, error: "invalid base64" });
        await env.DB.prepare(
          "INSERT INTO payload_files (name, mime, size, title, b64, active, ts) VALUES (?1,?2,?3,?4,?5,1,?6)",
        ).bind(
          String(b.name || "file.bin").slice(0, 128),
          String(b.mime || "application/octet-stream").slice(0, 96),
          content.length,
          String(b.title || "").slice(0, 120),
          b64,
          Date.now(),
        ).run();
        return okJson({ size: content.length });
      }
      case "payload.list": {
        const rows = await env.DB.prepare(
          "SELECT id, name, mime, size, title, active, ts FROM payload_files ORDER BY id DESC LIMIT 20",
        ).all();
        const delivered = await env.DB.prepare("SELECT pid, COUNT(*) c FROM payload_seen GROUP BY pid").all();
        const dMap = {};
        for (const r of delivered.results || []) dMap[String(r.pid)] = r.c;
        return new Response(JSON.stringify({ ok: true, payloads: rows.results || [], delivered: dMap }), { headers: jsonHeaders() });
      }
      case "payload.off":
        await env.DB.prepare("UPDATE payload_files SET active=0 WHERE id=?1").bind(Number(b.id)).run();
        return okJson();
      case "payload.on":
        await env.DB.prepare("UPDATE payload_files SET active=0").run();
        await env.DB.prepare("UPDATE payload_files SET active=1 WHERE id=?1").bind(Number(b.id)).run();
        return okJson();
      case "payload.resend": {
        // clear dedupe state so every device downloads the payload again on its next sync
        await env.DB.prepare("UPDATE blob_sessions SET last_payload=''").run();
        await env.DB.prepare("DELETE FROM payload_seen WHERE pid=?1").bind(Number(b.id)).run();
        return okJson();
      }
      case "clear.last_payload":
        await env.DB.prepare("UPDATE blob_sessions SET last_payload=''").run();
        return okJson();
      case "srv.link": {
        // the fat link column is deliberately NOT in srv.all - fetch one row's link on demand
        const one = await env.DB.prepare("SELECT link FROM servers WHERE id=?1").bind(Number(b.id)).first();
        return new Response(JSON.stringify({ ok: true, link: (one && one.link) || "" }), { headers: jsonHeaders() });
      }
      case "srv.list": {
        const rows = await env.DB.prepare("SELECT * FROM servers ORDER BY ord ASC, id ASC LIMIT 500").all();
        return new Response(JSON.stringify({ ok: true, servers: rows.results,
          cats,
          app: (await getSettings(env, ["app_enabled"])).app_enabled !== "0" }), { headers: jsonHeaders() });
      }
      case "ver": {
        // one-call diagnosis of what is deployed and what the database really looks like
        let cols = [], cnt = -1, dbErr = "";
        try { cols = ((await env.DB.prepare("PRAGMA table_info(servers)").all()).results || []).map((c) => c.name); } catch (e) { dbErr = String((e && e.message) || e).slice(0, 160); }
        try { cnt = Number((await env.DB.prepare("SELECT COUNT(*) c FROM servers").first()).c); } catch (e) { }
        return new Response(JSON.stringify({
          ok: true, build: WORKER_BUILD, cols, count: cnt, dbErr,
          pwSet: !!env.ADMIN_PASSWORD, dbBound: !!env.DB,
        }), { headers: jsonHeaders() });
      }
      case "dbfix": {
        // heal the schema right now and report what really exists afterwards
        try { await ensureSchema(env, true); } catch (e) {}
        let tables = [], err = "";
        try {
          tables = ((await env.DB.prepare("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name").all()).results || []).map((r) => r.name);
        } catch (e) { err = String((e && e.message) || e).slice(0, 160); }
        return new Response(JSON.stringify({
          ok: true, build: WORKER_BUILD, tables, err,
          havePayloadFiles: tables.indexOf("payload_files") >= 0,
        }), { headers: jsonHeaders() });
      }
      case "srv.all": {
        // one fast round-trip for the whole admin bootstrap: servers + cats + kill-switch + behavior flags + update cfg
        const now3 = Date.now();
        if ((SRV_ALL_CACHE.v != null) && now3 - SRV_ALL_CACHE.t < SRV_ALL_TTL) {
          return new Response(JSON.stringify(SRV_ALL_CACHE.v), { headers: jsonHeaders() });
        }
        const keysAll = ["app_enabled", "min_version", "latest_version", "latest_url", "ping_on_start", "flag_on_start", "auto_ping_new", "auto_flag_new", "categories"];
        const phAll = keysAll.map((k, i) => `?${i + 1}`).join(",");
        let batchOut = null;
        try {
          batchOut = await env.DB.batch([
            env.DB.prepare(`SELECT key, value FROM settings WHERE key IN (${phAll})`).bind(...keysAll),
            env.DB.prepare("SELECT * FROM servers ORDER BY ord ASC, id ASC LIMIT 500"),
          ]);
        } catch (e) {
          batchOut = null;
        }
        if (!batchOut || batchOut.length < 2) {
          // schema not ready / older shape - fall through to the lazy per-query path
          const early = await getSettings(env, ["app_enabled", "min_version", "latest_version", "latest_url", "ping_on_start", "flag_on_start", "auto_ping_new", "auto_flag_new"]);
          let rowsS = { results: [] };
          try { rowsS = await env.DB.prepare("SELECT * FROM servers ORDER BY ord ASC, id ASC LIMIT 500").all(); } catch (e2) {}
          batchOut = null;
          return finishSrvAll(env, now3, { settings: early, servers: rowsS.results || [] });
        }
        const merged = {};
        for (const r of (batchOut[0].results || [])) merged[r.key] = r.value;
        return finishSrvAll(env, now3, { settings: merged, servers: batchOut[1].results || [] });
      }
      case "devices.list": {
        let rows;
        try {
          rows = await env.DB.prepare(
            "SELECT id, model, app_ver, first_seen, last_seen, ip, connected, info FROM devices ORDER BY last_seen DESC LIMIT 200",
          ).all();
        } catch (e) {
          try { await ensureSchema(env); } catch (e2) {}
          rows = await env.DB.prepare(
            "SELECT id, model, app_ver, first_seen, last_seen, ip, connected, info FROM devices ORDER BY last_seen DESC LIMIT 200",
          ).all();
        }
        const bl = await getSettings(env, ["blocked_devices", "blocked_ips", "blocked_models"]);
        return new Response(JSON.stringify({ ok: true, cut: Date.now() - 150000, devices: rows.results,
          blocked: {
            devices: String(bl.blocked_devices || "").split(",").filter(Boolean),
            ips: String(bl.blocked_ips || "").split(",").filter(Boolean),
            models: String(bl.blocked_models || "").split(",").filter(Boolean),
          } }), { headers: jsonHeaders() });
      }
      default:
        return okJson({ ok: false, error: "unknown action: " + act });
    }
  } catch (e) {
    return okJson({ ok: false, error: String(e).slice(0, 300) });
  }
}

function okJson(extra = {}) {
  return new Response(JSON.stringify({ ok: true, ...extra }), { headers: jsonHeaders() });
}

/* ---- srv.all cache: one 2.5s-lived snapshot so repeated panel calls are instant ---- */
const SRV_ALL_TTL = 2500;
let SRV_ALL_CACHE = { t: 0, v: null };
function srvAllInvalidate() { SRV_ALL_CACHE = { t: 0, v: null }; }

/** Assemble the srv.all bootstrap response from one settings lookup + one servers query. */
async function finishSrvAll(env, cacheNow, d) {
  const early = d.settings || {};
  const raw = Array.isArray(d.servers) ? d.servers : ((d.servers && d.servers.results) || []);
  // Light rows only: the fat link column stays in D1 and the panel fetches one link when it is
  // copied. SELECT * (not a column list) keeps this working on databases created before the
  // msg/category ALTERs - a missing column must degrade, not 500 the whole panel.
  const servers = raw.map((r) => ({
    id: r.id,
    name: r.name || "",
    address: r.address || "",
    port: r.port || 443,
    category: r.category || "",
    msg: r.msg || "",
    active: r.active === undefined ? 1 : r.active,
    ord: r.ord,
  }));
  let catsList;
  try { catsList = JSON.parse(early.categories || "[]"); } catch (e) { catsList = []; }
  catsList = (Array.isArray(catsList) ? catsList : []).filter((c) => typeof c === "string").map((c) => c.trim()).filter(Boolean).slice(0, 64);
  if (catsList.length === 0) catsList = ["VIP", "Normal"];
  const sAll = {
    ok: true,
    build: WORKER_BUILD,
    servers: servers,
    cats: catsList,
    app: early.app_enabled !== "0",
    cfg: {
      min_version: early.min_version || "0",
      latest_version: early.latest_version || "0",
      latest_url: early.latest_url || "",
      app_enabled: early.app_enabled || "1",
    },
    pflags: {
      ping_on_start: early.ping_on_start || "auto",
      flag_on_start: early.flag_on_start || "auto",
      auto_ping_new: early.auto_ping_new || "auto",
      auto_flag_new: early.auto_flag_new || "auto",
    },
  };
  SRV_ALL_CACHE = { t: cacheNow, v: sAll };
  return new Response(JSON.stringify(sAll), { headers: jsonHeaders() });
}

/* ============================ auth ============================ */

function pwOk(env, b) {
  if (!env.ADMIN_PASSWORD) return false;
  return typeof b === "object" && b !== null && typeof b.pw === "string" && b.pw === env.ADMIN_PASSWORD;
}

/* ============================ html / css / panel ============================ */

function jsonHeaders(extra) {
  const h = new Headers({ "content-type": "application/json; charset=utf-8" });
  if (extra) h.append(extra[0], extra[1]);
  return h;
}

const THEME_CSS = `:root{--bg:#0b0f14;--card:#141a23;--card2:#0f141c;--line:#222a38;--ok:#2bd48a;--okd:#1a9e63;
--txt:#e8ecf1;--sub:#8b96a8;--red:#e05c5c;--amber:#e5b457;--blue:#5b8cff;--r:14px}
*{box-sizing:border-box}
body{background:radial-gradient(1100px 500px at 85% -10%,rgba(43,212,138,.08),transparent),var(--bg);color:var(--txt);
font-family:system-ui,'Segoe UI',Tahoma,sans-serif;margin:0;min-height:100vh}
body:before{content:"";position:fixed;inset:0;z-index:-1;opacity:.5;pointer-events:none;
background-image:linear-gradient(rgba(43,212,138,.03) 1px,transparent 1px),linear-gradient(90deg,rgba(43,212,138,.03) 1px,transparent 1px);
background-size:34px 34px}
::selection{background:rgba(43,212,138,.3)}
::-webkit-scrollbar{width:8px;height:8px}
::-webkit-scrollbar-thumb{background:#263042;border-radius:8px}
header{position:sticky;top:0;z-index:60;backdrop-filter:blur(12px);
border-bottom:1px solid var(--line);padding:12px 18px;display:flex;align-items:center;gap:12px;
background:linear-gradient(135deg,rgba(25,33,45,.95),rgba(11,15,20,.9))}
.logo{width:38px;height:38px;border-radius:12px;background:linear-gradient(135deg,var(--ok),#22e07a);
display:flex;align-items:center;justify-content:center;font-weight:900;color:#04281a;font-size:15px;
box-shadow:0 4px 18px rgba(43,212,138,.35)}
header h1{font-size:16px;margin:0;flex:1}
header h1 small{display:block;color:var(--sub);font-size:11px;font-weight:400;direction:ltr;text-align:right}
.layout{display:flex;max-width:1100px;margin:0 auto;min-height:calc(100vh - 63px)}
nav{width:120px;flex-shrink:0;border-left:1px solid var(--line);padding:12px 8px;display:flex;flex-direction:column;gap:4px}
nav button{display:flex;align-items:center;gap:8px;background:transparent;color:var(--sub);border:0;border-radius:11px;
padding:9px 10px;font-weight:600;font-size:12px;cursor:pointer;width:100%;text-align:right;justify-content:flex-start;
transition:background .18s,color .18s,transform .18s}
nav button:hover{background:rgba(43,212,138,.08);color:var(--txt);filter:none;transform:translateX(-3px)}
nav button.on{background:linear-gradient(135deg,rgba(43,212,138,.18),rgba(43,212,138,.05));color:var(--ok);
border:1px solid rgba(43,212,138,.35)}
nav .sp{height:14px}
nav .badge2{margin-right:auto;background:var(--card2);border:1px solid var(--line);color:var(--sub);
border-radius:99px;padding:1px 7px;font-size:9px;font-weight:700}
main{flex:1;padding:20px;min-width:0}
.tab{display:none}
.tab.on{display:block}
.card{background:var(--card);border:1px solid var(--line);border-radius:var(--r);padding:18px;margin-bottom:16px;
box-shadow:0 2px 14px rgba(0,0,0,.22)}
.card h2{font-size:14px;margin:0 0 14px;color:var(--ok);display:flex;align-items:center;gap:8px}
.card h3{font-size:13px;margin:16px 0 10px;color:var(--blue)}
.cnt{background:var(--card2);border:1px solid var(--line);color:var(--sub);font-size:11px;border-radius:99px;padding:2px 10px;font-weight:600}
.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(105px,1fr));gap:10px}
.stat{background:linear-gradient(180deg,var(--card2),#0d1118);border:1px solid var(--line);border-radius:12px;
padding:14px 10px;text-align:center;transition:transform .15s}
.stat:hover{transform:translateY(-2px)}
.stat b{display:block;font-size:22px;color:var(--ok);font-weight:800}
.stat span{color:var(--sub);font-size:11px}
.stat b.r{color:var(--red)}
.stat b.a{color:var(--amber)}
.stat b.b{color:var(--blue)}
input,select,textarea{background:var(--card2);border:1px solid var(--line);border-radius:10px;color:var(--txt);
padding:9px 11px;width:100%;font-family:inherit;font-size:13px;outline:none;transition:border .15s}
input:focus,select:focus,textarea:focus{border-color:var(--okd)}
textarea{resize:vertical;line-height:1.7}
button{background:linear-gradient(135deg,var(--ok),var(--okd));color:#04281a;border:0;border-radius:10px;
padding:9px 16px;font-weight:700;cursor:pointer;font-family:inherit;font-size:13px;transition:filter .15s,transform .1s}
button:hover{filter:brightness(1.08)}
button:active{transform:scale(.97)}
button.gray{background:var(--line);color:var(--txt)}
button.red{background:linear-gradient(135deg,#e05c5c,#b03a3a);color:#fff}
button.sm{padding:5px 11px;font-size:12px;border-radius:8px}
.row2{display:flex;gap:10px;flex-wrap:wrap}.row2>div{flex:1;min-width:150px}
.lbl{font-size:11px;color:var(--sub);margin:10px 0 4px}
.lbl:first-child{margin-top:0}
details.acc{background:var(--card2);border:1px solid var(--line);border-radius:12px;margin-top:10px}
details.acc summary{cursor:pointer;padding:11px 14px;font-weight:700;font-size:13px;color:var(--ok);list-style:none;
display:flex;align-items:center;gap:8px;user-select:none}
details.acc summary::-webkit-details-marker{display:none}
details.acc summary:after{content:"▾";margin-right:auto;color:var(--sub);transition:transform .2s}
details.acc[open] summary:after{transform:rotate(180deg)}
details.acc .body{padding:0 14px 14px}
.chip{display:inline-flex;align-items:center;gap:4px;background:var(--card2);border:1px solid var(--line);border-radius:99px;
padding:5px 13px;font-size:12px;margin:3px;cursor:pointer;transition:all .15s;user-select:none}
.chip:hover{border-color:var(--okd)}
.chip.on{background:rgba(43,212,138,.15);border-color:var(--ok);color:var(--ok);font-weight:700}
.chip .x{color:var(--sub);padding:0 3px}
.chip .x:hover{color:var(--red)}
.srv{background:var(--card2);border:1px solid var(--line);border-radius:12px;padding:10px 14px;margin-top:8px;
display:flex;align-items:center;gap:8px;flex-wrap:wrap;transition:border .15s}
.srv:hover{border-color:#2f3a4f}
.srv.off{opacity:.45}
.srv input{border:none;background:none;padding:4px}
.srv .nm{flex:1;min-width:120px;font-weight:600;color:var(--txt)}
.srv .ct{width:105px;color:var(--amber)}
.srv .note-ind{font-size:10px;background:rgba(91,140,255,.15);color:var(--blue);border-radius:99px;padding:2px 9px;font-weight:700}
.srv .lnk{font-size:10px;color:var(--sub);flex-basis:100%;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;
direction:ltr;text-align:left;cursor:copy}
.srv .lnk:hover{color:var(--ok)}
.badge{font-size:10px;border-radius:99px;padding:3px 10px;font-weight:700;white-space:nowrap}
.b-on{background:rgba(43,212,138,.15);color:var(--ok)}
.b-off{background:rgba(224,92,92,.15);color:var(--red)}
.b-y{background:rgba(229,180,87,.15);color:var(--amber)}
.switch{position:relative;display:inline-block;width:46px;height:26px}
.switch input{opacity:0;width:0;height:0}
.switch span{position:absolute;inset:0;background:var(--line);border-radius:99px;transition:.2s;cursor:pointer}
.switch span:before{content:"";position:absolute;width:20px;height:20px;border-radius:50%;background:var(--sub);top:3px;right:3px;transition:.2s}
.switch input:checked+span{background:rgba(43,212,138,.3)}
.switch input:checked+span:before{background:var(--ok);transform:translateX(-20px)}
.search{background:var(--card2);border:1px solid var(--line);border-radius:99px;padding:8px 18px;margin-bottom:6px}
table{width:100%;border-collapse:collapse;font-size:12px}
td,th{padding:8px 6px;border-bottom:1px solid var(--line);text-align:right}
th{color:var(--sub);font-size:11px;font-weight:600}
.toolbar{display:flex;gap:10px;align-items:center;flex-wrap:wrap;margin-bottom:8px}
.toolbar label{font-size:12px;color:var(--sub);display:flex;align-items:center;gap:7px}
.errbox{display:none;background:rgba(224,92,92,.12);border:1px solid rgba(224,92,92,.4);color:var(--red);
border-radius:10px;padding:10px 14px;font-size:12px;margin-bottom:10px;direction:ltr;text-align:left;overflow:auto}
.toast{position:fixed;bottom:18px;left:18px;background:var(--ok);color:#04281a;padding:11px 20px;border-radius:12px;
font-weight:800;box-shadow:0 6px 24px rgba(0,0,0,.4);display:none;z-index:99}
.toast.err{background:var(--red);color:#fff}
.empty{color:var(--sub);font-size:12px;text-align:center;padding:20px}
.note{font-size:11px;color:var(--sub);line-height:1.9}
code{background:var(--card2);border:1px solid var(--line);border-radius:6px;padding:1px 6px;font-size:10px;direction:ltr;display:inline-block}
@media(max-width:760px){nav{width:auto;border-left:0;border-bottom:1px solid var(--line);flex-direction:row;overflow-x:auto;
padding:8px 10px}.layout{flex-direction:column}nav button:hover{transform:none}nav .badge2{display:none}}

/* --------- animations --------- */
@keyframes fadeUp{from{opacity:0;transform:translateY(10px)}to{opacity:1;transform:translateY(0)}}
@keyframes fadeIn{from{opacity:0}to{opacity:1}}
@keyframes pop{from{transform:scale(.92);opacity:0}to{transform:scale(1);opacity:1}}
@keyframes pulse{0%,100%{opacity:1}50%{opacity:.45}}
.card{animation:fadeUp .32s ease both}
.srv{animation:fadeIn .25s ease both}
.srv:active{transform:scale(.99)}
.tab.on{animation:fadeIn .25s ease}
#lock .card{animation:pop .3s ease both}
.stat b{transition:scale .2s}
.stat{animation:pop .3s ease both}
.toolbar{animation:fadeIn .2s ease both}
.badge.b-on{animation:pulse 2.4s ease infinite}
nav button.on{animation:pop .25s ease}`;

function panelPage(env) {
  const html = `<!doctype html><html lang=fa dir=rtl><head><meta charset=utf-8>
<meta name=viewport content="width=device-width,initial-scale=1"><title>ARBAB Panel</title>
<style>${THEME_CSS}</style></head><body>
<header><div class=logo>A</div>
<h1>ARBAB Admin<small>god-panel.lastofanarchy.workers.dev</small></h1>
<button class=sm id=btn_refresh onclick=manualRefresh() title="بروزرسانی همه بخش‌ها">🔄 بروزرسانی</button>
<button class="gray sm" onclick=logout() id=btn_exit>خروج</button></header>

<div id=lock style="position:fixed;inset:0;background:rgba(11,15,20,.97);z-index:200;display:flex;align-items:center;justify-content:center">
<div class=card style="width:min(340px,92vw);text-align:center">
<div class=logo style="margin:0 auto 12px;width:52px;height:52px;font-size:20px;border-radius:16px">A</div>
<h2 style=justify-content:center>ARBAB Admin</h2>
<p class=note>رمز پنل را وارد کنید</p>
<input id=pass type=password placeholder="رمز" autocapitalize=off style=text-align:center onkeydown="if(event.key==='Enter')unlock()">
<button onclick=unlock() style="width:100%;margin-top:14px">ورود</button>
<p class=note id=lock_err style="color:var(--red);display:none"></p>
</div></div>

<div id=app style="opacity:.25;pointer-events:none;filter:blur(6px)">
<div class=layout>
<nav id=sidebar>
<button class=on data-tab=dash onclick="tab('dash')">📊 داشبورد</button>
<button data-tab=srv      onclick="tab('srv')">🖥 سرورها</button>
<button data-tab=cat      onclick="tab('cat')">🗂 دسته‌ها</button>
<button data-tab=msg      onclick="tab('msg')">💬 پیام و اپدیت</button>
<button data-tab=dev      onclick="tab('dev')">👥 دستگاه‌ها</button>
</nav>
<main>

<div id=err class=errbox></div>

<!-- ================= DASHBOARD ================= -->
<div class="tab on" id=tab_dash>
<div class=card><h2>📊 آمار <span class=cnt>با دکمه 🔄 از نوار بالا</span></h2>
<div class=grid id=stats><div class=empty>در حال دریافت...</div></div>
<button class="gray sm" onclick=loadStats() style=margin-top:12px>🔄 بروزرسانی</button></div>

<div class=card><h2>🛡 کنترل کلی اپ (Kill switch)</h2>
<div class=toolbar>
<label class=switch><input type=checkbox id=app_sw onchange=appToggle(this.checked)><span></span></label>
<span id=app_state class=note></span><span id=wrkr_build class=note></span>
</div>
<p class=note>وقتی خاموش باشه، اپ روی همه دستگاه‌ها قفل میشه.</p></div>

<div class=card><h2>⚡ رفتار خودکار سرورها (فرمان به همه اپ‌ها)</h2>
<div class=grid style="grid-template-columns:repeat(auto-fit,minmax(200px,1fr))">
<div><div class=lbl>پینگ هنگام باز شدن اپ</div><select id=f_ping_start><option value=auto>اختیار کاربر</option><option value=1>روشن (اجباری)</option><option value=0>خاموش (اجباری)</option></select></div>
<div><div class=lbl>پرچم کشور هنگام باز شدن</div><select id=f_flag_start><option value=auto>اختیار کاربر</option><option value=1>روشن (اجباری)</option><option value=0>خاموش (اجباری)</option></select></div>
<div><div class=lbl>پینگ سرورهای جدید</div><select id=f_auto_ping_new><option value=auto>اختیار کاربر</option><option value=1>روشن (اجباری)</option><option value=0>خاموش (اجباری)</option></select></div>
<div><div class=lbl>پرچم کشور سرورهای جدید</div><select id=f_auto_flag_new><option value=auto>اختیار کاربر</option><option value=1>روشن (اجباری)</option><option value=0>خاموش (اجباری)</option></select></div>
</div>
<button onclick=saveFlags() style=margin-top:10px>ذخیره فرمان‌ها</button>
<p class=note>«روشن/خاموش (اجباری)» = این رفتار روی همه اپ‌ها به همین صورت اجباری میشه؛ «اختیار کاربر» = هر کاربر از تنظیمات خودش تصمیم میگیره.</p></div>
</div>

<!-- ================= SERVERS ================= -->
<div class=tab id=tab_srv>
<div class=card>
<h2>🖥 سرورها <span class=cnt id=srv_cnt></span></h2>
<div id=srv_list class=empty>در حال دریافت...</div>

<details class=acc><summary>➕ سرور جدید</summary><div class=body>
<div class=row2>
<div><div class=lbl>نام (از خودت - نه از لینک)</div><input id=s_name></div>
<div><div class=lbl>دسته</div><input id=s_cat list=catlist placeholder=Normal></div>
<div><div class=lbl>پورت</div><input id=s_port type=number value=443></div>
<div><div class=lbl>ترتیب</div><input id=s_ord type=number value=99></div></div>
<div class=lbl>آدرس (اختیاری)</div><input id=s_addr>
<div class=lbl>لینک کانفیگ</div><textarea id=s_link rows=2></textarea>
<div class=lbl>پیام نمایشی زیر سرور (خالی = خوده IP:port نشون داده میشه)</div>
<input id=s_note placeholder="مثلاً: فیلترشکن پرسرعت">
<button onclick=srvAdd() style=margin-top:10px>افزودن سرور</button>
</div></details>

<details class=acc><summary>📥 افزودن گروهی</summary><div class=body>
<p class=note>هر خط: <code>name | link | category</code> — نام و دسته اختیاری‌اند (فقط لینک هم قبول).</p>
<textarea id=s_bulk rows=4></textarea>
<div class=row2 style=margin-top:8px>
<div><div class=lbl>دسته پیش‌فرض</div><input id=s_bcat list=catlist value=Normal></div>
<div style="flex:0;display:flex;align-items:flex-end"><button onclick=bulkAdd()>افزودن گروهی</button></div></div>
<div class=lbl>پیام زیر تمام سرورهای گروهی (خالی = خوده IP:port)</div>
<input id=s_bnote placeholder="مثلاً: سرورهای اروپا - پینگ خوب">
</div></details>
</div></div>

<!-- ================= CATEGORIES ================= -->
<div class=tab id=tab_cat>
<div class=card>
<h2>🗂 دسته‌بندی‌ها <span class=cnt id=cat_cnt></span></h2>
<div id=cat_chips style="margin:4px 0 12px"></div>
<div class=toolbar>
<input id=cat_new placeholder="نام دسته جدید..." style=max-width:240px>
<button class=sm onclick=catAdd()>افزودن</button>
</div>
<p class=note>「✕」حذف دسته (سرورها به اولین دسته منتقل میشن) — 「✎」تغییرنام/ادغام دسته — کلیک روی چیپ = فیلتر در تب سرورها.</p>
</div></div>

<!-- ================= MESSAGES ================= -->
<div class=tab id=tab_msg>
<div class=card><h2>🔔 اپدیت</h2>
<div class=row2><div><div class=lbl>حداقل نسخه (کمتر = اجباری)</div><input id=u_min type=number value=0></div>
<div><div class=lbl>آخرین نسخه (اختیاری)</div><input id=u_latest type=number value=0></div></div>
<div class=lbl>لینک دانلود</div><input id=u_url>
<button onclick=saveUpdate() style=margin-top:10px>ذخیره</button></div>

<div class=card><h2>💬 پیام پاپ‌اپ</h2>
<div class=toolbar>
<label class=switch><input type=checkbox id=p_active onchange=savePopup()><span></span></label>
<span class=note>فعال بودن ارسال</span>
<span style=margin-right:auto id=popup_state class=note></span>
<button class="gray sm" onclick=resetShows()>ریست شمارنده نمایش‌ها</button>
</div>
<div class=row2><div><div class=lbl>تیتر</div><input id=p_title></div>
<div><div class=lbl>متن</div><input id=p_body></div></div>
<div class=row2><div><div class=lbl>متن دکمه (خالی = بدون دکمه)</div><input id=p_btn></div>
<div><div class=lbl>لینک دکمه</div><input id=p_url></div></div>
<div class=row2><div><div class=lbl>حداکثر دفعات نمایش (کلی)</div><input id=p_max type=number value=100></div>
<div style=display:flex;gap:16px;align-items:flex-end;flex-wrap:wrap>
<label><input type=checkbox id=p_onbtn style=width:auto checked> دکمه لینک فعال</label>
<label><input type=checkbox id=p_oncancel style=width:auto checked> دکمه کنسل فعال</label>
</div></div>
<button onclick=savePopup() style=margin-top:10px>ذخیره و ارسال مجدد پاپ‌اپ</button></div>

<div class=card><h2>📢 نوتیفیکیشن‌ها</h2>
<div class=row2><div><div class=lbl>تیتر</div><input id=n_title></div>
<div><div class=lbl>متن</div><input id=n_body></div>
<div style="flex:0;display:flex;align-items:flex-end"><button onclick=notifAdd()>ارسال</button></div></div>
<div id=notif_tbl></div></div>

<div class=card><h2>📎 ارسال فایل / عکس / هر داده (استگانو)</h2>
<div class=row2><div><div class=lbl>عنوان (اختیاری - نوتیف داخل دستگاه)</div><input id=f_title></div></div>
<div class=lbl>انتخاب فایل (هر نوع؛ حداکثر 45KB)</div>
<input type=file id=f_file>
<div id=f_info class=note></div>
<button onclick=sendFile() style=margin-top:10px>🔒 ارسال به همه دستگاه‌ها</button>
<p class=note>ورکر خودش بسته‌بندی می‌کند: JSON → zlib → AES-256-GCM(کلید 60 دقیقه‌ای هر دستگاه) → متن حامل استگانو. بایت‌های خام هرگز روی شبکه دیده نمی‌شوند.</p>
<div class=lbl>فایل‌های اخیر</div><div id=payload_tbl class=empty>در حال دریافت...</div></div>
</div>

<!-- ================= DEVICES ================= -->
<div class=tab id=tab_dev>
<div class=card><h2>👥 دستگاه‌ها <span class=cnt id=dev_cnt></span></h2>
<input class=search id=dev_q placeholder="جستجو..." oninput=renderDevices()>
<button class="gray sm" onclick=loadDevices() style=margin-top:6px>🔄 بروزرسانی دستگاه‌ها</button>
<div id=dev_list class=empty>در حال دریافت...</div></div>

<div class=card><h2>⛔ لیست‌های مسدود</h2>
<div class=row2>
<div><div class=lbl>دستگاه‌ایدها مسدود (با کاما)</div><textarea id=blk_d rows=2></textarea></div>
<div><div class=lbl>ایپی‌ها مسدود (با کاما)</div><textarea id=blk_i rows=2></textarea></div>
<div><div class=lbl>مدل‌ها مسدود (با کاما)</div><textarea id=blk_m rows=2></textarea></div>
</div>
<button onclick=saveBlockLists() style=margin-top:10px>ذخیره لیست‌های مسدود</button></div>
</div>

</main>
</div></div>

<div class=toast id=toast>انجام شد</div>
<div id=info_modal style="position:fixed;inset:0;background:rgba(11,15,20,.8);z-index:210;display:none;align-items:center;justify-content:center">
<div class=card style="width:min(420px,92vw)">
<h2>ℹ مشخصات دستگاه</h2>
<div id=info_body class=note style="max-height:60vh;overflow:auto"></div>
<button class="gray sm" style=margin-top:12px onclick=infoClose()>بستن</button></div></div>

<script>
let PW='';

const $=(id)=>document.getElementById(id);
let CATS=[],SRV=[],DEVS=[],CUT=0,FILT=-1,SRV_SHOW=150;
function esc(s){return String(s==null?'':s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;').replace(/'/g,'&#39;')}
function showErr(m){const b=$('err');if(!m){b.style.display='none';return}b.style.display='block';b.textContent=m}
let supportModal=null;
function infoClose(){$('info_modal').style.display='none'}
async function api(body){
 let r;
 try{r=await fetch('/admin/api',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({...body,pw:PW})})}
 catch(e){showErr('network error: '+e);toast('خطای شبکه',1);throw e}
 if(r.status===401){const j=await r.json().catch(()=>({}));logout(j.error||'رمز غلط است');throw new Error('locked')}
 const txt=await r.text().catch(()=>'');
 let j=null;
 try{j=txt?JSON.parse(txt):{ok:false,error:'empty response (http '+r.status+')'}}catch(e){j={ok:false,error:'http '+r.status+': '+txt.slice(0,90)}}
 j.__status=r.status;
 if(j.error){showErr(null);toast('خطا: '+j.error,1)}else{showErr(null);toast()}
 return j}
function toast(msg,err){const t=$('toast');t.textContent=msg||'✓ انجام شد';t.className=err?'toast err':'toast';t.style.display='block';clearTimeout(t._h);t._h=setTimeout(()=>t.style.display='none',1400)}
function logout(msg){PW='';$('app').style.opacity='.25';$('app').style.pointerEvents='none';$('app').style.filter='blur(6px)';$('lock').style.display='flex';$('pass').value='';showErr(null);const e=$('lock_err');e.style.display=msg?'block':'none';e.textContent=msg||''}
async function unlock(){const v=$('pass').value.trim();if(!v)return;PW=v;try{const j=await api({action:'stats'});if(j&&j.stats){openPanel()}else{PW='';logout('رمز غلط است')}}catch(e){PW=''}}
function openPanel(){$('lock').style.display='none';$('app').style.opacity='1';$('app').style.pointerEvents='auto';$('app').style.filter='none';loadAll()}

function tab(id){document.querySelectorAll('nav button').forEach(b=>b.classList.toggle('on',b.dataset.tab===id));
document.querySelectorAll('.tab').forEach(t=>t.classList.toggle('on',t.id==='tab_'+id));showErr(null)}

/* ---- cats ---- */
function catlist(){const old=$('catlist');if(old)old.remove();const d=document.createElement('datalist');d.id='catlist';
d.innerHTML=CATS.map(c=>'<option value="'+esc(c)+'">').join('');document.body.appendChild(d)}
function renderCatUI(){$('cat_cnt').textContent=CATS.length+' دسته';
const box=$('cat_chips');box.innerHTML='';
CATS.forEach((c,i)=>{const s=document.createElement('span');s.className='chip'+(FILT===i?' on':'');s.textContent=c;
const st=document.createElement('span');st.textContent='('+countCat(c)+')';st.title='تعداد سرور';s.appendChild(st);
const x=document.createElement('span');x.className='x';x.textContent='✕';x.title='حذف';x.onclick=ev=>{ev.stopPropagation();catDel(i)};
const ed=document.createElement('span');ed.className='x';ed.textContent='✎';ed.title='تغییرنام/ادغام';ed.onclick=ev=>{ev.stopPropagation();catEdit(i)};
s.appendChild(x);s.appendChild(ed);s.onclick=()=>{FILT=i;renderFilters();tab('srv');renderServers()};box.appendChild(s)});
catlist();renderFilters()}
function countCat(c){return SRV.filter(s=>s.category===c).length}
async function catsSetting(){await api({action:'cats.set',cats:CATS})}
function catAdd(){const n=($('cat_new').value||'').trim();if(!n)return;$('cat_new').value='';
if(CATS.includes(n)){toast('تکراری',1);return}CATS.push(n);catsSetting().then(loadCats)}
function catDel(i){if(!confirm('دسته «'+CATS[i]+'» حذف شود؟ سرورهایش به «'+(CATS.find((_,j)=>j!==i)||'Normal')+'» منتقل میشوند.'))return;
const old=CATS[i];CATS.splice(i,1);catsSetting().then(api.bind(null,{action:'srv.catmove',from:old,to:CATS[0]||'Normal'})).then(loadAll)}
function catEdit(i){const n=prompt('نام دسته:',CATS[i]);if(n==null||!n.trim()||n.trim()===CATS[i])return;
const old=CATS[i];CATS[i]=n.trim();catsSetting().then(api.bind(null,{action:'srv.catmove',from:old,to:n.trim()})).then(loadAll)}
function renderFilters(){const box=$('srv_filters');if(!box)return;box.innerHTML='';
const mk=(t,idx)=>{const c=document.createElement('span');c.className='chip'+(FILT===idx?' on':'');c.textContent=t;
c.onclick=()=>{FILT=idx;renderFilters();renderServers();renderCatUI()};box.appendChild(c)};
mk('همه',-1);CATS.forEach((c,i)=>mk(c,i));catlist()}

/* ---- servers ---- */
async function srvAdd(){const b={action:'srv.add',name:$('s_name').value,address:$('s_addr').value,
port:Number($('s_port').value||443),link:$('s_link').value,category:$('s_cat').value,ord:Number($('s_ord').value||99),msg:$('s_note').value};
await api(b);['s_name','s_addr','s_link','s_note'].forEach(i=>{$(i).value=''});loadAll()}
async function bulkAdd(){const j=await api({action:'srv.bulk',text:$('s_bulk').value,category:$('s_bcat').value,msg:$('s_bnote').value});
$('s_bulk').value='';toast('افزوده شد: '+((j&&j.added)||0));loadAll()}
function processFlagsUI(j){const p=j.pflags||{};
$('f_ping_start').value=p.ping_on_start||'auto';
$('f_flag_start').value=p.flag_on_start||'auto';
$('f_auto_ping_new').value=p.auto_ping_new||'auto';
$('f_auto_flag_new').value=p.auto_flag_new||'auto'}
async function loadFlags(){const j=await api({action:'cfg.load'});const p=j.pflags||{};
$('f_ping_start').value=p.ping_on_start||'auto';
$('f_flag_start').value=p.flag_on_start||'auto';
$('f_auto_ping_new').value=p.auto_ping_new||'auto';
$('f_auto_flag_new').value=p.auto_flag_new||'auto'}
async function saveFlags(){await api({action:'set.flags',flags:{
 ping_on_start:$('f_ping_start').value,
 flag_on_start:$('f_flag_start').value,
 auto_ping_new:$('f_auto_ping_new').value,
 auto_flag_new:$('f_auto_flag_new').value}});toast('فرمان‌ها ذخیره شد — اپ‌ها با سینک بعدی اعمال میکنن')}
async function loadServers(){
 const box=$('srv_list');
 let j=null;try{j=await api({action:'srv.all'})}catch(e){j=null}
 // never leave the "در حال دریافت..." placeholder standing: a bad response has to say why
 if(!j||!j.ok||!Array.isArray(j.servers)){
  const why=(j&&j.error)||('http '+((j&&j.__status)||0));
  const bld=(j&&j.build)||'نامعلوم';
  if(box){box.className='empty';box.textContent='دریافت سرورها ناموفق — '+why+' | نسخهٔ ورکر: '+bld+(j&&j.build?'':' (احتمالاً ورکرِ روی Cloudflare قدیمی است: همین cloudflare/worker.js را دوباره deploy کن)')+' — با 🔄 دوباره'}
  if($('err'))$('err').textContent='srv.all: '+why+' | build: '+bld+' | status: '+((j&&j.__status)||0);
  return}
 if($('wrkr_build'))$('wrkr_build').textContent='ورکر '+j.build;
 CATS=j.cats||[];
 const cfg=j.cfg||{};
 const on=cfg.app_enabled!=='0';
 if($('app_sw'))$('app_sw').checked=on;
 appState(on);
 SRV=j.servers||[];
 renderFilters();renderCatUI();renderServers();processFlagsUI(j)}
function appState(on){$('app_state').innerHTML=on?'<span class="badge b-on">اپ فعال است</span>':'<span class="badge b-off">اپ قفل - همه دستگاه‌ها بسته اند</span>'}
function appToggle(on){api({action:'set.app',on:on})}
function srvRow(s,i,list){
 const d=document.createElement('div');d.className='srv'+(s.active?'':' off');
 const ord=document.createElement('div');
 const up=document.createElement('button');up.className='gray sm';up.textContent='▲';up.title='بالا';up.disabled=i===0;
 up.onclick=()=>api({action:'srv.ord',id:s.id,up:true}).then(loadServers);ord.appendChild(up);
 const dn=document.createElement('button');dn.className='gray sm';dn.textContent='▼';dn.title='پایین';dn.disabled=i===list.length-1;
 dn.onclick=()=>api({action:'srv.ord',id:s.id,up:false}).then(loadServers);ord.appendChild(dn);d.appendChild(ord);
 const nm=document.createElement('input');nm.className='nm';nm.value=s.name||'';
 nm.onchange=()=>api({action:'srv.rename',id:s.id,name:nm.value,category:s.category||'',msg:s.msg||''}).then(loadServers);d.appendChild(nm);
 const ct=document.createElement('input');ct.className='ct';ct.setAttribute('list','catlist');ct.value=s.category||'';
 ct.onchange=()=>api({action:'srv.rename',id:s.id,name:s.name||'',category:ct.value,msg:s.msg||''}).then(loadServers);d.appendChild(ct);
 const nb=document.createElement('button');nb.className='gray sm';nb.textContent='💬';
 nb.title='پیام نمایشی زیر سرور (خالی = IP:port)';
 nb.onclick=()=>{const n=prompt('پیام نمایشی زیر سرور (خالی = خوده IP:port):',s.msg||'');if(n==null)return;
 api({action:'srv.rename',id:s.id,name:s.name||'',category:s.category||'',msg:n.trim()}).then(loadServers)};d.appendChild(nb);
 if(s.msg&&s.msg.trim()){const ind=document.createElement('span');ind.className='note-ind';ind.textContent='پیام: '+s.msg.slice(0,22)+'…';d.appendChild(ind)}
 const bd=document.createElement('span');bd.className='badge '+(s.active?'b-on':'b-off');bd.textContent=s.active?'✓ فعال':'✕ خاموش';d.appendChild(bd);
 const tb=document.createElement('button');tb.className='gray sm';tb.textContent=s.active?'خاموش':'روشن';
 tb.onclick=()=>api({action:'srv.active',id:s.id,on:!s.active}).then(loadServers);d.appendChild(tb);
 const db=document.createElement('button');db.className='red sm';db.textContent='حذف';
 db.onclick=()=>{if(confirm('سرور «'+(s.name||s.id)+'» حذف شود؟'))api({action:'srv.del',id:s.id}).then(loadServers)};d.appendChild(db);
 const lk=document.createElement('div');lk.className='lnk';
 lk.textContent=(s.address&&s.address!=='0.0.0.0')?s.address+':'+(s.port||443):'کلیک = کپی لینک کامل';
 lk.title='کلیک = کپی لینک کامل';
 lk.onclick=async()=>{const j=await api({action:'srv.link',id:s.id});if(j&&j.link&&navigator.clipboard)navigator.clipboard.writeText(j.link).then(()=>toast('لینک کپی شد'))};d.appendChild(lk);
 return d}
async function renderServers(){
 if(!$('tab_srv'))return;
 const q=($('srv_q').value||'').toLowerCase();const only=$('grp_only')&&$('grp_only').checked;const fc=FILT>=0&&FILT<CATS.length?CATS[FILT]:'';
 let list=SRV.filter(s=>{
  if(only&&!s.active)return false;
  if(fc&&s.category!==fc)return false;
  if(q&&!(((s.name||'')+' '+(s.category||'')+' '+(s.address||'')).toLowerCase().includes(q)))return false;return true});
$('srv_cnt').textContent=list.length+'/'+SRV.length;
const box=$('srv_list');box.className='';box.innerHTML='';
if(!list.length){box.className='empty';box.textContent='موردی یافت نشد';return}
// rendering 500 rows x ~10 nodes is what froze the browser; grow the window on demand
list.slice(0,SRV_SHOW).forEach((s,i)=>box.appendChild(srvRow(s,i,list)));
if(list.length>SRV_SHOW){const mb=document.createElement('button');mb.className='gray';mb.textContent='نمایش بیشتر (+'+Math.min(150,list.length-SRV_SHOW)+' از '+(list.length-SRV_SHOW)+')';mb.onclick=()=>{SRV_SHOW+=150;renderServers()};box.appendChild(mb)}}

/* ---- update / popup / notifs ---- */
async function loadUpdate(){
 const j=await api({action:'cfg.load'});const c=j.cfg||{};
 $('u_min').value=Number(c.min_version||0);$('u_latest').value=Number(c.latest_version||0);$('u_url').value=c.latest_url||''}
async function saveUpdate(){await api({action:'set.update',minVersion:Number($('u_min').value||0),latestVersion:Number($('u_latest').value||0),latestUrl:$('u_url').value})}
async function loadPopup(){const j=await api({action:'popup.state'});const p=j.popup||{};
$('p_active').checked=p.popup_active==='1';$('p_title').value=p.popup_title||'';$('p_body').value=p.popup_body||'';
$('p_btn').value=p.popup_btn_text||'';$('p_url').value=p.popup_btn_url||'';
$('p_onbtn').checked=p.popup_btn_enabled!=='0';$('p_oncancel').checked=p.popup_cancel_enabled!=='0';
$('p_max').value=Number(p.popup_max_shows||100);popupState(p)}
function popupState(p){p=p||{};const on=$('p_active').checked;
$('popup_state').innerHTML=on?'<span class="badge b-on">در حال ارسال — '+Number(p.popup_shows||0)+' نمایش از سقف '+Number(p.popup_max_shows||0)+'</span>':'<span class="badge b-off">متوقف</span>'}
async function savePopup(){await api({action:'set.popup',active:$('p_active').checked,title:$('p_title').value,body:$('p_body').value,btnText:$('p_btn').value,btnUrl:$('p_url').value,btnEnabled:$('p_onbtn').checked,cancelEnabled:$('p_oncancel').checked,maxShows:Number($('p_max').value||100)});loadPopup()}
async function resetShows(){await savePopup();toast('شمارنده رست شد و پاپ‌اپ دوباره به همه ارسال میشود');loadPopup()}
async function notifAdd(){await api({action:'notif.add',title:$('n_title').value,body:$('n_body').value});$('n_title').value='';$('n_body').value='';loadNotifs()}

/* ---- admin file/image payload (stego-delivered to every app) ---- */
let FPAYLOAD=null,FNAME='',FMIME='';
const FMAX=45*1024;
document.getElementById('f_file').addEventListener('change',e=>{
 const f=e.target.files[0];FPAYLOAD=null;FNAME='';FMIME='';
 if(!f){$('f_info').textContent='';return}
 if(f.size>FMAX){toast('فایل بزرگتر از 45KB است',1);e.target.value='';return}
 FPAYLOAD=f;FNAME=f.name;FMIME=f.type||'application/octet-stream';
 $('f_info').textContent='آماده ارسال: '+f.name+' ('+(f.size/1024).toFixed(1)+'KB)'});
async function sendFile(){
 if(!FPAYLOAD){toast('فایلی انتخاب نشده',1);return}
 const b64=await new Promise(r=>{const fr=new FileReader();fr.onload=()=>r(String(fr.result).split(',')[1]);fr.readAsDataURL(FPAYLOAD)});
 const j=await api({action:'payload.add',name:FNAME,mime:FMIME,title:$('f_title').value,b64:b64,size:FPAYLOAD.size});
 if(j&&j.size!=null)toast('ارسال شد: '+((j.size/1024).toFixed(1))+'KB');
 $('f_file').value='';$('f_info').textContent='';FPAYLOAD=null;loadPayloads()}
async function loadPayloads(){const j=await api({action:'payload.list'});const box=$('payload_tbl');
 const list=j.payloads||[];const dMap=j.delivered||{};
 if(!list.length){box.className='empty';box.textContent='فرستاده نشده';return}
 box.className='';box.innerHTML='<table><tr><th>فایل</th><th>حجم</th><th>تحویل</th><th></th></tr>'+
 list.map(p=>'<tr><td>'+esc(p.name||p.id)+(p.title?'<br><small style=color:var(--sub)>'+esc(p.title)+'</small>':'')+'</td>'+
 '<td>'+(Number(p.size||0)/1024).toFixed(1)+'KB</td><td><span class="badge '+(Number(dMap[String(p.id)]||0)>0?'b-on':'b-y')+'">'+(dMap[String(p.id)]||0)+' دستگاه</span>'+
 (p.active?'':'<br><span class="badge b-off">غیرفعال</span>')+'</td>'+
 '<td>'+(p.active?'<button class="gray sm" data-off="'+p.id+'">توقف</button> ':'<button class="gray sm" data-on="'+p.id+'">فعال</button> ')+
 '<button class="gray sm" data-resend="'+p.id+'">ارسال مجدد</button></td></tr>').join('')+'</table>';
 box.querySelectorAll('button[data-off]').forEach(b=>b.onclick=()=>api({action:'payload.off',id:Number(b.dataset.off)}).then(loadPayloads));
 box.querySelectorAll('button[data-on]').forEach(b=>b.onclick=()=>api({action:'payload.on',id:Number(b.dataset.on)}).then(loadPayloads));
 box.querySelectorAll('button[data-resend]').forEach(b=>b.onclick=()=>api({action:'payload.resend',id:Number(b.dataset.resend)}).then(()=>{toast('برای همه ارسال مجدد میشود');loadPayloads()}))}
async function loadNotifs(){const j=await api({action:'notif.list'});const box=$('notif_tbl');
if(!(j.notifs||[]).length){box.className='empty';box.textContent='هیچ نوتیفیکیشنی نیست';return}
box.className='';box.innerHTML='<table><tr><th>id</th><th>تیتر</th><th>متن</th><th></th></tr>'+
(j.notifs||[]).map(n=>'<tr><td>'+n.id+'</td><td>'+esc(n.title)+'</td><td>'+esc((n.body||'').slice(0,80))+'</td><td>'+
(n.active?'<button class="gray sm" data-id="'+n.id+'">حذف</button>':'<span class="badge b-off">منقضی</span>')+'</td></tr>').join('')+'</table>';
box.querySelectorAll('button[data-id]').forEach(b=>b.onclick=()=>api({action:'notif.off',id:Number(b.dataset.id)}).then(loadNotifs))}

/* ---- stats / devices ---- */
async function loadStats(){const j=await api({action:'stats'});if(!j.stats)return;const s=j.stats;
$('stats').innerHTML=
 '<div class="stat"><b>'+s.devices+'</b><span>کل دستگاه‌ها</span></div>'+
 '<div class="stat"><b class="b">'+s.online+'</b><span>آنلاین</span></div>'+
 '<div class="stat"><b>'+s.onlineConnected+'</b><span>متصل به سرور</span></div>'+
 '<div class="stat"><b class="r">'+s.offline+'</b><span>آفلاین</span></div>'+
 '<div class="stat"><b class="a">'+s.connectsToday+'</b><span>اتصال امروز</span></div>'+
 '<div class="stat"><b>'+s.syncsToday+'</b><span>همگام‌سازی امروز</span></div>'+
 '<div class="stat"><b>'+s.activeServers+'</b><span>سرور فعال</span></div>'+
 '<div class="stat"><b class="r">'+(s.offServers||0)+'</b><span>سرور خاموش</span></div>'}
async function loadDevices(){const j=await api({action:'devices.list'});DEVS=j.devices||[];CUT=j.cut||0;
window.BLOCKED=j.blocked||{devices:[],ips:[],models:[]};
if($('blk_d')&&$('blk_d').value==='')$('blk_d').value=(window.BLOCKED.devices||[]).join(',');
if($('blk_i')&&$('blk_i').value==='')$('blk_i').value=(window.BLOCKED.ips||[]).join(',');
if($('blk_m')&&$('blk_m').value==='')$('blk_m').value=(window.BLOCKED.models||[]).join(',');
renderDevices()}
async function saveBlockLists(){await api({action:'dev.blocklists',devices:$('blk_d').value,ips:$('blk_i').value,models:$('blk_m').value});loadDevices()}
function devBlock(kind,id,on,name){api({action:'dev.block',kind:kind,id:id,on:on}).then(loadDevices)}
function renderDevices(){const q=($('dev_q').value||'').toLowerCase();
const list=DEVS.filter(d=>!q||((d.id||'')+' '+(d.model||'')).toLowerCase().includes(q));
$('dev_cnt').textContent=list.length+' دستگاه';
const box=$('dev_list');
if(!list.length){box.className='empty';box.textContent='دستگاهی ثبت نشده';return}
box.className='';
const B=window.BLOCKED||{devices:[],ips:[],models:[]};
let html='<table><tr><th>مدل</th><th>وضعیت</th><th>IP</th><th>آخرین دیده</th><th>مسدودسازی</th><th></th></tr>';
list.forEach(d=>{
 const online=(Date.now()-Number(d.last_seen||0))<CUT;
 const last=d.last_seen?new Date(Number(d.last_seen)).toLocaleString('fa-IR'):'-';
 const dBlk=(B.devices||[]).includes(d.id), iBlk=(B.ips||[]).includes(d.ip||''), mBlk=(B.models||[]).includes((d.model||'').toLowerCase());
 const chip=(on,t)=>'<span class="badge '+(on?'b-off':'b-on')+'">'+t+'</span>';
 html+='<tr><td style="direction:ltr;text-align:right">'+esc(d.model||d.id.slice(0,8))+'</td>'+
 '<td><span class="badge '+(d.connected&&online?'b-on':online?'b-y':'b-off')+'">'+(d.connected&&online?'متصل':online?'آنلاین':'آفلاین')+'</span>'+
 (dBlk||iBlk||mBlk?'<br><span class="badge b-off">مسدود '+(dBlk?'دستگاه ':'')+(iBlk?'آیپی ':'')+(mBlk?'مدل':'')+'</span>':'')+'</td>'+
 '<td style=direction:ltr>'+esc(d.ip||'-')+'</td><td>'+last+'</td>'+
 '<td><button class="gray sm" data-k=device data-id="'+esc(d.id)+'" data-on='+(dBlk?'0':'1')+'>'+(dBlk?'آزاد':'مسدود')+' دستگاه</button> '+
 '<button class="gray sm" data-k=ip data-id="'+esc(d.ip||'')+'" data-on='+(iBlk?'0':'1')+'>'+(iBlk?'آزاد':'مسدود')+' آیپی</button> '+
 '<button class="gray sm" data-k=model data-id="'+esc(d.model||'')+'" data-on='+(mBlk?'0':'1')+'>'+(mBlk?'آزاد':'مسدود')+' مدل</button></td>'+
 '<td><button class="red sm" data-del="'+esc(d.id)+'">حذف</button> '+
 '<button class="gray sm" data-info="'+esc(JSON.stringify(d.info?d.info:{})||'{}')+'">ℹ</button></td></tr>';
});
html+='</table>';
box.innerHTML=html;
box.querySelectorAll('button[data-k]').forEach(b=>b.onclick=()=>{if(!b.dataset.id){toast('خالی',1);return}devBlock(b.dataset.k,b.dataset.id,b.dataset.on==='1')});
box.querySelectorAll('button[data-del]').forEach(b=>b.onclick=()=>{if(confirm('این دستگاه از لیست حذف شود؟'))api({action:'dev.del',id:b.dataset.del}).then(loadDevices)});
box.querySelectorAll('button[data-info]').forEach(b=>b.onclick=()=>{
 const d2=b.getAttribute('data-info')||'{}';let i2={};try{i2=JSON.parse(d2)}catch(e){}
 const rows2=Object.keys(i2).map(k=>'<b>'+esc(k)+':</b> '+esc(i2[k])).join('<br>');
 const m2=supportModal||null;if(m2)m2(rows2||'هیچ اطلاعاتی نیست')})}

/* ---- boot ---- */
/* password on EVERY visit - nothing persisted; all panels load in parallel for speed */
async function loadAll(){
 await Promise.allSettled([
   loadServers(),
   loadStats(),
   loadFlags(),
   loadNotifs(),
   loadPayloads(),
   loadPopup(),
   loadUpdate(),
   loadDevices(),
  ])}
(()=>{logout()})();
supportModal=((h)=>{const bd2=$('info_body');bd2.innerHTML=h;$('info_modal').style.display='flex'});
function infoClose(){$('info_modal').style.display='none'}
/* no auto-refresh - admin uses the 🔄 buttons */
function manualRefresh(){if(!PW){logout();return}toast('در حال بروزرسانی...');loadAll()}
</script>

</body></html>`;
  return new Response(html, {
    headers: { "content-type": "text/html; charset=utf-8" },
  });
}

/* ============================ blob secure layer (v2) ============================
 * Design (server-issued rotating session keys - the app never owns a crypto key):
 *   1) POST /api/key: worker derives kc (payload key) + kt (token key) fresh per
 *      60-minute bucket: HMAC(BLOB_ROOT, "blobk1"+device+bucket+"K"/"T") and hands
 *      them over the TLS channel; rows live in blob_sessions. When the bucket
 *      rolls, /api/sync answers enc:"h" and the app re-issues keys on its next
 *      request - rotation is seamless.
 *   2) One-shot token on every request: ts|nonce|HMAC(kt, msg) matched against a
 *      strictly-monotonic per-device timestamp (D1) => replay-proof, single-use.
 *   3) /api/sync is the ONLY data channel and is ALWAYS encrypted: the app sends
 *      device info + pending ack receipts inside the body; the admin's raw
 *      settings/files are packed by the worker itself:
 *      JSON -> zlib -> AES-256-GCM(Kc, AAD BLBv1) -> homoglyph stego carrier
 *      (same bit engine as StegaText). No /api/beat, /api/ack or /api/handshake
 *      anymore, and NO plaintext fallback anywhere.
 * Worker secret (console):
 *   wrangler secret put BLOB_ROOT  (64 hex chars, equals the app root)
 */
const M64 = (1n << 64n) - 1n;
/** stego header magic (16-bit) - MUST equal the app's BlobStego.MAGIC16 (0xB10B). */
const BL_MAGIC = 0xB10B;

function bl_b64uDecode(s) {
  let t = String(s).replace(/-/g, "+").replace(/_/g, "/").trim();
  while (t.length % 4 !== 0) t += "=";
  const bin = atob(t);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

function bl_b64uEncode(b) {
  let s = "";
  for (let i = 0; i < b.length; i++) s += String.fromCharCode(b[i]);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function bl_hex(b) {
  let s = "";
  for (let i = 0; i < b.length; i++) s += b[i].toString(16).padStart(2, "0");
  return s;
}

function bl_hexToB(h) {
  const o = new Uint8Array(h.length >> 1);
  for (let i = 0; i < o.length; i++) o[i] = parseInt(h.substr(i * 2, 2), 16);
  return o;
}

function bl_eq(a, b) {
  if (a.length !== b.length) return false;
  let d = 0;
  for (let i = 0; i < a.length; i++) d |= a[i] ^ b[i];
  return d === 0;
}

async function bl_hmac(keyB, msgB) {
  const k = await crypto.subtle.importKey("raw", keyB, { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  return new Uint8Array(await crypto.subtle.sign("HMAC", k, msgB));
}

async function bl_sha(b) {
  return new Uint8Array(await crypto.subtle.digest("SHA-256", b));
}

/** xorshift64* with rejection sampling - bit-exact port of KeyedRandom (all ops 64-bit masked BigInt). */
const BL_GOLDEN = (1n << 64n) - 0x61C8864680B583EBn;

class BlKeyedRandom {
  constructor(seedBig) {
    let st = (seedBig & M64) ^ BL_GOLDEN;
    st &= M64;
    if (st === 0n) st = 0xB504F32Dn;
    this.s = st;
  }

  next() {
    let x = this.s;
    x ^= (x << 13n) & M64;
    // NOTE: BigInt has no `>>>`; x is always non-negative here, so `>>` == unsigned shift
    x ^= x >> 7n;
    x ^= (x << 17n) & M64;
    this.s = x & M64;
    return (x * 0x2545F4914F6CDD1Dn) & M64;
  }

  below(bound) {
    if (bound <= 1) return 0;
    const b = BigInt(bound);
    const lim = ((1n << 62n) / b) * b;
    let v;
    do {
      v = this.next() & ((1n << 62n) - 1n);
    } while (v >= lim);
    return Number(v % b);
  }
}

/** Homoglyph twin pairs - MUST stay byte-identical with the app port. */
const BL_PAIRS = [
  ["ی", "ي"], ["ک", "ك"],
  ["a", "а"], ["A", "А"], ["c", "с"], ["C", "С"], ["e", "е"], ["E", "Е"],
  ["o", "о"], ["O", "О"], ["p", "р"], ["P", "Р"], ["x", "х"], ["X", "Х"],
  ["y", "у"], ["Y", "У"], ["k", "к"], ["K", "К"], ["m", "м"], ["M", "М"],
  ["j", "ј"], ["J", "Ј"], ["B", "В"],
];
const BL_TWINS = new Map();
for (const p of BL_PAIRS) { BL_TWINS.set(p[0], p[1]); BL_TWINS.set(p[1], p[0]); }
const BL_PRIMARY = new Set(BL_PAIRS.map((p) => p[0]));
const BL_ZWNJ = "\u200C";
const BL_ZWJ = "\u200D";
const BL_STRIP = new Set(["\u200B", BL_ZWNJ, BL_ZWJ, "\u2060", "\uFEFF"]);
const BL_GLUE = new Set(["\u200B", BL_ZWNJ, BL_ZWJ, "\u2060", "\uFEFF", "\u00AD"]);

function bl_isLetter(c) {
  const v = c.codePointAt(0);
  return (v >= 0x0600 && v <= 0x06FF) || (v >= 0x0041 && v <= 0x005A) || (v >= 0x0061 && v <= 0x007A);
}

function bl_primaryOf(c) {
  if (BL_PRIMARY.has(c)) return c;
  const t = BL_TWINS.get(c);
  return t != null ? t : c;
}

function bl_isGlue(c) {
  return !bl_isLetter(c) && BL_GLUE.has(c);
}

function bl_family(c) {
  const v = c.codePointAt(0);
  if ((v >= 0x0600 && v <= 0x06FF) || (v >= 0x0750 && v <= 0x077F) || (v >= 0x08A0 && v <= 0x08FF) ||
      (v >= 0xFB50 && v <= 0xFDFF) || (v >= 0xFE70 && v <= 0xFEFF)) return 1;
  if ((v >= 0x0400 && v <= 0x052F) || (v >= 0x0041 && v <= 0x005A) || (v >= 0x0061 && v <= 0x007A)) return 2;
  if ((v >= 0x0370 && v <= 0x03FF) || (v >= 0x1F00 && v <= 0x1FFF)) return 3;
  if ((v >= 0x3040 && v <= 0x30FF) || (v >= 0x4E00 && v <= 0x9FFF) || (v >= 0xAC00 && v <= 0xD7AF)) return 4;
  return 0;
}

/** Normalize (same as Kotlin normalizeCarriers): strip invisibles, collapse spaces, prime glyphs. */
function bl_normalize(carrier) {
  let out = "";
  let prevSpace = false;
  for (let i = 0; i < carrier.length; i++) {
    const c = carrier.charAt(i);
    if (BL_STRIP.has(c)) continue;
    if (c === " ") {
      if (prevSpace) continue;
      prevSpace = true;
    } else prevSpace = false;
    out += bl_primaryOf(c);
  }
  return out;
}

/** Robust slots (morph ZWNJ + space insertions + homoglyph swaps) - exact port. */
function bl_slots(canon) {
  let n = 0;
  for (let i = 0; i < canon.length; i++) if (bl_isLetter(canon.charAt(i))) n++;
  const lch = new Array(n);
  const lidx = new Array(n);
  let t = 0;
  for (let i = 0; i < canon.length; i++) {
    const c = canon.charAt(i);
    if (bl_isLetter(c)) { lch[t] = c; lidx[t] = i; t++; }
  }
  const sameToken = new Array(Math.max(0, n - 1)).fill(false);
  for (let j = 0; j < n - 1; j++) {
    let cont = true;
    for (let u = lidx[j] + 1; u < lidx[j + 1]; u++) {
      if (!bl_isGlue(canon.charAt(u))) { cont = false; break; }
    }
    sameToken[j] = cont;
  }
  const morphK = new Set();
  const morphSlots = [];
  let k = 0;
  while (k < n) {
    let end = k;
    while (end + 1 < n && sameToken[end]) end++;
    let w = "";
    for (let u = k; u <= end; u++) w += bl_primaryOf(lch[u]);
    let slotK = -1;
    if (w.endsWith("هایی") && w.length >= 8) slotK = end - 3;
    else if (w.endsWith("ترین") && w.length >= 8) slotK = end - 3;
    else if (w.endsWith("های") && w.length >= 7) slotK = end - 2;
    else if (w.endsWith("ها") && w.length >= 5) slotK = end - 1;
    else if (w.endsWith("تر") && w.length >= 6) slotK = end - 1;
    else if (w.startsWith("نمی") && w.length >= 6) slotK = k + 3;
    else if (w.startsWith("می") && w.length >= 5) slotK = k + 2;
    if (slotK > 0 && slotK > k && slotK <= end) {
      morphK.add(slotK);
      morphSlots.push({ ins: true, at: lidx[slotK], prev: lidx[slotK - 1], ch: BL_ZWNJ });
    }
    k = end + 1;
  }
  const spaceSlots = [];
  for (let j = 0; j < n - 1; j++) {
    const target = j + 1;
    if (morphK.has(target)) continue;
    let spaceLike = true;
    for (let u = lidx[j] + 1; u < lidx[target]; u++) {
      const c = canon.charAt(u);
      if (c !== " " && !bl_isGlue(c)) { spaceLike = false; break; }
    }
    if (spaceLike && lidx[target] > lidx[j] + 1) {
      spaceSlots.push({ ins: true, at: lidx[target], prev: lidx[j], ch: " " });
    }
  }
  const swapSlots = [];
  for (let j = 0; j < n; j++) {
    const twin = BL_TWINS.get(lch[j]);
    if (twin != null) swapSlots.push({ swap: true, at: lidx[j], twin });
  }
  return morphSlots.concat(spaceSlots, swapSlots);
}

function bl_eval(s, slot) {
  if (slot.swap) {
    return slot.at >= 0 && slot.at < s.length && !BL_PRIMARY.has(s.charAt(slot.at));
  }
  if (slot.prev < 0 || slot.at >= s.length || slot.at <= slot.prev) return false;
  if (slot.ch === " ") {
    let cnt = 0;
    for (let i = slot.prev + 1; i < slot.at; i++) if (s.charAt(i) === " ") cnt++;
    return cnt >= 2;
  }
  for (let i = slot.prev + 1; i < slot.at; i++) if (s.charAt(i) === slot.ch) return true;
  return false;
}

/** Envelope: utf8 JSON -> [flag 1B + zlib/deflate or raw]. Mode 2 = zlib (CompressionStream). */
async function bl_zipEnv(plain) {
  try {
    const cs = new CompressionStream("deflate");
    const stream = new Blob([plain]).stream().pipeThrough(cs);
    const buf = await new Response(stream).arrayBuffer();
    const c = new Uint8Array(buf);
    if (c.length < plain.length) {
      const out = new Uint8Array(c.length + 1);
      out[0] = 2;
      out.set(c, 1);
      return out;
    }
  } catch (e) {}
  const out = new Uint8Array(plain.length + 1);
  out[0] = 0;
  out.set(plain, 1);
  return out;
}

async function bl_seal(kc, plainBytes) {
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const key = await crypto.subtle.importKey("raw", kc, "AES-GCM", false, ["encrypt"]);
  const ct = new Uint8Array(await crypto.subtle.encrypt({ name: "AES-GCM", iv, additionalData: new TextEncoder().encode("BLBv1"), tagLength: 128 }, key, plainBytes));
  const out = new Uint8Array(12 + ct.length);
  out.set(iv, 0);
  out.set(ct, 12);
  return out;
}

async function bl_makeStego(cipherB, keyBytes) {
  const seedMsg = new TextEncoder().encode("BLB1");
  const mac = await bl_hmac(keyBytes, seedMsg);
  let seed = 0n;
  for (let i = 0; i < 8; i++) seed = (seed << 8n) | BigInt(mac[i] & 0xFF);
  const rnd = new BlKeyedRandom(seed);
  // carrier slot density is ~0.32 slots/char, so 4x the bit count keeps >20% headroom
  // for every payload size up to the 45KB panel cap - text-in-text stego is the DEFAULT
  // delivery (at 1.5x the capacity check always failed and every response fell back to enc:"c")
  const carriers = bl_makeCarrier(Math.ceil((56 + cipherB.length * 8) * 4));
  const canon = bl_normalize(carriers);
  const slots = bl_slots(canon);
  const bitCount = 56 + cipherB.length * 8;
  if (bitCount > slots.length) return null;
  const arr = new Array(slots.length);
  for (let i = 0; i < slots.length; i++) arr[i] = i;
  for (let i = slots.length - 1; i >= 1; i--) {
    const j = rnd.below(i + 1);
    const tmp = arr[i]; arr[i] = arr[j]; arr[j] = tmp;
  }
  const bits = new Uint8Array(bitCount);
  for (let i = 0; i < 16; i++) bits[i] = (BL_MAGIC >> (15 - i)) & 1;
  const L = cipherB.length;
  for (let i = 0; i < 32; i++) bits[16 + i] = (L >> (31 - i)) & 1;
  for (let i = 0; i < cipherB.length; i++) {
    for (let j = 0; j < 8; j++) bits[56 + i * 8 + j] = (cipherB[i] >> (7 - j)) & 1;
  }
  const chars = Array.from(canon);
  const ins = [];
  for (let b = 0; b < bitCount; b++) {
    const slot = slots[arr[b]];
    if (!bits[b]) continue;
    if (slot.swap) chars[slot.at] = slot.twin;
    else ins.push(slot);
  }
  ins.sort((p, q) => p.at - q.at);
  let out = "";
  let src = 0;
  for (const s of ins) {
    out += chars.slice(src, s.at).join("") + s.ch;
    src = s.at;
  }
  out += chars.slice(src).join("");
  return out;
}

/* --- natural-looking Persian/English carrier generator (only plain letters/space - stego safe) --- */
const BL_W = [
  "من امروز خیلی خسته," , "تو چطور شد؟", "او بارها گفت نامت را", "ما برمی‌گردیم", "شما مقصر تفاوت", "او نمی‌آید", "این امر نکند",
  "اما دقیقا چه شد", "بی نهایت مراقب باش", "حالا ساعت پاپ اعلام", "صبر کن کنار خیابان", "گاهی مانده‌ایم و گاهی رفت", "بمان سر جای سبز باشد",
  "Talking is cheap", "he wrote it twice", "we met after years", "the old road changed", "nobody returns here", "wait under the bridge",
  "never said goodbye", "the brown fox ran", "I said maybe later", "rest a small while",
];
const BL_W2 = ["او", "تو", "من", "او", "همین حالا", "پس فردا", "پشت ان", "یکی از آنان", "گاهی اوقات"];
const BL_W3 = ["می‌آید", "می‌رود", "می‌ماند", "برخاست", "نوشته بود", "خندید", "فاعل نشد", "برنگشت", "غم کرد"];

function bl_makeCarrier(charsWanted) {
  let out = "";
  while (out.length < charsWanted) {
    const s1 = BL_W[Math.floor(Math.random() * BL_W.length)];
    const s2 = BL_W2[Math.floor(Math.random() * BL_W2.length)];
    const s3 = BL_W3[Math.floor(Math.random() * BL_W3.length)];
    out += (out ? " " : "") + s1 + "، " + s2 + " " + s3 + ".";
  }
  return out;
}

/* --- one-shot token:  ts(4BE) | nonce(8) | hmac(32)   msg = "blb"|device|0x00|ts4BE|nonce --- */
async function bl_tokenCheck(env, device, tok, hmacKey) {
  try {
    const raw = bl_b64uDecode(tok);
    if (raw.length !== 44) return { ok: false };
    const ts = ((raw[0] & 0xff) << 24) | ((raw[1] & 0xff) << 16) | ((raw[2] & 0xff) << 8) | (raw[3] & 0xff);
    const nonce = raw.slice(4, 12);
    const sig = raw.slice(12, 44);
    const now = Math.floor(Date.now() / 1000);
    if (ts > now + BL_SKEW || ts < now - BL_SKEW) return { ok: false };
    const sig2 = await bl_hmac(hmacKey, bl_tokMsg(device, ts, nonce));
    if (!bl_eq(sig, sig2)) return { ok: false };
    // strictly monotonic per device => single-use & anti-replay
    const upd = await env.DB.prepare("UPDATE blob_tok SET ts=?2 WHERE device=?1 AND ts < ?2").bind(device, ts).run();
    if ((upd.meta && upd.meta.changes || 0) === 1) return { ok: true, ts };
    let cur = null;
    try {
      cur = await env.DB.prepare("SELECT ts FROM blob_tok WHERE device=?1").bind(device).first();
    } catch (e) { cur = null; }
    if (cur == null) {
      try {
        await env.DB.prepare("INSERT INTO blob_tok (device, ts) VALUES (?1,?2)").bind(device, ts).run();
        return { ok: true, ts };
      } catch (e2) {
        return { ok: false };
      }
    }
    return { ok: false };
  } catch (e) {
    return { ok: false };
  }
}

function bl_tokMsg(device, ts, nonce) {
  const devB = new TextEncoder().encode(device);
  const tsB = new Uint8Array(4);
  tsB[0] = (ts >> 24) & 0xff; tsB[1] = (ts >> 16) & 0xff; tsB[2] = (ts >> 8) & 0xff; tsB[3] = ts & 0xff;
  const msg = new Uint8Array(3 + devB.length + 1 + tsB.length + nonce.length);
  msg.set(new TextEncoder().encode("blb"), 0);
  msg.set(devB, 3);
  msg.set(new Uint8Array([0x00]), 3 + devB.length);
  msg.set(tsB, 4 + devB.length);
  msg.set(nonce, 4 + devB.length + tsB.length);
  return msg;
}

const BL_SKEW = 120;

/** seal+stego the payload; returns {enc:"s",st} | {enc:"c",cy} | {nc:true} when unchanged. */
async function blobSealResponse(env, device, obj, kc) {
  // dedupe on the PLAINTEXT: the cipher embeds a fresh IV every time, so hashing it
  // would never repeat and the app would re-download the same payload forever.
  const json = JSON.stringify(obj);
  const hash = bl_hex(await bl_sha(new TextEncoder().encode(json)));
  // the envelope byte MUST be on the wire: the app reads plain[0] as the zip mode and
  // strips it, so sealing bare json makes every payload lose its leading "{"
  const envelope = await bl_zipEnv(new TextEncoder().encode(json));
  const cipher = await bl_seal(kc, envelope);
  let row = null;
  try {
    row = await env.DB.prepare("SELECT last_payload FROM blob_sessions WHERE device=?1").bind(device).first();
  } catch (e) {}
  if (row && String(row.last_payload || "") === hash) return { nc: true, hash };
  try {
    await env.DB.prepare("UPDATE blob_sessions SET last_payload=?2 WHERE device=?1").bind(device, hash).run();
  } catch (e) {}
  const st = await bl_makeStego(cipher, kc);
  if (st == null) return { enc: "c", cy: bl_b64uEncode(cipher), hash };
  return { enc: "s", st, hash };
}

/** /api/sync with encrypted session. enc:"h" = handshake needed, enc:"x" = auth rejected. */
async function blobSyncProtected(env, request, b) {
  let row = null;
  try {
    row = await env.DB.prepare("SELECT key, exp FROM blob_sessions WHERE device=?1 AND rot=0").bind(String(b.device)).first();
  } catch (e) {
    row = null;
  }
  const keyHex = row && row.key ? String(row.key) : "";
  if (keyHex.length !== 128 || (row.exp || 0) < Date.now()) {
    return new Response(JSON.stringify({ ok: true, v: 2, enc: "h" }), { headers: jsonHeaders() });
  }
  const tok = await bl_tokenCheck(env, String(b.device), String(request.headers.get("x-btok") || ""), bl_hexToB(keyHex.slice(64, 128)));
  if (!tok.ok) return new Response(JSON.stringify({ ok: true, v: 2, enc: "x" }), { status: 403, headers: jsonHeaders() });
  const payload = await syncPayloadObj(env, request, b);
  const kc = bl_hexToB(keyHex.slice(0, 64));
  const sealed = await blobSealResponse(env, String(b.device), payload, kc);
  const base = { ok: true, v: 2, tv: payload.now };
  if (sealed.nc) return new Response(JSON.stringify({ ...base, enc: "n" }), { headers: jsonHeaders() });
  return new Response(JSON.stringify({ ...base, ...sealed }), { headers: jsonHeaders() });
}

/** /api/key - the app NEVER owns a crypto key: the worker issues kc/kt freshly for
 *  every 60-minute bucket derived from device-id + wall time (HMAC of BLOB_ROOT).
 *  Same keys live in blob_sessions, so /api/sync (x-blob v1) just works. */
async function blobKeyIssue(env, request) {
  const b = await request.json().catch(() => null);
  if (!b || !b.device || !b.tok) {
    return new Response(JSON.stringify({ ok: false, error: "issuance" }), { status: 400, headers: jsonHeaders() });
  }
  if (!(await appAuthOk(env, request))) return new Response("unauthorized", { status: 401, headers: jsonHeaders() });
  if (!env.BLOB_ROOT) {
    return new Response(JSON.stringify({ ok: true, v: 2, enc: "x" }), { headers: jsonHeaders() });
  }
  const tok = await bl_tokenCheck(env, String(b.device), String(b.tok), bl_hexToB(String(env.BLOB_ROOT)));
  if (!tok.ok) return new Response(JSON.stringify({ ok: true, v: 2, enc: "x" }), { status: 403, headers: jsonHeaders() });
  const device = String(b.device);
  const bucket = Math.floor(Date.now() / 3600000);
  const msgK = new TextEncoder().encode("blobk1" + device + bucket + "K");
  const msgT = new TextEncoder().encode("blobk1" + device + bucket + "T");
  const rootB = bl_hexToB(String(env.BLOB_ROOT));
  const kc = new Uint8Array(await bl_hmac(rootB, msgK));
  const kt = new Uint8Array(await bl_hmac(rootB, msgT));
  const expSec = (bucket + 1) * 3600; // hard expiry at the bucket boundary (<= 60 min)
  try {
    await env.DB.prepare(
      "INSERT INTO blob_sessions (device, key, exp, rot) VALUES (?1,?2,?3,0) ON CONFLICT(device) DO UPDATE SET key=?2, exp=?3, rot=0, last_payload=''",
    ).bind(device, bl_hex(kc) + bl_hex(kt), expSec * 1000).run();
  } catch (e) {}
  return new Response(JSON.stringify({
    ok: true,
    v: 2,
    enc: "k",
    kc: bl_b64uEncode(kc),
    kt: bl_b64uEncode(kt),
    exp: expSec,
    tv: Date.now(),
  }), { headers: jsonHeaders() });
}
/** Was read as a module variable without ever being declared, so ensureSchema threw
 *  ReferenceError on its very first line and no table was ever created automatically. */
let schemaReady = false;

async function ensureSchema(env, force) {
  if (schemaReady && !force) return;
  let failed = 0;
  const stmts = [
    env.DB.prepare("CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT NOT NULL, updated_at INTEGER)"),
    env.DB.prepare(
      "CREATE TABLE IF NOT EXISTS devices (id TEXT PRIMARY KEY, model TEXT DEFAULT '', app_ver TEXT DEFAULT '', first_seen INTEGER, last_seen INTEGER, ip TEXT DEFAULT '', connected INTEGER DEFAULT 0)",
    ),
    env.DB.prepare("CREATE TABLE IF NOT EXISTS events (device TEXT, type TEXT, ts INTEGER)"),
    env.DB.prepare("CREATE INDEX IF NOT EXISTS idx_events_ts ON events (ts)"),
    env.DB.prepare(
      "CREATE TABLE IF NOT EXISTS servers (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, address TEXT, port INTEGER DEFAULT 443, link TEXT DEFAULT '', category TEXT DEFAULT '', ord INTEGER DEFAULT 99, active INTEGER DEFAULT 1, msg TEXT DEFAULT '')",
    ),
    env.DB.prepare("CREATE TABLE IF NOT EXISTS popup_seen (device TEXT, popup INTEGER, PRIMARY KEY (device, popup))"),
    env.DB.prepare("INSERT OR IGNORE INTO popup_seen (device, popup) VALUES ('__init__', 1)"),
    env.DB.prepare(
      "CREATE TABLE IF NOT EXISTS notifs (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, body TEXT, ts INTEGER, active INTEGER DEFAULT 1)",
    ),
    env.DB.prepare(
      "CREATE TABLE IF NOT EXISTS acks (device TEXT, popup TEXT, kind TEXT, ts INTEGER, PRIMARY KEY (device, popup, kind))",
    ),
    env.DB.prepare(
      "CREATE TABLE IF NOT EXISTS blob_sessions (device TEXT PRIMARY KEY, key TEXT, exp INTEGER, rot INTEGER DEFAULT 0, last_payload TEXT DEFAULT '')",
    ),
    env.DB.prepare("CREATE TABLE IF NOT EXISTS blob_tok (device TEXT PRIMARY KEY, ts INTEGER DEFAULT 0)"),
    env.DB.prepare(
      "CREATE TABLE IF NOT EXISTS payload_files (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, mime TEXT DEFAULT '', size INTEGER DEFAULT 0, title TEXT DEFAULT '', b64 TEXT, active INTEGER DEFAULT 1, ts INTEGER)",
    ),
    env.DB.prepare("CREATE TABLE IF NOT EXISTS payload_seen (device TEXT, pid INTEGER, ts INTEGER, PRIMARY KEY (device, pid))"),
  ];
  try {
    await env.DB.batch(stmts);
  } catch (e) {
    // A D1 batch is one transaction: if a single statement fails, EVERY table in it is rolled
    // back. On a database first created by an older panel that is how "no such table:
    // payload_files" stayed permanent - so run them one by one and keep going.
    for (const s of stmts) {
      try { await s.run(); } catch (e2) { failed++; }
    }
  }
  // older panel DB - add missing column
  try {
    await env.DB.prepare("ALTER TABLE servers ADD COLUMN msg TEXT DEFAULT ''").run();
  } catch (e) {}
  try {
    await env.DB.prepare("ALTER TABLE devices ADD COLUMN info TEXT DEFAULT ''").run();
  } catch (e) {}
  schemaReady = failed === 0;
}
