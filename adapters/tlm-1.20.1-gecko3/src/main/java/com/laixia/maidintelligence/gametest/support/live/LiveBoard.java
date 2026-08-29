package com.laixia.maidintelligence.gametest.support.live;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.core.BlockPos;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 无头测试的**实时监视器**：不开客户端、不要渲染——本地起一个微型 HTTP
 * 口，浏览器开 <a href="http://127.0.0.1:25599/">面板</a> 就能看每个在跑
 * 测试里她的位置、动作供词与尾迹（玩家点名："不需要精细的画面，就需要知
 * 道实时情况"）。
 *
 * <p>数据由读数带（{@code PathwalkTrace.sample}）顺手喂——测试基建已经
 * 每 tick 采样所有关键测试，这里只是把同一份数据开一扇实时的窗。JDK 内置
 * HttpServer，零依赖；只听回环地址，测试世界一次性，端口随进程生灭。
 */
public final class LiveBoard {
    private static final int PORT = 25599;

    /** 每条测试保留的尾迹长度。 */
    private static final int TRAIL = 60;

    /** 多少毫秒没更新就从面板上消失（测试结束/超时）。 */
    private static final long FADE_MS = 4000L;

    private record Snap(double x, double y, double z, String note,
            long wall) {
    }

    private static final Map<String, Deque<Snap>> FEEDS =
            new ConcurrentHashMap<>();

    /** 每场一次的地形俯视（列高度表）与最近一次规划路径。 */
    private static final Map<String, String> TERRAIN =
            new ConcurrentHashMap<>();
    private static final Map<String, String> PLANS =
            new ConcurrentHashMap<>();

    /** 事件日志：note 变化的时刻记一行，最近若干条。 */
    private static final Map<String, Deque<String>> EVENTS =
            new ConcurrentHashMap<>();
    private static final Map<String, String> LAST_NOTE =
            new ConcurrentHashMap<>();
    private static final Map<String, Long> BORN =
            new ConcurrentHashMap<>();

    /** 场景编号：注册顺序发号，面板与 /state?id=N 都认它（玩家点名：
     *  "为场景设置ID，方便查找和专注"）。 */
    private static final Map<String, Integer> IDS =
            new ConcurrentHashMap<>();
    private static final java.util.concurrent.atomic.AtomicInteger NEXT_ID =
            new java.util.concurrent.atomic.AtomicInteger();
    private static volatile HttpServer server;

    private LiveBoard() {
    }

    /** 读数带每采一帧喂一帧：坐标转成相对场原点。 */
    public static void post(String name, BlockPos zero, EntityMaid maid,
            String note) {
        ensureServer();
        // **并行副本各开一格**：PinSpread 把同名测试展开成同批多份，名字
        // 一样、场地不同——共用一个 feed 就是两条帧流交错，面板上她在两
        // 点间瞬移、走与跳每帧对调（栅栏圈实测看成"机枪连跳"，追了半天
        // 的假病）。场原点是天然的副本键。
        String key = name + " @" + zero.getX() + "," + zero.getZ();
        IDS.computeIfAbsent(key, k -> NEXT_ID.incrementAndGet());
        TERRAIN.computeIfAbsent(key, n -> scanTerrain(maid, zero));
        capturePlan(key, zero, maid);
        logEvent(key, zero, maid, note == null ? "-" : note);
        Deque<Snap> feed = FEEDS.computeIfAbsent(key,
                n -> new ArrayDeque<>());
        synchronized (feed) {
            feed.addLast(new Snap(
                    maid.getX() - zero.getX(),
                    maid.getY() - zero.getY(),
                    maid.getZ() - zero.getZ(),
                    note == null ? "-" : note,
                    System.currentTimeMillis()));
            while (feed.size() > TRAIL) {
                feed.removeFirst();
            }
        }
    }

    /** note 一变就记一行：相对秒、动作、位置、路径进度。
     *  变化只认**动作词**（首词）——供词可以带数值（regroup b0.43），
     *  数值每帧微变，按全文比较一秒十几行就把日志刷穿了。 */
    private static void logEvent(String name, BlockPos zero,
            EntityMaid maid, String note) {
        String verb = note.indexOf(' ') > 0
                ? note.substring(0, note.indexOf(' ')) : note;
        String prev = LAST_NOTE.put(name, verb);
        if (verb.equals(prev)) {
            return;
        }
        long born = BORN.computeIfAbsent(name,
                n -> System.currentTimeMillis());
        String pathBit = "";
        if (maid.getNavigation() instanceof com.laixia.maidintelligence
                .feature.behavior.tlm.pathing.SureFootedNavigation sure
                && sure.voxelPath() != null) {
            pathBit = " " + sure.voxelPath().cursor() + "/"
                    + sure.voxelPath().length();
        }
        String line = String.format("%5.1fs %-8s (%.1f,%.1f,%.1f)%s",
                (System.currentTimeMillis() - born) / 1000.0, note,
                maid.getX() - zero.getX(), maid.getY() - zero.getY(),
                maid.getZ() - zero.getZ(), pathBit);
        Deque<String> log = EVENTS.computeIfAbsent(name,
                n -> new ArrayDeque<>());
        synchronized (log) {
            log.addLast(line);
            while (log.size() > 16) {
                log.removeFirst();
            }
        }
    }

    /** 场地俯视：每列最高支撑面的高度（相对场原点，-9 表示空柱）。 */
    private static String scanTerrain(EntityMaid maid, BlockPos zero) {
        StringBuilder json = new StringBuilder(
                "{\"x0\":-2,\"z0\":-2,\"w\":22,\"h\":18,\"top\":[");
        var level = maid.level();
        var cursor = new BlockPos.MutableBlockPos();
        for (int dz = -2; dz < 16; dz++) {
            for (int dx = -2; dx < 20; dx++) {
                int top = -9;
                for (int dy = 12; dy >= 0; dy--) {
                    cursor.set(zero.getX() + dx, zero.getY() + dy,
                            zero.getZ() + dz);
                    if (!level.getBlockState(cursor)
                            .getCollisionShape(level, cursor).isEmpty()) {
                        top = dy;
                        break;
                    }
                }
                if (dx > -2 || dz > -2) {
                    json.append(',');
                }
                json.append(top);
            }
        }
        return json.append("]}").toString();
    }

    /** 她当前的规划路径（锚点序列）；没有就记空。 */
    private static void capturePlan(String name, BlockPos zero,
            EntityMaid maid) {
        String plan = "[]";
        if (maid.getNavigation() instanceof com.laixia.maidintelligence
                .feature.behavior.tlm.pathing.SureFootedNavigation sure
                && sure.voxelPath() != null) {
            var vp = sure.voxelPath();
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < vp.length(); i++) {
                if (i > 0) {
                    json.append(',');
                }
                var at = vp.anchorAt(i).at();
                json.append('[')
                        .append(Math.round((at.x - zero.getX()) * 10.0)
                                / 10.0)
                        .append(',')
                        .append(Math.round((at.y - zero.getY()) * 10.0)
                                / 10.0)
                        .append(',')
                        .append(Math.round((at.z - zero.getZ()) * 10.0)
                                / 10.0)
                        .append(']');
            }
            plan = json.append(']').toString();
        }
        PLANS.put(name, plan);
    }

    private static void ensureServer() {
        if (server != null) {
            return;
        }
        synchronized (LiveBoard.class) {
            if (server != null) {
                return;
            }
            try {
                HttpServer boot = HttpServer.create(
                        new InetSocketAddress("127.0.0.1", PORT), 0);
                boot.createContext("/", exchange -> {
                    byte[] body = "/state".equals(
                            exchange.getRequestURI().getPath())
                            ? stateJson(exchange.getRequestURI().getQuery())
                            : pageHtml();
                    String type = "/state".equals(
                            exchange.getRequestURI().getPath())
                            ? "application/json"
                            : "text/html; charset=utf-8";
                    exchange.getResponseHeaders().add("Content-Type", type);
                    exchange.sendResponseHeaders(200, body.length);
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(body);
                    }
                });
                boot.start();
                server = boot;
                System.out.println(
                        "[live-board] http://127.0.0.1:" + PORT + "/");
            } catch (IOException e) {
                System.out.println("[live-board] port busy: " + e);
                server = null;
            }
        }
    }

    /** {@code query} 支持 id=N：只回那一场（fade 也不挡——专注模式连
     *  已收场的现场也要能翻）。 */
    private static byte[] stateJson(String query) {
        long now = System.currentTimeMillis();
        int wanted = -1;
        if (query != null && query.startsWith("id=")) {
            try {
                wanted = Integer.parseInt(query.substring(3));
            } catch (NumberFormatException ignored) {
            }
        }
        int focus = wanted;
        StringBuilder json = new StringBuilder("[");
        FEEDS.forEach((name, feed) -> {
            synchronized (feed) {
                int id = IDS.getOrDefault(name, 0);
                if (focus > 0 && id != focus) {
                    return;
                }
                Snap last = feed.peekLast();
                if (last == null
                        || (focus <= 0 && now - last.wall > FADE_MS)) {
                    return;
                }
                if (json.length() > 1) {
                    json.append(',');
                }
                json.append("{\"id\":").append(id)
                        .append(",\"name\":\"").append(name.replace('"', ' '))
                        .append("\",\"note\":\"")
                        .append(last.note.replace('"', ' '))
                        .append("\",\"terrain\":")
                        .append(TERRAIN.getOrDefault(name, "null"))
                        .append(",\"plan\":")
                        .append(PLANS.getOrDefault(name, "[]"))
                        .append(",\"log\":[")
                        .append(logJson(name))
                        .append("],\"trail\":[");
                boolean first = true;
                for (Snap snap : feed) {
                    if (!first) {
                        json.append(',');
                    }
                    first = false;
                    json.append('[')
                            .append(Math.round(snap.x * 10.0) / 10.0)
                            .append(',')
                            .append(Math.round(snap.y * 10.0) / 10.0)
                            .append(',')
                            .append(Math.round(snap.z * 10.0) / 10.0)
                            .append(']');
                }
                json.append("]}");
            }
        });
        json.append(']');
        return json.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String logJson(String name) {
        Deque<String> log = EVENTS.get(name);
        if (log == null) {
            return "";
        }
        StringBuilder json = new StringBuilder();
        synchronized (log) {
            for (String line : log) {
                if (json.length() > 0) {
                    json.append(',');
                }
                json.append('"')
                        .append(line.replace('"', ' ').replace('\\', ' '))
                        .append('"');
            }
        }
        return json.toString();
    }

    private static byte[] pageHtml() {
        // 页面内嵌：distribution 聚合各模块只收 Java，classpath 资源在无
        // 头测试服务器里装不到——一个监视器不值得为此折腾打包链。
        return PAGE.getBytes(StandardCharsets.UTF_8);
    }

    private static final String PAGE = """
<!doctype html>
<meta charset="utf-8">
<title>maid live board</title>
<style>
  body { background:#14161a; color:#cfd3da; font:12px/1.4 Consolas, monospace;
         margin:12px; }
  h1 { font-size:14px; margin:0 0 10px; color:#8fc7ff; }
  #grid { display:flex; flex-wrap:wrap; gap:10px; }
  .cell { background:#1c2026; border:1px solid #2c323b; border-radius:6px;
          padding:6px; width:356px; }
  .row { display:flex; gap:6px; }
  .views canvas { display:block; }
  canvas + canvas { margin-top:3px; }
  .log { width:132px; font-size:10px; line-height:1.5; color:#93a0ad;
         white-space:pre; overflow:hidden; }
  .cell b { display:block; font-size:11px; color:#e8ecf2;
            white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
  .cell i { font-style:normal; font-size:11px; }
  canvas { background:#12151a; border-radius:4px; display:block;
           margin-top:4px; }
</style>
<h1>maid live board <span id="n"></span></h1>
<div id="grid"></div>
<script>
const vb = n => (n || '').split(' ')[0];
const NOTE_COLOR = {
  walk:'#6fd66f', climb:'#e8c256', leap:'#e8c256', takeoff:'#ff9b42',
  flight:'#5aa8ff', airborne:'#5a7fff', drop:'#b088ff', stepoff:'#b088ff',
  regroup:'#d0d0d0', stale:'#ff5f5f', done:'#3adba6', '-':'#8a919c'
};
async function tick() {
  try {
    const feeds = await (await fetch('/state')).json();
    const grid = document.getElementById('grid');
    document.getElementById('n').textContent = '· ' + feeds.length + ' live';
    const seen = new Set();
    for (const f of feeds) {
      seen.add(f.name);
      let cell = document.getElementById('c_' + f.name);
      if (!cell) {
        cell = document.createElement('div');
        cell.className = 'cell'; cell.id = 'c_' + f.name;
        cell.innerHTML = '<b></b><i></i><div class=row>'
          + '<div class=views>'
          + '<canvas class=top width=208 height=120></canvas>'
          + '<canvas class=front width=208 height=66></canvas>'
          + '<canvas class=side width=208 height=66></canvas>'
          + '</div><div class=log></div></div>';
        grid.appendChild(cell);
      }
      cell.querySelector('b').textContent = '#'+(f.id||'?')+' '+f.name;
      const note = cell.querySelector('i');
      note.textContent = f.note;
      note.style.color = NOTE_COLOR[vb(f.note)] || '#cfd3da';
      draw(cell.querySelector('.top'), f);
      drawProfile(cell.querySelector('.front'), f, 0);
      drawProfile(cell.querySelector('.side'), f, 2);
      cell.querySelector('.log').textContent =
          (f.log || []).slice().reverse().join('\\n');
    }
    for (const cell of [...grid.children]) {
      if (!seen.has(cell.id.slice(2))) cell.remove();
    }
  } catch (e) { document.getElementById('n').textContent = '· offline'; }
  setTimeout(tick, 400);
}
function draw(cv, f) {
  const g = cv.getContext('2d');
  g.clearRect(0, 0, cv.width, cv.height);
  const T = f.terrain;
  let x0=-2, z0=-2, x1=20, z1=16;
  if (!T) {
    x0=1e9; x1=-1e9; z0=1e9; z1=-1e9;
    for (const p of f.trail) { x0=Math.min(x0,p[0]); x1=Math.max(x1,p[0]);
                               z0=Math.min(z0,p[2]); z1=Math.max(z1,p[2]); }
    x0-=1; x1+=1; z0-=1; z1+=1;
  } else { x0=T.x0; z0=T.z0; x1=T.x0+T.w; z1=T.z0+T.h; }
  const s = Math.min(cv.width/(x1-x0), (cv.height-14)/(z1-z0));
  const px = (x, z) => [ (x-x0)*s + (cv.width-(x1-x0)*s)/2,
                         (z-z0)*s + 12 ];
  if (T) {
    for (let iz = 0; iz < T.h; iz++) for (let ix = 0; ix < T.w; ix++) {
      const top = T.top[iz*T.w + ix];
      if (top < 0) continue;
      const lum = 28 + top*14;
      g.fillStyle = 'rgb(' + lum + ',' + (lum+4) + ',' + (lum+10) + ')';
      const [cx, cz] = px(T.x0+ix, T.z0+iz);
      g.fillRect(cx, cz, s+0.5, s+0.5);
    }
  }
  if (f.plan && f.plan.length) {
    g.strokeStyle = '#e8c256'; g.setLineDash([3,2]); g.beginPath();
    for (let i = 0; i < f.plan.length; i++) {
      const [x, y] = px(f.plan[i][0], f.plan[i][2]);
      i ? g.lineTo(x, y) : g.moveTo(x, y);
    }
    g.stroke(); g.setLineDash([]);
    const end = f.plan[f.plan.length-1], [ex, ez] = px(end[0], end[2]);
    g.fillStyle = '#e8c256';
    g.fillRect(ex-2, ez-2, 4, 4);
  }
  const t = f.trail;
  if (t.length) {
    g.strokeStyle = '#5fd68f'; g.beginPath();
    for (let i = 0; i < t.length; i++) {
      const [x, y] = px(t[i][0], t[i][2]);
      i ? g.lineTo(x, y) : g.moveTo(x, y);
    }
    g.stroke();
    const last = t[t.length-1], [lx, ly] = px(last[0], last[2]);
    g.fillStyle = NOTE_COLOR[vb(f.note)] || '#fff';
    g.beginPath(); g.arc(lx, ly, 3.5, 0, 7); g.fill();
    g.fillStyle = '#9aa3af';
    g.fillText('y ' + last[1].toFixed(1) + '  (' + last[0].toFixed(1)
        + ', ' + last[2].toFixed(1) + ')', 4, 10);
  }
}
function drawProfile(cv, f, axis) {
  const g = cv.getContext('2d');
  g.clearRect(0, 0, cv.width, cv.height);
  const T = f.terrain;
  const label = axis === 0 ? 'front x-y' : 'side z-y';
  g.fillStyle = '#5a6472'; g.fillText(label, 4, 9);
  let a0, a1;
  if (T) { a0 = axis===0 ? T.x0 : T.z0;
           a1 = a0 + (axis===0 ? T.w : T.h); }
  else {
    a0=1e9; a1=-1e9;
    for (const p of f.trail) { a0=Math.min(a0,p[axis]); a1=Math.max(a1,p[axis]); }
    a0-=1; a1+=1;
  }
  const Y0 = -1, Y1 = 13;
  const s = cv.width/(a1-a0), sy = (cv.height-12)/(Y1-Y0);
  const px = (a, y) => [ (a-a0)*s, cv.height - (y-Y0)*sy ];
  if (T) {
    const n = axis===0 ? T.w : T.h;
    for (let i = 0; i < n; i++) {
      let sky = -9;
      const m = axis===0 ? T.h : T.w;
      for (let j = 0; j < m; j++) {
        const top = axis===0 ? T.top[j*T.w + i] : T.top[i*T.w + j];
        if (top > sky) sky = top;
      }
      if (sky < 0) continue;
      const [bx, by] = px(a0+i, sky+1);
      g.fillStyle = '#3a424e';
      g.fillRect(bx, by, s+0.5, cv.height - by);
    }
  }
  if (f.plan && f.plan.length) {
    g.strokeStyle = '#e8c256'; g.setLineDash([3,2]); g.beginPath();
    for (let i = 0; i < f.plan.length; i++) {
      const [x, y] = px(f.plan[i][axis], f.plan[i][1]);
      i ? g.lineTo(x, y) : g.moveTo(x, y);
    }
    g.stroke(); g.setLineDash([]);
  }
  const t = f.trail;
  if (t.length) {
    g.strokeStyle = '#5fd68f'; g.beginPath();
    for (let i = 0; i < t.length; i++) {
      const [x, y] = px(t[i][axis], t[i][1]);
      i ? g.lineTo(x, y) : g.moveTo(x, y);
    }
    g.stroke();
    const last = t[t.length-1], [lx, ly] = px(last[axis], last[1]);
    g.fillStyle = NOTE_COLOR[vb(f.note)] || '#fff';
    g.beginPath(); g.arc(lx, ly, 3, 0, 7); g.fill();
  }
}
tick();
</script>
""";
}
