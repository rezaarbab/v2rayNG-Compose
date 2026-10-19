/**
 * ARBAB Admin Worker — Cloudflare Workers + D1 (no VPS / no hosting)
 *
 * MANAGES: app switch (kill), remote servers, categories,
 *   forced/optional app-update dialogs, popup messages (with per-device shown-count),
 *   admin notifications (in-app), device stats + online/offline counts (device-id dedup).
 *
 * ------------------------------------------------------------------ SETUP
 *  1) npm i -g wrangler
 *     wrangler login
 *  2) wrangler d1 create arbab-db            -> copy database_id into wrangler.toml
 *  3) wrangler secret put ADMIN_PASSWORD     -> type your panel password
 *  4) (optional, stricter) wrangler secret put APP_TOKEN   -> same token in app
 *  5) wrangler deploy
 *     -> open https://<your-worker>.workers.dev  (panel)
 * TEST commands (console):
 *  wrangler d1 execute arbab-db --remote --command "SELECT count(*) FROM devices"
 *  curl -s -X POST https://<worker>/api/sync -H "content-type: application/json" \
 *     -d '{"device":"test-1","model":"emu","ver":1,"ptype":"sync"}'
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
      if (body.pw && body.pw === env.ADMIN_PASSWORD) {
        return new Response(JSON.stringify({ ok: true }), {
          headers: jsonHeaders([
            "Set-Cookie",
            `arbab=${await adminToken(env)}; HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=604800`,
          ]),
        });
      }
      return new Response(JSON.stringify({ ok: false, error: "wrong password" }), { status: 401, headers: jsonHeaders() });
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
        // one link per line:  [name]  link  [category]   (name optional - auto generated)
        let seq = Number((await env.DB.prepare("SELECT COUNT(*) c FROM servers").first()).c) + 1;
        for (const rawPiece of String(b.text || "").split("\n")) {
          const raw = rawPiece.trim();
          if (!raw) continue;
          await env.DB.prepare(
            "INSERT INTO servers (name, address, port, link, category, ord, active) VALUES (?1,?2,?3,?4,?5,?6,1)",
          ).bind(`srv-${seq}`, "0.0.0.0", 443, raw.slice(0, 4096), String(b.category || "").slice(0, 64), seq++).run();
        }
        return okJson();
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
      case "notif.add":
        await env.DB.prepare("INSERT INTO notifs (title, body, ts, active) VALUES (?1,?2,?3,1)")
          .bind(String(b.title || "").slice(0, 120), String(b.body || "").slice(0, 2000), Date.now()).run();
        return okJson();
      case "notif.off":
        await env.DB.prepare("UPDATE notifs SET active=0 WHERE id=?1").bind(Number(b.id)).run();
        return okJson();
      case "stats": {
        const dayStart = new Date().setHours(0, 0, 0, 0);
        const onlineCut = Date.now() - 150 * 1000;
        const total = await env.DB.prepare("SELECT COUNT(*) c FROM devices").first();
        const online = await env.DB.prepare("SELECT COUNT(*) c FROM devices WHERE last_seen>?1").bind(onlineCut).first();
        const onlineConn = await env.DB.prepare("SELECT COUNT(*) c FROM devices WHERE last_seen>?1 AND connected=1").bind(onlineCut).first();
        const connectsToday = await env.DB.prepare("SELECT COUNT(*) c FROM events WHERE ts>?1 AND type='connect'").bind(dayStart).first();
        const syncsToday = await env.DB.prepare("SELECT COUNT(*) c FROM events WHERE ts>?1").bind(dayStart).first();
        return new Response(JSON.stringify({ ok: true, stats: {
          devices: total.c,
          online: online.c,
          onlineConnected: onlineConn.c,
          offline: total.c - online.c,
          connectsToday: connectsToday ? connectsToday.c : 0,
          syncsToday: syncsToday ? syncsToday.c : 0,
        } }), { headers: jsonHeaders() });
      }
      case "srv.list": {
        const rows = await env.DB.prepare("SELECT * FROM servers ORDER BY id DESC LIMIT 500").all();
        return new Response(JSON.stringify({ ok: true, servers: rows.results }), { headers: jsonHeaders() });
      }
      case "notif.list": {
        const rows = await env.DB.prepare("SELECT * FROM notifs ORDER BY id DESC LIMIT 50").all();
        return new Response(JSON.stringify({ ok: true, notifs: rows.results }), { headers: jsonHeaders() });
      }
      default:
        return okJson({ ok: false, error: "niaash" });
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
  return m && (m[1] === (await adminToken(env)));
}

/* ============================ html pages ============================ */

function jsonHeaders(extra) {
  const h = new Headers({ "content-type": "application/json" });
  if (extra) h.append(extra[0], extra[1]);
  return h;
}

const LOGIN_HTML = `<!doctype html><html lang=fa dir=rtl><head><meta charset=utf-8>
<meta name=viewport content="width=device-width,initial-scale=1"><title>ARBAB Panel</title>
<style>body{background:#0e1117;color:#e6e6e8;font-family:system-ui,sans-serif;display:flex;justify-content:center;padding-top:15vh}
.card{background:#171a21;padding:28px 34px;border-radius:18px;box-shadow:0 4px 28px rgba(0,0,0,.5);width:320px;text-align:center}
input{width:100%;padding:12px;border-radius:12px;border:1px solid #2b3040;background:#0e1117;color:#fff;margin-top:14px;box-sizing:border-box}
button{width:100%;padding:12px;border-radius:12px;border:0;background:#2bd48a;color:#05281a;font-weight:700;margin-top:14px;cursor:pointer}
h2{margin:0;color:#2bd48a}</style></head><body><div class=card><h2>ARBAB</h2><p>رمز پنل را بزنید</p>
<input id=pass type=password placeholder=رمز><button onclick=go()><b>ورود</b></button></div>
<script>function go(){fetch('/admin/login',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({pw:document.getElementById('pass').value})}).then(r=>r.ok?location.reload():alert('رمز غلط است'))}</script>
</body></html>`;

function loginPageHtml() {
  return new Response(LOGIN_HTML, { headers: { "content-type": "text/html; charset=utf-8" } });
}

function panelPage(env) {
  const html = `<!doctype html><html lang=fa dir=rtl><head><meta charset=utf-8>
<meta name=viewport content="width=device-width,initial-scale=1"><title>ARBAB Panel</title>
<style>
:root{--bg:#0e1117;--card:#171a21;--line:#252a38;--ok:#2bd48a;--txt:#e6e6e8;--sub:#98a2b3}
body{background:var(--bg);color:var(--txt);font-family:system-ui,sans-serif;margin:0;padding:18px}
h1{color:var(--ok);font-size:20px;margin:0 0 4px}
h2{font-size:15px;margin:0 0 10px;color:var(--ok)}
.card{background:var(--card);border:1px solid var(--line);border-radius:16px;padding:16px;margin-bottom:14px}
.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(110px,1fr));gap:10px;text-align:center}
.stat{background:#10141c;border-radius:12px;padding:12px}
.stat b{display:block;font-size:20px;color:var(--ok)}
.stat span{color:var(--sub);font-size:11px}
input,select,textarea{background:#10141c;border:1px solid var(--line);border-radius:10px;color:var(--txt);padding:9px;width:100%;box-sizing:border-box;margin-top:6px;font-family:inherit}
button{background:var(--ok);color:#04281a;border:0;border-radius:10px;padding:9px 14px;font-weight:700;cursor:pointer;margin-top:6px}
button.gray{background:#2b3040;color:var(--txt)}
button.red{background:#e05c5c;color:#fff}
table{width:100%;border-collapse:collapse;font-size:12px}
td,th{padding:7px;border-bottom:1px solid var(--line);text-align:right}
.row2{display:flex;gap:8px;flex-wrap:wrap}.row2>div{flex:1;min-width:140px}
.lbl{font-size:11px;color:var(--sub);margin-top:8px}
.toast{position:fixed;bottom:14px;right:14px;background:var(--ok);color:#04281a;padding:10px 16px;border-radius:12px;font-weight:700;display:none}
</style></head><body>
<h1>ARBAB Admin</h1>
<button class=gray onclick=logout() style="position:fixed;left:18px;top:16px">خروج</button>

<div class=card><h2>آمار</h2>
<div class=grid id=stats></div>
<div><button class=gray onclick=loadStats()>🔄 بروزرسانی</button></div></div>

<div class=card><h2>سرورها</h2>
<div class=row2><div><div class=lbl>نام (خودت - نه از کانفیگ)</div><input id=s_name></div>
<div><div class=lbl>آدرس</div><input id=s_addr></div>
<div><div class=lbl>پورت</div><input id=s_port type=number value=443></div></div>
<div class=lbl>لینک کانفیگ</div><textarea id=s_link rows=2></textarea>
<div class=row2><div><div class=lbl>دسته‌بندی</div><input id=s_cat placeholder="VIP / Normal"></div>
<div><div class=lbl>ترتیب</div><input id=s_ord type=number value=99></div></div>
<button onclick=api({action:'srv.add',...readSrv()})>افزودن سرور</button>
<div class=lbl>افزودن گروهی (هر خط یک لینک، نام اتوماتیک srv-1, srv-2 ...)</div>
<textarea id=s_bulk rows=3></textarea>
<button onclick=api({action:'srv.bulk',text:document.getElementById('s_bulk').value,category:document.getElementById('s_cat').value})>افزودن گروهی</button>
<div class=lbl></div>
<table id=srv_tbl></table></div>

<div class=card><h2>کنل کلی اپ (Kill switch)</h2>
<p style=font-size:12px;color:var(--sub)>وقتی خاموش باشه، اپ روی همه دستگاه‌ها قفل میشه.</p>
<button id=app_on onclick=appToggle(true)>فعال کن</button>
<button class=red id=app_off onclick=appToggle(false)>غیرفعال کن</button></div>

<div class=card><h2>اپدیت اجباری / اختیاری</h2>
<div class=row2><div><div class=lbl>حداقل نسخه (کمتر از این = اجباری)</div><input id=u_min type=number value=0></div>
<div><div class=lbl>آخرین نسخه (اختیاری)</div><input id=u_latest type=number value=0></div></div>
<div class=lbl>لینک دانلود</div><input id=u_url>
<button onclick=saveUpdate()>ذخیره</button></div>

<div class=card><h2>پیام پاپ‌اپ (ورود)</h2>
<div class=row2><div><div class=lbl>تیتر</div><input id=p_title></div>
<div><div class=lbl>متن</div><input id=p_body></div></div>
<div class=row2><div><div class=lbl>متن دکمه (خالی = بدون دکمه)</div><input id=p_btn></div>
<div><div class=lbl>لینک دکمه</div><input id=p_url></div></div>
<div class=row2><div style=flex:0><label><input type=checkbox id=p_onbtn style="width:auto" checked> دکمه لینک فعال؟</label><br>
<label><input type=checkbox id=p_oncancel style="width:auto" checked> دکمه کنسل فعال؟</label></div>
<div><div class=lbl>حداکثر دفعات نمایش (کلی)</div><input id=p_max type=number value=100></div></div>
<button onclick=savePopup()>ارسال پاپ‌اپ</button>
<div class=lbl id=popup_state></div></div>

<div class=card><h2>نوتیفیکیشن‌ها</h2>
<div class=lbl>تیتر</div><input id=n_title>
<div class=lbl>متن</div><input id=n_body>
<button onclick=api({action:'notif.add',title:document.getElementById('n_title').value,body:document.getElementById('n_body').value}).then(loadNotifs)>ارسال نوتیفیکیشن</button>
<table id=notif_tbl></table></div>
<div class=toast id=toast>✓ انجام شد</div>

<script>
const $=(id)=>document.getElementById(id);
async function api(body){const r=await fetch('/admin/api',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify(body)});const j=await r.json();if(j.error)alert('خطا: '+j.error);toast();return j}
function toast(){const t=$('toast');t.style.display='block';setTimeout(()=>t.style.display='none',1200)}
function logout(){fetch('/admin/logout').then(()=>location.reload())}
function readSrv(){return{name:$('s_name').value,address:$('s_addr').value,port:Number($('s_port').value||443),link:$('s_link').value,category:$('s_cat').value,ord:Number($('s_ord').value||99)}}
async function loadStats(){const j=await api({action:'stats'});if(j.stats){const s=j.stats;$('stats').innerHTML=
 '<div class=stat><b>'+s.devices+'</b><span>کل دستگاه‌ها</span></div>'+
 '<div class=stat><b>'+s.online+'</b><span>آنلاین</span></div>'+
 '<div class=stat><b>'+s.onlineConnected+'</b><span>آنلاین متصل به سرور</span></div>'+
 '<div class=stat><b>'+s.offline+'</b><span>آفلاین</span></div>'+
 '<div class=stat><b>'+s.connectsToday+'</b><span>اتصال امروز</span></div>'+
 '<div class=stat><b>'+s.syncsToday+'</b><span>همگام‌سازی امروز</span></div>'}}
async function srvSave(d){await api({action:'srv.add',...readSrv()});loadServers()}
async function loadServers(){const j=await api({action:'srv.list'});$('srv_tbl').innerHTML='<tr><th>id</th><th>نام</th><th>دسته</th><th>وضعیت</th><th></th></tr>'+
 (j.servers||[]).map(s=>{
   return '<tr><td>'+s.id+'</td>'+
   '<td><input value="'+(s.name||'')+'" onchange=api({action:"srv.rename",id:'+s.id+',name:this.value,category:"'+(s.category||'')+'"}).then(loadServers)></td>'+
   '<td><input value="'+(s.category||'')+'" onchange=api({action:"srv.rename",id:'+s.id+',name:\''+(s.name||'')+'\',category:this.value}).then(loadServers)></td>'+
   '<td>'+(s.active?'✓':'✗')+'</td><td><button class=gray onclick=api({action:"srv.active",id:'+s.id+',on:'+(s.active?'false':'true')+'}).then(loadServers)>'+(s.active?'غیرفعال':'فعال')+'</button> <button class=red onclick=api({action:"srv.del",id:'+s.id+'}).then(loadServers)>حذف</button></td></tr>'
 }).join('')}
async function loadNotifs(){const j=await api({action:'notif.list'});$('notif_tbl').innerHTML='<tr><th>id</th><th>تیتر</th><th>متن</th><th></th></tr>'+
 (j.notifs||[]).map(n=>'<tr><td>'+n.id+'</td><td>'+(n.title||'')+'</td><td>'+(n.body||'')+'</td><td><button class=gray onclick=api({action:"notif.off",id:'+n.id+'}).then(loadNotifs)>حذف</button></td></tr>').join('')}
function appToggle(on){api({action:'set.app',on:on})}
function saveUpdate(){api({action:'set.update',minVersion:Number($('u_min').value||0),latestVersion:Number($('u_latest').value||0),latestUrl:$('u_url').value})}
function savePopup(){api({action:'set.popup',active:true,title:$('p_title').value,body:$('p_body').value,btnText:$('p_btn').value,btnUrl:$('p_url').value,btnEnabled:$('p_onbtn').checked,cancelEnabled:$('p_oncancel').checked,maxShows:Number($('p_max').value||0)})}
loadStats();loadServers();loadNotifs();
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
