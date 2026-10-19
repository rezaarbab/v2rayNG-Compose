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
 * ------------------------------------------------------------------ /SETUP
 */

export default {
  async fetch(request, env, ctx) {
    try {
      await ensureSchema(env);
    } catch (e) {
      // db not ready yet - admin被告 will see the typed error inside the panel
    }
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
      return adminApi(env, body);
    }

    // ---------------- app api ----------------
    if (path === "/api/sync") return appSync(env, request);
    if (path === "/api/beat") return appBeat(env, request);
    if (path === "/api/ack") return appAck(env, request);

    return new Response("not found", { status: 404 });
  },
};

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

  const now = Date.now();
  if (b.connected) {
    await env.DB.prepare(
      "INSERT INTO devices (id, model, app_ver, first_seen, last_seen, ip, connected) VALUES (?1,?2,?3,?4,?4,?5,1) " +
        "ON CONFLICT(id) DO UPDATE SET last_seen=?4, model=?2, app_ver=?3, ip=?5, connected=1",
    )
      .bind(String(b.device), String(b.model || "").slice(0, 64), String(b.ver || 0), now, clientIp(request))
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

  const cfg = await getSettings(env, [
    "app_enabled", "min_version", "latest_version", "latest_url",
    "popup_active", "popup_id", "popup_title", "popup_body", "popup_btn_text",
    "popup_btn_url", "popup_btn_enabled", "popup_cancel_enabled", "popup_max_shows", "popup_shows",
  ]);

  const appEnabled = cfg.app_enabled !== "0";
  const ver = Number(b.ver || 0);
  const min = Number(cfg.min_version || 0);
  const latest = Number(cfg.latest_version || 0);

  let out = {
    ok: true,
    now,
    enabled: appEnabled,
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
  return new Response(JSON.stringify(out), { headers: jsonHeaders() });
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

async function appBeat(env, request) {
  const b = await request.json().catch(() => null);
  if (!b || !b.device) return new Response(JSON.stringify({ ok: false }), { status: 400, headers: jsonHeaders() });
  if (!(await appAuthOk(env, request))) return new Response("unauthorized", { status: 401, headers: jsonHeaders() });
  const now = Date.now();
  await env.DB.prepare(
    "INSERT INTO devices (id, model, app_ver, first_seen, last_seen, ip, connected) VALUES (?1,'',0,?2,?2,'',?3) " +
      "ON CONFLICT(id) DO UPDATE SET last_seen=?2, connected=?3",
  ).bind(String(b.device), now, b.connected ? 1 : 0).run();
  return new Response(JSON.stringify({ ok: true, enabled: (await getSettings(env, ["app_enabled"])).app_enabled !== "0" }), { headers: jsonHeaders() });
}

async function appAck(env, request) {
  const b = await request.json().catch(() => null);
  if (!b || !b.device) return new Response(JSON.stringify({ ok: false }), { status: 400, headers: jsonHeaders() });
  if (!(await appAuthOk(env, request))) return new Response("unauthorized", { status: 401, headers: jsonHeaders() });
  if (b.kind === "event" && b.name) {
    await env.DB.prepare("INSERT INTO events (device, type, ts) VALUES (?1,?2,?3)")
      .bind(String(b.device), String(b.name).slice(0, 32), Date.now()).run();
  } else if (b.kind === "popup" && b.id) {
    await env.DB.prepare("INSERT OR IGNORE INTO acks (device, popup, kind, ts) VALUES (?1,?2,'popup',?3)")
      .bind(String(b.device), String(b.id), Date.now()).run();
    const cur = Number((await getSettings(env, ["popup_shows"])).popup_shows || 0);
    await setSetting(env, "popup_shows", String(cur + 1));
  } else if (b.kind === "notif" && b.id) {
    await env.DB.prepare("INSERT OR IGNORE INTO acks (device, popup, kind, ts) VALUES (?1,?2,'notif',?3)")
      .bind(String(b.device), String(b.id), Date.now()).run();
  }
  return new Response(JSON.stringify({ ok: true }), { headers: jsonHeaders() });
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
        return okJson();
      }
      case "srv.bulk": {
        let seq = Number((await env.DB.prepare("SELECT COUNT(*) c FROM servers").first()).c) + 1;
        const defCat = String(b.category || "").trim().slice(0, 64) || (cats[0] || "Normal");
        let added = 0;
        for (const rawPiece of String(b.text || "").split("\n")) {
          const parts = rawPiece.split("|").map((x) => x.trim());
          let name = "", link = "", cat = "";
          if (parts.length >= 3) { name = parts[0]; link = parts[1]; cat = parts[2]; }
          else if (parts.length === 2) { name = parts[0]; link = parts[1]; }
          else { link = parts[0] || ""; }
          if (!link) continue;
          await env.DB.prepare(
            "INSERT INTO servers (name, address, port, link, category, ord, active, msg) VALUES (?1,?2,?3,?4,?5,?6,1,'')",
          ).bind(name.slice(0, 64) || `srv-${seq}`, "0.0.0.0", 443, link.slice(0, 4096), (cat || defCat).slice(0, 64), seq++).run();
          added++;
        }
        return okJson({ added });
      }
      case "srv.del":
        await env.DB.prepare("DELETE FROM servers WHERE id=?1").bind(Number(b.id)).run();
        return okJson();
      case "srv.active":
        await env.DB.prepare("UPDATE servers SET active=?2 WHERE id=?1").bind(Number(b.id), b.on ? 1 : 0).run();
        return okJson();
      case "srv.rename":
        await env.DB.prepare("UPDATE servers SET name=?2, category=?3, msg=?4 WHERE id=?1").bind(
          Number(b.id), String(b.name).slice(0, 64), String(b.category || "").slice(0, 64),
          String(b.msg == null ? "" : b.msg).slice(0, 200)).run();
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
        return okJson();
      }
      case "srv.catmove":
        await env.DB.prepare("UPDATE servers SET category=?2 WHERE category=?1").bind(
          String(b.from || "").slice(0, 64), String(b.to || "").slice(0, 64)).run();
        return okJson();
      case "cats.set": {
        const list = (Array.isArray(b.cats) ? b.cats : []).map(String).map((c) => c.trim().slice(0, 64)).filter(Boolean).slice(0, 64);
        await setSetting(env, "categories", JSON.stringify(list));
        return okJson();
      }
      case "set.app":
        await setSetting(env, "app_enabled", b.on ? "1" : "0");
        return okJson();
      case "set.flags": {
        const legal = { ping_on_start: 1, flag_on_start: 1, auto_ping_new: 1, auto_flag_new: 1 };
        for (const [k, v] of Object.entries(b.flags || {})) {
          if (legal[k] && ["1", "0", "auto"].includes(String(v))) {
            await setSetting(env, k, String(v));
          }
        }
        return okJson();
      }
      case "set.update":
        await setSetting(env, "min_version", String(Number(b.minVersion || 0)));
        await setSetting(env, "latest_version", String(Number(b.latestVersion || 0)));
        await setSetting(env, "latest_url", String(b.latestUrl || "").slice(0, 512));
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
      case "srv.list": {
        const rows = await env.DB.prepare("SELECT * FROM servers ORDER BY ord ASC, id ASC LIMIT 500").all();
        return new Response(JSON.stringify({ ok: true, servers: rows.results,
          cats,
          app: (await getSettings(env, ["app_enabled"])).app_enabled !== "0" }), { headers: jsonHeaders() });
      }
      case "devices.list": {
        const rows = await env.DB.prepare(
          "SELECT id, model, app_ver, first_seen, last_seen, ip, connected FROM devices ORDER BY last_seen DESC LIMIT 200",
        ).all();
        return new Response(JSON.stringify({ ok: true, cut: Date.now() - 150000, devices: rows.results }), { headers: jsonHeaders() });
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
::selection{background:rgba(43,212,138,.3)}
::-webkit-scrollbar{width:8px;height:8px}
::-webkit-scrollbar-thumb{background:#263042;border-radius:8px}
header{position:sticky;top:0;z-index:60;background:rgba(11,15,20,.88);backdrop-filter:blur(12px);
border-bottom:1px solid var(--line);padding:12px 18px;display:flex;align-items:center;gap:12px}
.logo{width:38px;height:38px;border-radius:12px;background:linear-gradient(135deg,var(--ok),#22e07a);
display:flex;align-items:center;justify-content:center;font-weight:900;color:#04281a;font-size:15px;
box-shadow:0 4px 18px rgba(43,212,138,.35)}
header h1{font-size:16px;margin:0;flex:1}
header h1 small{display:block;color:var(--sub);font-size:11px;font-weight:400;direction:ltr;text-align:right}
.layout{display:flex;max-width:1100px;margin:0 auto;min-height:calc(100vh - 63px)}
nav{width:172px;flex-shrink:0;border-left:1px solid var(--line);padding:16px 12px;display:flex;flex-direction:column;gap:5px}
nav button{display:flex;align-items:center;gap:9px;background:transparent;color:var(--sub);border:0;border-radius:12px;
padding:11px 13px;font-weight:600;font-size:13px;cursor:pointer;width:100%;text-align:right;justify-content:flex-start}
nav button:hover{background:rgba(43,212,138,.08);color:var(--txt);filter:none}
nav button.on{background:linear-gradient(135deg,rgba(43,212,138,.18),rgba(43,212,138,.05));color:var(--ok);
border:1px solid rgba(43,212,138,.35)}
nav .sp{height:16px}
nav .badge2{margin-right:auto;background:var(--card2);border:1px solid var(--line);color:var(--sub);
border-radius:99px;padding:1px 8px;font-size:10px;font-weight:700}
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
padding:8px 10px}.layout{flex-direction:column}nav .badge2{display:none}}`;

function panelPage(env) {
  const html = `<!doctype html><html lang=fa dir=rtl><head><meta charset=utf-8>
<meta name=viewport content="width=device-width,initial-scale=1"><title>ARBAB Panel</title>
<style>${THEME_CSS}</style></head><body>
<header><div class=logo>A</div>
<h1>ARBAB Admin<small>god-panel.lastofanarchy.workers.dev</small></h1>
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
<div class=card><h2>📊 آمار <span class=cnt id=stat_live>هر ۳۰ ثانیه بروز میشه</span></h2>
<div class=grid id=stats><div class=empty>در حال دریافت...</div></div>
<button class="gray sm" onclick=loadStats() style=margin-top:12px>🔄 بروزرسانی</button></div>

<div class=card><h2>🛡 کنترل کلی اپ (Kill switch)</h2>
<div class=toolbar>
<label class=switch><input type=checkbox id=app_sw onchange=appToggle(this.checked)><span></span></label>
<span id=app_state class=note></span>
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
</div>

<!-- ================= DEVICES ================= -->
<div class=tab id=tab_dev>
<div class=card><h2>👥 دستگاه‌ها <span class=cnt id=dev_cnt></span></h2>
<input class=search id=dev_q placeholder="جستجو..." oninput=renderDevices()>
<div id=dev_list class=empty>در حال دریافت...</div></div>
</div>

</main>
</div></div>

<div class=toast id=toast>انجام شد</div>

<script>
let PW='';

const $=(id)=>document.getElementById(id);
let CATS=[],SRV=[],DEVS=[],CUT=0,FILT=-1;
function esc(s){return String(s==null?'':s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;').replace(/'/g,'&#39;')}
function showErr(m){const b=$('err');if(!m){b.style.display='none';return}b.style.display='block';b.textContent=m}
async function api(body){
 let r;
 try{r=await fetch('/admin/api',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({...body,pw:PW})})}
 catch(e){showErr('network error: '+e);toast('خطای شبکه',1);throw e}
 if(r.status===401){const j=await r.json().catch(()=>({}));logout(j.error||'رمز غلط است');throw new Error('locked')}
 const j=await r.json().catch(()=>({ok:false,error:'bad json from worker'}));
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
async function bulkAdd(){const j=await api({action:'srv.bulk',text:$('s_bulk').value,category:$('s_bcat').value});
$('s_bulk').value='';toast('افزوده شد: '+((j&&j.added)||0));loadAll()}
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
 const [c,s]=await Promise.all([api({action:'cfg.load'}),api({action:'srv.list'})]);
 CATS=c.cats||[];
 $('app_sw').checked=!!c.cfg.app_enabled;appState(c.cfg.app_enabled!=='0');
 SRV=s.servers||[];
 renderFilters();renderCatUI();renderServers()}
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
 const lk=document.createElement('div');lk.className='lnk';lk.textContent=(s.link||'')+(s.address&&s.address!=='0.0.0.0'?' | '+s.address+':'+(s.port||443):'');
 lk.title='کلیک = کپی لینک';
 lk.onclick=()=>{navigator.clipboard&&navigator.clipboard.writeText(s.link||'').then(()=>toast('لینک کپی شد'))};d.appendChild(lk);
 return d}
async function renderServers(){
 if(!$('tab_srv'))return;
 const q=($('srv_q').value||'').toLowerCase();const only=$('grp_only')&&$('grp_only').checked;const fc=FILT>=0&&FILT<CATS.length?CATS[FILT]:'';
 let list=SRV.filter(s=>{
  if(only&&!s.active)return false;
  if(fc&&s.category!==fc)return false;
  if(q&&!(((s.name||'')+' '+(s.category||'')+' '+(s.link||'')).toLowerCase().includes(q)))return false;return true});
$('srv_cnt').textContent=list.length+'/'+SRV.length;
const box=$('srv_list');box.className='';box.innerHTML='';
if(!list.length){box.className='empty';box.textContent='موردی یافت نشد';return}
list.forEach((s,i)=>box.appendChild(srvRow(s,i,list)))}

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
async function loadDevices(){const j=await api({action:'devices.list'});DEVS=j.devices||[];CUT=j.cut||0;renderDevices()}
function renderDevices(){const q=($('dev_q').value||'').toLowerCase();
const list=DEVS.filter(d=>!q||((d.id||'')+' '+(d.model||'')).toLowerCase().includes(q));
$('dev_cnt').textContent=list.length+' دستگاه';
const box=$('dev_list');
if(!list.length){box.className='empty';box.textContent='دستگاهی ثبت نشده';return}
box.className='';box.innerHTML='<table><tr><th>مدل</th><th>وضعیت</th><th>نسخه</th><th>IP</th><th>آخرین دیده</th></tr>'+
list.map(d=>{const online=(Date.now()-Number(d.last_seen||0))<CUT;
const last=d.last_seen?new Date(Number(d.last_seen)).toLocaleString('fa-IR'):'-';
return '<tr><td style="direction:ltr;text-align:right">'+esc(d.model||d.id.slice(0,8))+'</td>'+
'<td><span class="badge '+(d.connected&&online?'b-on':online?'b-y':'b-off')+'">'+(d.connected&&online?'متصل':online?'آنلاین':'آفلاین')+'</span></td>'+
'<td>'+(d.app_ver||'-')+'</td><td style=direction:ltr>'+esc((d.ip||'-').slice(0,16))+'</td><td>'+last+'</td></tr>'}).join('')+'</table>'}

/* ---- boot ---- */
/* password on EVERY visit - nothing persisted; all panels load in parallel for speed */
async function loadAll(){
 await Promise.allSettled([
  loadServers(),
  loadStats(),
  loadFlags(),
  loadNotifs(),
  loadPopup(),
  loadUpdate(),
  loadDevices(),
 ])}
(()=>{logout()})();
setInterval(()=>{if(PW)loadStats().catch(()=>{})},30000);
</script>

</body></html>`;
  return new Response(html, {
    headers: { "content-type": "text/html; charset=utf-8" },
  });
}

/* ============================ schema ============================ */

let schemaReady = false;
async function ensureSchema(env) {
  if (schemaReady) return;
  await env.DB.batch([
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
  ]);
  // older panel DB - add missing column
  try {
    await env.DB.prepare("ALTER TABLE servers ADD COLUMN msg TEXT DEFAULT ''").run();
  } catch (e) {}
  schemaReady = true;
}
