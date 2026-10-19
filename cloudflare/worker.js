/**
 * ARBAB Admin Worker — Cloudflare Workers + D1 (no VPS / no hosting)
 *
 * MANAGES: app switch (kill), remote servers with category management,
 *   forced/optional app-update dialogs, popup messages (with per-device shown-count),
 *   admin notifications (in-app), device stats + online/offline counts (device-id dedup).
 *
 * ------------------------------------------------------------------ SETUP (Console)
 *  1) npm i -g wrangler
 *  2) wrangler login
 *  3) wrangler d1 create god-db
 *     -> database_id را کپی کنید؛ در wrangler.toml کنار همین فایل بنویسید:
 *        [[d1_databases]]
 *        binding = "DB"
 *        database_name = "god-db"
 *        database_id = "<کپی شده>"
 *  4) cd cloudflare && wrangler deploy
 *  5) رمز پنل (دو روش):
 *     a) داشبورد: Workers & Pages > god-panel > Settings >
 *        Variables and Secrets > Add > ADMIN_PASSWORD (نوع: Secret) > رمز دلخواه
 *        (همان بخش "Runtime variables and secrets")
 *     b) کنسول:  wrangler secret put ADMIN_PASSWORD
 *     پنل تا وقتی ADMIN_PASSWORD ست نشده قفل است (401).
 *  6) پنل: https://god-panel.lastofanarchy.workers.dev
 * TEST commands (console):
 *  wrangler d1 execute god-db --remote --command "SELECT count(*) FROM devices"
 *  curl -s -X POST https://god-panel.lastofanarchy.workers.dev/api/sync -H "content-type: application/json" -d "{\"device\":\"test-1\",\"model\":\"emu\",\"ver\":1}"
 * ------------------------------------------------------------------ /SETUP
 */

export default {
  async fetch(request, env, ctx) {
    try {
      await ensureSchema(env);
    } catch (e) {
      // db not reachable yet - keep serving login page
    }
    const url = new URL(request.url);
    const path = url.pathname.replace(/\/+$/, "") || "/";

    // ---------------- admin panel ----------------
    if (path === "/" || path === "/admin" || path === "/panel") {
      return adminAuthOk(env, request) ? panelPage(env) : loginPageHtml();
    }
    if (path === "/admin/login" && request.method === "POST") {
      const body = await request.json().catch(() => ({}));
      if (env.ADMIN_PASSWORD && body.pw && body.pw === env.ADMIN_PASSWORD) {
        return new Response(JSON.stringify({ ok: true }), {
          headers: jsonHeaders([
            "Set-Cookie",
            `arbab=${await adminToken(env)}; HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=604800`,
          ]),
        });
      }
      return new Response(JSON.stringify({ ok: false, error: env.ADMIN_PASSWORD ? "wrong password" : "panel password not configured on worker" }), { status: 401, headers: jsonHeaders() });
    }
    if (path === "/admin/logout") {
      return new Response(JSON.stringify({ ok: true }), {
        headers: jsonHeaders(["Set-Cookie", "arbab=; Max-Age=0; Path=/"]),
      });
    }
    if (path.startsWith("/admin/api") && request.method === "POST") {
      if (!adminAuthOk(env, request)) return new Response("unauthorized", { status: 401 });
      const body = await request.json().catch(() => ({}));
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
  const cfg = await getSettings(env, [
    "app_enabled",
    "min_version",
    "latest_version",
    "latest_url",
    "popup_active",
    "popup_id",
    "popup_title",
    "popup_body",
    "popup_btn_text",
    "popup_btn_url",
    "popup_btn_enabled",
    "popup_cancel_enabled",
    "popup_max_shows",
    "popup_shows",
  ]);

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
  if (min > 0 && ver < min) {
    out.update = { forced: true, text: cfg.latest_version && latest > ver ? "نسخه جدید" : "برای ادامه باید اپدیت کنید", btnText: "Update", url: cfg.latest_url || "" };
  } else if (latest > ver && (cfg.latest_url || "") !== "") {
    out.update = { forced: false, text: "نسخه جدید در دسترس است", btnText: "Update", url: cfg.latest_url };
  }
  // popup: shown max N times globally, once per device per popup id
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
  out.notifications = out.notifications.filter(
    (n) => !dismissedNotifs.has(String(n.id)),
  );
  return new Response(JSON.stringify(out), { headers: jsonHeaders() });
}

async function listServers(env) {
  const q = "SELECT id, name, address, port, link, category FROM servers WHERE active=1 ORDER BY ord ASC, id ASC LIMIT 500";
  const rows = await env.DB.prepare(q).all();
  return (rows.results || []).map((r) => ({
    id: String(r.id),
    name: r.name,          //admin-assigned - never from the config (#NAME tag)
    address: r.address,
    port: r.port || 443,
    link: r.link,
    category: r.category || "",
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
    // connect / disconnect events - feed the panel stats
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

/* ============================ categories ============================ */

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
    switch (act) {
      case "srv.add": {
        // name NEVER taken from the config link (#NAME) - admin provides it, or default srv-<n>
        const name = String(b.name || "").trim().slice(0, 64) || `srv-${(b.seq ?? Date.now()) % 10000}`;
        await env.DB.prepare(
          "INSERT INTO servers (name, address, port, link, category, ord, active) VALUES (?1,?2,?3,?4,?5,?6,1)",
        ).bind(name, String(b.address || "").slice(0, 256), Number(b.port || 443), String(b.link || "").slice(0, 4096),
          String(b.category || "").slice(0, 64), Number(b.ord || 0)).run();
        return okJson();
      }
      case "srv.bulk": {
        // one link per line:  name | link | category   (name/category optional)
        let seq = Number((await env.DB.prepare("SELECT COUNT(*) c FROM servers").first()).c) + 1;
        const defCat = String(b.category || "").slice(0, 64) || "Normal";
        for (const rawPiece of String(b.text || "").split("\n")) {
          const parts = rawPiece.split("|").map((x) => x.trim());
          let name = "", link = "", cat = "";
          if (parts.length >= 3) { name = parts[0]; link = parts[1]; cat = parts[2]; }
          else if (parts.length === 2) { name = parts[0]; link = parts[1]; }
          else { link = parts[0] || ""; }
          if (!link) continue;
          await env.DB.prepare(
            "INSERT INTO servers (name, address, port, link, category, ord, active) VALUES (?1,?2,?3,?4,?5,?6,1)",
          ).bind(name.slice(0, 64) || `srv-${seq}`, "0.0.0.0", 443, link.slice(0, 4096), (cat || defCat).slice(0, 64), seq++).run();
        }
        return okJson({ added: seq });
      }
      case "srv.del":
        await env.DB.prepare("DELETE FROM servers WHERE id=?1").bind(Number(b.id)).run();
        return okJson();
      case "srv.active":
        await env.DB.prepare("UPDATE servers SET active=?2 WHERE id=?1").bind(Number(b.id), b.on ? 1 : 0).run();
        return okJson();
      case "srv.rename":
        await env.DB.prepare("UPDATE servers SET name=?2, category=?3 WHERE id=?1").bind(Number(b.id), String(b.name).slice(0, 64), String(b.category || "").slice(0, 64)).run();
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
      case "srv.catmove": {
        // move all servers of one category to another
        await env.DB.prepare("UPDATE servers SET category=?2 WHERE category=?1").bind(String(b.from || "").slice(0, 64), String(b.to || "").slice(0, 64)).run();
        return okJson();
      }
      case "cats.set": {
        const list = (Array.isArray(b.cats) ? b.cats : []).map(String).map((c) => c.trim().slice(0, 64)).filter(Boolean).slice(0, 64);
        await setSetting(env, "categories", JSON.stringify(list));
        return okJson();
      }
      case "cats.get": {
        return okJson({ cats: await getCategories(env) });
      }
      case "set.app":
        await setSetting(env, "app_enabled", b.on ? "1" : "0");
        return okJson();
      case "set.update": {
        await setSetting(env, "min_version", String(Number(b.minVersion || 0)));
        await setSetting(env, "latest_version", String(Number(b.latestVersion || 0)));
        await setSetting(env, "latest_url", String(b.latestUrl || "").slice(0, 512));
        return okJson();
      }
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
        const c = await getSettings(env, ["popup_active", "popup_title", "popup_body", "popup_shows", "popup_max_shows", "popup_id"]);
        return okJson({ popup: c });
      }
      case "cfg.load": {
        const cfg = await getSettings(env, ["min_version", "latest_version", "latest_url", "app_enabled"]);
        return okJson({ cfg, cats: await getCategories(env) });
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
        const act = await env.DB.prepare("SELECT COUNT(*) c FROM servers WHERE active=1").first();
        const deact = await env.DB.prepare("SELECT COUNT(*) c FROM servers WHERE active=0").first();
        return new Response(JSON.stringify({ ok: true, stats: {
          devices: total.c,
          online: online.c,
          onlineConnected: onlineConn.c,
          offline: total.c - online.c,
          connectsToday: connectsToday ? connectsToday.c : 0,
          syncsToday: syncsToday ? syncsToday.c : 0,
          activeServers: act.c,
          offServers: deact.c,
        } }), { headers: jsonHeaders() });
      }
      case "srv.list": {
        const rows = await env.DB.prepare("SELECT * FROM servers ORDER BY ord ASC, id ASC LIMIT 500").all();
        return new Response(JSON.stringify({ ok: true, servers: rows.results,
          app: (await getSettings(env, ["app_enabled"])).app_enabled !== "0" }), { headers: jsonHeaders() });
      }
      case "devices.list": {
        const rows = await env.DB.prepare("SELECT id, model, app_ver, first_seen, last_seen, ip, connected FROM devices ORDER BY last_seen DESC LIMIT 200").all();
        return new Response(JSON.stringify({ ok: true, cut: Date.now() - 150000, devices: rows.results }), { headers: jsonHeaders() });
      }
      default:
        return okJson({ ok: false, error: "unknown action" });
    }
  } catch (e) {
    return okJson({ ok: false, error: String(e).slice(0, 300) });
  }
}

function okJson(extra = {}) {
  return new Response(JSON.stringify({ ok: true, ...extra }), { headers: jsonHeaders() });
}

/* ============================ auth helpers ============================ */

async function adminToken(env) {
  const enc = new TextEncoder();
  const key = await crypto.subtle.importKey("raw", enc.encode(env.ADMIN_PASSWORD), "HMAC", false, ["sign"]);
  const mac = await crypto.subtle.sign("HMAC", key, enc.encode("arbab-admin"));
  return [...new Uint8Array(mac)].map((x) => x.toString(16).padStart(2, "0")).join("");
}

async function adminAuthOk(env, request) {
  if (!env.ADMIN_PASSWORD) return false; // refuse if password not configured
  const cookie = request.headers.get("cookie") || "";
  const m = cookie.match(/arbab=([a-f0-9]+)/);
  return !!(m && (m[1] === (await adminToken(env))));
}

/* ============================ html pages ============================ */

function jsonHeaders(extra) {
  const h = new Headers({ "content-type": "application/json" });
  if (extra) h.append(extra[0], extra[1]);
  return h;
}

const THEME_CSS = `:root{--bg:#0b0f14;--card:#151a23;--card2:#10141c;--line:#232a38;--ok:#2bd48a;--okd:#1a9e63;
--txt:#e8ecf1;--sub:#8b96a8;--red:#e05c5c;--amber:#e5b457;--blue:#5b8cff;--r:14px}
*{box-sizing:border-box}
body{background:radial-gradient(1200px 600px at 80% -10%,rgba(43,212,138,.07),transparent),var(--bg);color:var(--txt);
font-family:system-ui,'Segoe UI',Tahoma,sans-serif;margin:0}
::selection{background:rgba(43,212,138,.3)}
header{position:sticky;top:0;z-index:50;background:rgba(11,15,20,.85);backdrop-filter:blur(10px);
border-bottom:1px solid var(--line);padding:14px 20px;display:flex;align-items:center;gap:12px}
.logo{width:38px;height:38px;border-radius:12px;background:linear-gradient(135deg,var(--ok),#22e07a);
display:flex;align-items:center;justify-content:center;font-weight:900;color:#04281a;font-size:15px;
box-shadow:0 4px 18px rgba(43,212,138,.35)}
header h1{font-size:17px;margin:0;flex:1}
header h1 small{display:block;color:var(--sub);font-size:11px;font-weight:400}
main{max-width:980px;margin:0 auto;padding:18px}
.card{background:var(--card);border:1px solid var(--line);border-radius:var(--r);padding:18px;margin-bottom:16px;
box-shadow:0 2px 16px rgba(0,0,0,.25)}
.card h2{font-size:14px;margin:0 0 14px;color:var(--ok);display:flex;align-items:center;gap:8px}
.cnt{background:var(--card2);border:1px solid var(--line);color:var(--sub);font-size:11px;border-radius:99px;padding:2px 10px;font-weight:600}
.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(110px,1fr));gap:10px}
.stat{background:linear-gradient(180deg,var(--card2),#0d1118);border:1px solid var(--line);border-radius:12px;
padding:14px 10px;text-align:center;transition:transform .15s}
.stat:hover{transform:translateY(-2px)}
.stat b{display:block;font-size:22px;color:var(--ok)}
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
.chip{display:inline-flex;align-items:center;gap:4px;background:var(--card2);border:1px solid var(--line);border-radius:99px;
padding:5px 13px;font-size:12px;margin:3px;cursor:pointer;transition:all .15s;user-select:none}
.chip:hover{border-color:var(--okd)}
.chip.on{background:rgba(43,212,138,.15);border-color:var(--ok);color:var(--ok);font-weight:700}
.chip .x{color:var(--sub);padding:0 2px}
.chip .x:hover{color:var(--red)}
.srv{background:var(--card2);border:1px solid var(--line);border-radius:12px;padding:10px 14px;margin-top:8px;
display:flex;align-items:center;gap:8px;flex-wrap:wrap;transition:border .15s}
.srv:hover{border-color:#2f3a4f}
.srv.off{opacity:.45}
.srv input{border:none;background:none;padding:4px}
.srv .nm{flex:1;min-width:120px;font-weight:600;color:var(--txt)}
.srv .ct{width:105px;color:var(--amber)}
.srv .lnk{font-size:10px;color:var(--sub);flex-basis:100%;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;
direction:ltr;text-align:left;cursor:copy}
.srv .lnk:hover{color:var(--ok)}
.badge{font-size:10px;border-radius:99px;padding:3px 10px;font-weight:700;white-space:nowrap}
.b-on{background:rgba(43,212,138,.15);color:var(--ok)}
.b-off{background:rgba(224,92,92,.15);color:var(--red)}
.b-y{background:rgba(229,180,87,.15);color:var(--amber)}
.b-bl{background:rgba(91,140,255,.15);color:var(--blue)}
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
.toolbar label{font-size:12px;color:var(--sub);display:flex;align-items:center;gap:6px}
.toast{position:fixed;bottom:18px;left:18px;background:var(--ok);color:#04281a;padding:11px 20px;border-radius:12px;
font-weight:800;box-shadow:0 6px 24px rgba(0,0,0,.4);display:none;z-index:99}
.toast.err{background:var(--red);color:#fff}
.empty{color:var(--sub);font-size:12px;text-align:center;padding:16px}
.note{font-size:11px;color:var(--sub);line-height:1.8}
.catgrp{margin:10px 0 2px;color:var(--ok);font-size:12px;font-weight:700}`;

const LOGIN_HTML = `<!doctype html><html lang=fa dir=rtl><head><meta charset=utf-8>
<meta name=viewport content="width=device-width,initial-scale=1"><title>ARBAB — ورود</title>
<style>${THEME_CSS}</style></head><body>
<header><div class=logo>A</div><h1>ARBAB<small>god-panel</small></h1></header>
<main style="padding-top:10vh;max-width:360px">
<div class=card style=text-align:center>
<div class=logo style="margin:0 auto 12px;width:52px;height:52px;font-size:20px;border-radius:16px">A</div>
<h2 style=justify-content:center>ورود به پنل</h2>
<p class=note>رمز پنل را وارد کنید</p>
<input id=pass type=password placeholder=رمز autofocus style=text-align:center onkeydown="if(event.key==='Enter')go()">
<button onclick=go() style=width:100%;margin-top:14px>ورود</button>
<p class=note id=err style="color:var(--red);display:none"></p>
</div></main>
<script>
function go(){fetch('/admin/login',{method:'POST',headers:{'content-type':'application/json'},
body:JSON.stringify({pw:document.getElementById('pass').value})}).then(r=>r.json()).then(j=>{
if(j.ok)location.reload();else{const e=document.getElementById('err');e.style.display='block';e.textContent='⛔ '+(j.error||'رمز غلط است')}})}
</script></body></html>`;

function loginPageHtml() {
  return new Response(LOGIN_HTML, { headers: { "content-type": "text/html; charset=utf-8" } });
}

function panelPage(env) {
  const html = `<!doctype html><html lang=fa dir=rtl><head><meta charset=utf-8>
<meta name=viewport content="width=device-width,initial-scale=1"><title>ARBAB Panel</title>
<style>${THEME_CSS}</style></head><body>
<header><div class=logo>A</div>
<h1>ARBAB Admin<small>god-panel.lastofanarchy.workers.dev</small></h1>
<button class="gray sm" onclick=logout()>خروج</button></header>
<main>

<div class=card><h2>📊 آمار <span class=cnt id=stat_live>هر ۳۰ ثانیه</span></h2>
<div class=grid id=stats class=grid><div class=empty>در حال دریافت...</div></div></div>

<div class=card><h2>🛡 کنترل کلی اپ (Kill switch)</h2>
<div class=toolbar>
<label class=switch><input type=checkbox id=app_sw onchange=appToggle(this.checked)><span></span></label>
<span id=app_state class=note></span>
</div>
<p class=note>وقتی خاموش باشه، اپ روی همه دستگاه‌ها قفل میشه.</p></div>

<div class=card><h2>🗂 دسته‌بندی‌ها <span class=cnt id=cat_cnt></span></h2>
<div id=cat_chips style=margin:4px012px></div>
<div class=toolbar>
<input id=cat_new placeholder="نام دسته جدید..." style=max-width:240px>
<button class=sm onclick=catAdd()>افزودن</button>
</div>
<p class=note>「✕」حذف دسته (سرورها به Normal منتقل میشن) — 「✎」تغییرنام/ادغام دسته.</p></div>

<div class=card><h2>🖥 سرورها <span class=cnt id=srv_cnt></span></h2>
<input class=search id=srv_q placeholder="جستجو..." oninput="renderServers()">
<div class=toolbar>
<label><input type=checkbox id=only_on onchange="renderServers()"> فقط فعال‌ها</label>
</div>
<div id=srv_filters style="margin:6px 0"></div>
<div id=srv_list class=empty>در حال دریافت...</div>

<div style=margin-top:16px>
<h2 style=color:var(--blue)>افزودن سرور جدید</h2>
<div class=row2>
<div><div class=lbl>نام (از خودت - نه از کانفیگ)</div><input id=s_name></div>
<div><div class=lbl>دسته</div><input id=s_cat list=catlist placeholder=Normal></div>
<div><div class=lbl>پورت</div><input id=s_port type=number value=443></div>
<div><div class=lbl>ترتیب</div><input id=s_ord type=number value=99></div></div>
<div class=lbl>آدرس (اختیاری)</div><input id=s_addr>
<div class=lbl>لینک کانفیگ</div><textarea id=s_link rows=2></textarea>
<button onclick=srvAdd() style=margin-top:10px>افزودن سرور</button>

<h2 style="color:var(--blue);margin-top:18px">افزودن گروهی</h2>
<p class=note>هر خط: <code style=direction:ltr>name | link | category</code> — نام و دسته اختیاری‌اند (فقط لینک هم قبول است).</p>
<textarea id=s_bulk rows=4></textarea>
<div class=row2 style="margin-top:8px">
<div><div class=lbl>دسته پیش‌فرض</div><input id=s_bcat list=catlist value=Normal></div>
<div style="flex:0;display:flex;align-items:flex-end"><button onclick=bulkAdd()>افزودن گروهی</button></div></div>
</div></div>

<div class=card><h2>🔔 اپدیت</h2>
<div class=row2><div><div class=lbl>حداقل نسخه (کمتر از این = اجباری)</div><input id=u_min type=number value=0></div>
<div><div class=lbl>آخرین نسخه (اختیاری)</div><input id=u_latest type=number value=0></div></div>
<div class=lbl>لینک دانلود</div><input id=u_url>
<button onclick=saveUpdate() style=margin-top:10px>ذخیره تنظیمات اپدیت</button></div>

<div class=card><h2>💬 پیام پاپ‌اپ</h2>
<div class=toolbar>
<label class=switch><input type=checkbox id=p_active onchange=savePopup()><span></span></label>
<span class=note>فعال بودن ارسال</span>
<span style=margin-right:auto id=popup_state class=note></span>
<button class="gray sm" onclick=refreshPopupId()>ارسال مجدد (شناسه جدید)</button>
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
<button onclick=resetShows() style=margin-top:10px>ریست شمارنده نمایش‌ها</button></div>

<div class=card><h2>📢 نوتیفیکیشن‌ها</h2>
<div class=row2><div><div class=lbl>تیتر</div><input id=n_title></div>
<div><div class=lbl>متن</div><input id=n_body></div>
<div style="flex:0;display:flex;align-items:flex-end"><button onclick=notifAdd()>ارسال</button></div></div>
<div id=notif_tbl></div></div>

<div class=card><h2>👥 دستگاه‌ها <span class=cnt id=dev_cnt></span></h2>
<input class=search id=dev_q placeholder="جستجو..." oninput=renderDevices()>
<div id=dev_list class=empty>در حال دریافت...</div></div>

<div class=toast id=toast>انجام شد</div>
</main>
<script>
const $=(id)=>document.getElementById(id);
let CATS=[],SRV=[],DEVS=[],CUT=0;
function esc(s){return String(s==null?'':s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;').replace(/'/g,'&#39;')}
async function api(body){const r=await fetch('/admin/api',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify(body)});const j=await r.json();if(j.error){toast('خطا: '+j.error,1)}else toast();return j}
function toast(msg,err){const t=$('toast');t.textContent=msg||'✓ انجام شد';t.className=err?'toast err':'toast';t.style.display='block';clearTimeout(t._h);t._h=setTimeout(()=>t.style.display='none',1400)}
function logout(){fetch('/admin/logout').then(()=>location.reload())}
function catlist(){const l=$('catlist');if(l)l.remove();const d=document.createElement('datalist');d.id='catlist';d.innerHTML=CATS.map(c=>'<option value="'+esc(c)+'">').join('');document.body.appendChild(d)}

/* ---- cats ---- */
async function catsSetting(){await api({action:'cats.set',cats:CATS});renderCatUI()}
function renderCatUI(){$('cat_cnt').textContent=CATS.length+' دسته';
const box=$('cat_chips');box.innerHTML='';
CATS.forEach((c,i)=>{const s=document.createElement('span');s.className='chip'+(window.FILT===i?' on':'');s.textContent=c+' ';
s.onclick=()=>{window.FILT=i;renderCatUI();renderFilters()};
const x=document.createElement('span');x.className='x';x.textContent='✕';x.title='حذف دسته';x.onclick=e=>{e.stopPropagation();catDel(i)};
const ed=document.createElement('span');ed.className='x';ed.textContent='✎';ed.title='تغییرنام/ادغام';ed.onclick=e=>{e.stopPropagation();catEdit(i)};
const st=document.createElement('span');st.textContent=(SRV.filter(s2=>s2.category===c).length);
s.appendChild(st);s.appendChild(x);s.appendChild(ed);box.appendChild(s)});
catlist()}
function catAdd(){const n=($('cat_new').value||'').trim();if(!n)return;$('cat_new').value='';if(CATS.includes(n)){toast('تکراری',1);return}CATS.push(n);catsSetting()}
function catDel(i){if(!confirm('دسته «'+CATS[i]+'» حذف شود؟ سرورهایش به Normal منتقل میشوند.'))return;const old=CATS[i];CATS.splice(i,1);catsSetting().then(()=>api({action:'srv.catmove',from:old,to:'Normal'}).then(loadAll))}
function catEdit(i){const n=prompt('نام دسته:',CATS[i]);if(n==null||!n.trim()||n.trim()===CATS[i])return;const old=CATS[i];CATS[i]=n.trim();catsSetting().then(()=>api({action:'srv.catmove',from:old,to:n.trim()}).then(loadAll))}
function catOf(){return window.FILT>=0&&window.FILT<CATS.length?CATS[window.FILT]:''}
function renderFilters(){window.FILT==null&&(window.FILT=-1);
const box=$('srv_filters');box.innerHTML='';
const mk=(t,idx)=>{const c=document.createElement('span');c.className='chip'+(window.FILT===idx?' on':'');c.textContent=t;c.onclick=()=>{window.FILT=idx;renderFilters();renderServers()};box.appendChild(c)};
mk('همه',-1);CATS.forEach((c,i)=>mk(c,i))}

/* ---- servers ---- */
async function srvAdd(){const b={action:'srv.add',name:$('s_name').value,address:$('s_addr').value,port:Number($('s_port').value||443),link:$('s_link').value,category:$('s_cat').value,ord:Number($('s_ord').value||99)};
await api(b);['s_name','s_addr','s_link'].forEach(i=>{$(i).value=''});loadAll()}
async function bulkAdd(){const j=await api({action:'srv.bulk',text:$('s_bulk').value,category:$('s_bcat').value});$('s_bulk').value='';loadAll()}
async function loadServers(){const j=await api({action:'srv.list'});SRV=j.servers||[];
$('app_sw').checked=!!j.app;appState(j.app);renderFilters();renderServers();renderCatUI()}
function appState(on){$('app_state').innerHTML=on?'<span class="badge b-on">اپ فعال است</span>':'<span class="badge b-off">اپ قفل - همه دستگاه‌ها بسته اند</span>'}
function appToggle(on){api({action:'set.app',on:on}).then(()=>{appState(on)})}
let ordMap={};
async function renderServers(){const q=($('srv_q').value||'').toLowerCase();const only=$('only_on').checked;const fc=catOf();
let list=SRV.filter(s=>{
 if(only&&!s.active)return false;
 if(fc&&s.category!==fc)return false;
 if(q&&!(((s.name||'')+' '+(s.category||'')+' '+(s.link||'')).toLowerCase().includes(q)))return false;return true});
ordMap={};list.forEach((s,i)=>ordMap[s.id]=i);
$('srv_cnt').textContent=list.length+'/'+SRV.length;
const box=$('srv_list');box.className='';box.innerHTML='';
if(!list.length){box.className='empty';box.textContent='موردی یافت نشد';return}
list.forEach((s,i)=>{box.appendChild(srvRow(s,i,list))})}
function srvRow(s,i,list){
 const d=document.createElement('div');d.className='srv'+(s.active?'':' off');
 const ord=document.createElement('div');ord.className='ord';
 const up=document.createElement('button');up.className='gray sm';up.textContent='▲';up.title='بالا';up.disabled=i===0;
 up.onclick=()=>api({action:'srv.ord',id:s.id,up:true}).then(loadAll);ord.appendChild(up);
 const dn=document.createElement('button');dn.className='gray sm';dn.textContent='▼';dn.title='پایین';dn.disabled=i===list.length-1;
 dn.onclick=()=>api({action:'srv.ord',id:s.id,up:false}).then(loadAll);ord.appendChild(dn);d.appendChild(ord);
 const nm=document.createElement('input');nm.className='nm';nm.value=s.name||'';
 nm.onchange=()=>api({action:'srv.rename',id:s.id,name:nm.value,category:s.category||''}).then(loadAll);d.appendChild(nm);
 const ct=document.createElement('input');ct.className='ct';ct.setAttribute('list','catlist');ct.value=s.category||'';
 ct.onchange=()=>api({action:'srv.rename',id:s.id,name:s.name||'',category:ct.value}).then(loadAll);d.appendChild(ct);
 const bd=document.createElement('span');bd.className='badge '+(s.active?'b-on':'b-off');bd.textContent=s.active?'✓ فعال':'✕ خاموش';d.appendChild(bd);
 const tb=document.createElement('button');tb.className='gray sm';tb.textContent=s.active?'خاموش':'روشن';
 tb.onclick=()=>api({action:'srv.active',id:s.id,on:!s.active}).then(loadAll);d.appendChild(tb);
 const db=document.createElement('button');db.className='red sm';db.textContent='حذف';
 db.onclick=()=>{if(confirm('سرور «'+(s.name||s.id)+'» حذف شود؟'))api({action:'srv.del',id:s.id}).then(loadAll)};d.appendChild(db);
 const lk=document.createElement('div');lk.className='lnk';lk.textContent=s.link||'';lk.title='کلیک = کپی';
 lk.onclick=()=>{navigator.clipboard&&navigator.clipboard.writeText(s.link||'').then(()=>toast('لینک کپی شد'))};d.appendChild(lk);
 return d}

/* ---- update / popup / notifs ---- */
async function loadUpdate(){const j=await api({action:'cfg.load'});const c=j.cfg||{};
$('u_min').value=Number(c.min_version||0);$('u_latest').value=Number(c.latest_version||0);$('u_url').value=c.latest_url||''}
async function saveUpdate(){await api({action:'set.update',minVersion:Number($('u_min').value||0),latestVersion:Number($('u_latest').value||0),latestUrl:$('u_url').value})}
async function loadPopup(){const j=await api({action:'popup.state'});const p=j.popup||{};
$('p_active').checked=p.popup_active==='1';
$('p_title').value=p.popup_title||'';$('p_body').value=p.popup_body||'';
$('p_max').value=Number(p.popup_max_shows||100);
popupState(p)}
function popupState(p){p=p||{};const on=$('p_active').checked;
$('popup_state').innerHTML=on?'<span class="badge b-on">در حال ارسال — '+Number(p.popup_shows||0)+' نمایش از سقف '+Number(p.popup_max_shows||0)+'</span>':'<span class="badge b-off">متوقف</span>'}
async function savePopup(){await api({action:'set.popup',active:$('p_active').checked,title:$('p_title').value,body:$('p_body').value,btnText:$('p_btn').value,btnUrl:$('p_url').value,btnEnabled:$('p_onbtn').checked,cancelEnabled:$('p_oncancel').checked,maxShows:Number($('p_max').value||100)});loadPopup()}
function refreshPopupId(){savePopup()}
async function resetShows(){await api({action:'set.popup',active:$('p_active').checked,title:$('p_title').value,body:$('p_body').value,btnText:$('p_btn').value,btnUrl:$('p_url').value,btnEnabled:$('p_onbtn').checked,cancelEnabled:$('p_oncancel').checked,maxShows:$('p_max').value});toast('شمارنده صفر شد و پاپ‌اپ دوباره برای همه ارسال می‌شود');loadPopup()}
async function notifAdd(){await api({action:'notif.add',title:$('n_title').value,body:$('n_body').value});$('n_title').value='';$('n_body').value='';loadNotifs()}
async function loadNotifs(){const j=await api({action:'notif.list'});
const box=$('notif_tbl');
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
box.className='';box.innerHTML='<table><tr><th>مدل</th><th>وضعیت</th><th>نسخه</th><th>IP</th><th>آخرین دیده‌شدن</th></tr>'+
list.map(d=>{const online=(Date.now()-Number(d.last_seen||0))<CUT;
const last=d.last_seen?new Date(Number(d.last_seen)).toLocaleString('fa-IR'):'-';
return '<tr><td style="direction:ltr;text-align:right">'+esc(d.model||d.id.slice(0,8))+'</td>'+
'<td><span class="badge '+(d.connected&&online?'b-on':online?'b-y':'b-off')+'">'+(d.connected&&online?'متصل':online?'آنلاین':'آفلاین')+'</span></td>'+
'<td>'+(d.app_ver||'-')+'</td><td style=direction:ltr>'+esc((d.ip||'-').slice(0,16))+'</td><td>'+last+'</td></tr>'}).join('')+'</table>'}

/* ---- boot ---- */
async function loadAll(){renderFilters();await loadCats();await loadServers();await loadStats();await loadNotifs();await loadPopup();await loadUpdate();await loadDevices();renderDevices()}
loadAll();
setInterval(()=>{loadStats().catch(()=>{});loadDevices().catch(()=>{})},30000);
</script>
</body></html>`;
  return new Response(html, { headers: { "content-type": "text/html; charset=utf-8" } });
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
      "CREATE TABLE IF NOT EXISTS servers (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, address TEXT, port INTEGER DEFAULT 443, link TEXT DEFAULT '', category TEXT DEFAULT '', ord INTEGER DEFAULT 99, active INTEGER DEFAULT 1)",
    ),
    env.DB.prepare(
      "CREATE TABLE IF NOT EXISTS popup_seen (device TEXT, popup INTEGER, PRIMARY KEY (device, popup))",
    ),
    env.DB.prepare("INSERT OR IGNORE INTO popup_seen (device, popup) VALUES ('__init__', 1)"),
    env.DB.prepare(
      "CREATE TABLE IF NOT EXISTS notifs (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, body TEXT, ts INTEGER, active INTEGER DEFAULT 1)",
    ),
  ]);
  schemaReady = true;
}
