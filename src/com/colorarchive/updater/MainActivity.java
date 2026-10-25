package com.colorarchive.updater;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String REPO = "one899/ColorArchive-Updater";
    private static final String TAG = "lkm-latest";
    private static final String R2_BASE = "https://lkm.colorarchive.cloud/";
    private static final String META = "/metadata/ksu/kernelsu.ko";
    private static final String META_VER = "/metadata/ksu/version.txt";
    private static final String KO_NAME = "kernelsu-stripped.ko";
    private static final String BOOT_NAME = "boot-lkm-9rt.img";
    private static final String MANIFEST = "manifest.json";

    // 品牌固定配色
    private static final int C_PRIMARY = 0xFF4F5BD5;
    private static final int C_SURFACE = 0xFFFBFBFF;
    private static final int C_SURFACE_VAR = 0xFFEEF0F8;
    private static final int C_ON_SURFACE = 0xFF1A1B21;
    private static final int C_ON_VAR = 0xFF5A5D72;
    private static final int C_CONTAINER = 0xFFE3E6FF;
    private static final int C_ON_CONTAINER = 0xFF16194A;
    private static final int C_OK = 0xFF1F8A4C;
    private static final int C_WARN = 0xFFB4690E;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextView heroTitle, heroSub, chipRoot, chipMod, chipHot;
    private TextView koLocal, koCloud, bootLocal, bootCloud, koBadge, bootBadge;
    private ProgressBar bar;

    private String cloudKoVer = null, cloudBootVer = null;
    private long cloudKoEpoch = 0, cloudBootEpoch = 0;
    private String cloudKoSha = "", cloudBootSha = "";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        edgeToEdge();

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(C_SURFACE);
        sv.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int h = dp(20);
        root.setPadding(h, dp(12) + statusBarHeight(), h, dp(24));

        root.addView(header());
        root.addView(section("设备状态"));
        root.addView(heroCard());
        root.addView(section("版本"));
        root.addView(versionCard());
        root.addView(section("操作"));
        root.addView(filledCard(IconView.ICON_DOWNLOAD, "更新内核模块", "0.4 MB · 写入 /metadata 后重启生效",
                new Runnable() { public void run() { downloadKo(); } }));
        root.addView(tonalCard(IconView.ICON_BOLT, "刷写 boot 镜像", "192 MB · 校验并备份后写入 boot 分区",
                new Runnable() { public void run() { flashBoot(); } }));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(8), 0, 0);
        TextView reboot = textButton("重启设备");
        reboot.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                confirm("重启设备？", "应用新模块需要重启", "重启", "取消",
                        new Runnable() { public void run() { execRoot("reboot"); } });
            }
        });
        TextView refresh = textButton("刷新状态");
        refresh.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { new Thread(new Runnable() {
                public void run() { refresh(); loadManifest(true); } }).start(); }
        });
        row.addView(reboot, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(refresh, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(row);

        sv.addView(root);
        setContentView(sv);

        refresh();
        new Thread(new Runnable() { public void run() { loadManifest(false); } }).start();
    }

    // ---------- 主题 ----------
    private void edgeToEdge() {
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().setNavigationBarColor(C_SURFACE);
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
            if (Build.VERSION.SDK_INT >= 23) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }

    private int statusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : dp(24);
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    private GradientDrawable bg(int color, int r) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(r));
        return d;
    }

    private void ripple(View v, int color, int r) {
        if (Build.VERSION.SDK_INT >= 21) {
            v.setBackground(new RippleDrawable(ColorStateList.valueOf(alpha(C_PRIMARY, 0.12f)), null, null));
        }
    }

    private int alpha(int c, float a) {
        return Color.argb((int) (255 * a), Color.red(c), Color.green(c), Color.blue(c));
    }

    // ---------- 组件 ----------
    private View header() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(4));

        TextView logo = new TextView(this);
        logo.setText("CA");
        logo.setTextSize(16);
        logo.setTypeface(null, Typeface.BOLD);
        logo.setTextColor(Color.WHITE);
        logo.setGravity(Gravity.CENTER);
        GradientDrawable circle = new GradientDrawable();
        circle.setColor(C_PRIMARY);
        circle.setShape(GradientDrawable.OVAL);
        logo.setBackground(circle);
        row.addView(logo, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(dp(14), 0, 0, 0);
        TextView a = new TextView(this);
        a.setText("ColorArchive");
        a.setTextSize(22);
        a.setTypeface(null, Typeface.BOLD);
        a.setTextColor(C_ON_SURFACE);
        TextView c = new TextView(this);
        c.setText("LKM 更新器");
        c.setTextSize(13);
        c.setTextColor(C_ON_VAR);
        t.addView(a);
        t.addView(c);
        row.addView(t);
        return row;
    }

    private TextView section(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(12);
        t.setTextColor(C_ON_VAR);
        t.setLetterSpacing(0.08f);
        t.setPadding(dp(4), dp(18), 0, dp(10));
        return t;
    }

    private View heroCard() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(bg(C_CONTAINER, 24));
        c.setPadding(dp(20), dp(20), dp(20), dp(20));

        heroTitle = new TextView(this);
        heroTitle.setText("检测中…");
        heroTitle.setTextSize(20);
        heroTitle.setTypeface(null, Typeface.BOLD);
        heroTitle.setTextColor(C_ON_CONTAINER);
        c.addView(heroTitle);

        heroSub = new TextView(this);
        heroSub.setTextSize(12);
        heroSub.setTextColor(alpha(C_ON_CONTAINER, 0.78f));
        heroSub.setPadding(0, dp(4), 0, dp(14));
        heroSub.setLineSpacing(dp(3), 1f);
        c.addView(heroSub);

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chipRoot = chip("root");
        chipMod = chip("模块");
        chipHot = chip("热更新");
        chips.addView(chipRoot);
        LinearLayout.LayoutParams g = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        g.leftMargin = dp(8);
        chips.addView(chipMod, g);
        LinearLayout.LayoutParams g2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        g2.leftMargin = dp(8);
        chips.addView(chipHot, g2);
        c.addView(chips);

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgress(0);
        bar.setVisibility(View.GONE);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(6));
        bl.topMargin = dp(16);
        c.addView(bar, bl);

        return withMargin(c);
    }

    private TextView chip(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(11);
        t.setTextColor(C_ON_CONTAINER);
        t.setPadding(dp(10), dp(5), dp(10), dp(5));
        GradientDrawable d = new GradientDrawable();
        d.setColor(alpha(Color.WHITE, 0.6f));
        d.setCornerRadius(dp(20));
        t.setBackground(d);
        return t;
    }

    private View versionCard() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(bg(C_SURFACE_VAR, 24));
        c.setPadding(dp(20), dp(6), dp(20), dp(6));

        c.addView(versionRow("内核模块", true));
        c.addView(divider());
        c.addView(versionRow("内核", false));
        return withMargin(c);
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(alpha(C_ON_VAR, 0.15f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        v.setLayoutParams(lp);
        return v;
    }

    private View versionRow(String label, final boolean isKo) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(14), 0, dp(14));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        TextView t1 = new TextView(this);
        t1.setText(label);
        t1.setTextSize(14);
        t1.setTextColor(C_ON_SURFACE);
        TextView t2 = new TextView(this);
        t2.setTextSize(11);
        t2.setTextColor(C_ON_VAR);
        left.addView(t1);
        left.addView(t2);
        row.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView badge = new TextView(this);
        badge.setTextSize(11);
        badge.setPadding(dp(8), dp(4), dp(8), dp(4));
        badge.setVisibility(View.GONE);
        row.addView(badge);

        if (isKo) { koLocal = t2; koBadge = badge; } else { bootLocal = t2; bootBadge = badge; }
        return row;
    }

    private View filledCard(int icon, final String title, final String desc, final Runnable action) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setBackground(bg(C_PRIMARY, 24));
        c.setPadding(dp(18), dp(18), dp(18), dp(18));
        c.setClickable(true);
        if (Build.VERSION.SDK_INT >= 21) {
            c.setBackground(new RippleDrawable(ColorStateList.valueOf(alpha(Color.WHITE, 0.2f)),
                    bg(C_PRIMARY, 24), null));
        }
        IconView iv = new IconView(this, icon, Color.WHITE);
        c.addView(iv, new LinearLayout.LayoutParams(dp(28), dp(28)));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(14), 0, 0, 0);
        TextView t = new TextView(this);
        t.setText(title); t.setTextSize(16); t.setTypeface(null, Typeface.BOLD); t.setTextColor(Color.WHITE);
        TextView d = new TextView(this);
        d.setText(desc); d.setTextSize(12); d.setTextColor(alpha(Color.WHITE, 0.82f));
        d.setPadding(0, dp(3), 0, 0);
        col.addView(t); col.addView(d);
        c.addView(col);
        c.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { new Thread(action).start(); } });
        return withMargin(c);
    }

    private View tonalCard(int icon, final String title, final String desc, final Runnable action) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setBackground(bg(C_SURFACE_VAR, 24));
        c.setPadding(dp(18), dp(18), dp(18), dp(18));
        c.setClickable(true);
        IconView iv = new IconView(this, icon, C_PRIMARY);
        c.addView(iv, new LinearLayout.LayoutParams(dp(28), dp(28)));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(14), 0, 0, 0);
        TextView t = new TextView(this);
        t.setText(title); t.setTextSize(16); t.setTypeface(null, Typeface.BOLD); t.setTextColor(C_ON_SURFACE);
        TextView d = new TextView(this);
        d.setText(desc); d.setTextSize(12); d.setTextColor(C_ON_VAR);
        d.setPadding(0, dp(3), 0, 0);
        col.addView(t); col.addView(d);
        c.addView(col);
        c.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { new Thread(action).start(); } });
        return withMargin(c);
    }

    private View withMargin(View v) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        v.setLayoutParams(lp);
        return v;
    }

    private TextView textButton(final String label) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(14);
        t.setTypeface(null, Typeface.BOLD);
        t.setTextColor(C_PRIMARY);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(14), 0, dp(14));
        if (Build.VERSION.SDK_INT >= 21) {
            t.setBackground(new RippleDrawable(ColorStateList.valueOf(alpha(C_PRIMARY, 0.12f)), null, null));
        }
        t.setClickable(true);
        return t;
    }

    // ---------- 弹窗 ----------
    private void confirm(final String title, final String msg, final String ok, final String cancel,
                         final Runnable onOk) {
        ui.post(new Runnable() {
            public void run() {
                AlertDialog d = new AlertDialog.Builder(MainActivity.this)
                        .setTitle(title).setMessage(msg)
                        .setPositiveButton(ok, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface x, int w) {
                                new Thread(onOk).start(); } })
                        .setNegativeButton(cancel, null).create();
                d.setCanceledOnTouchOutside(true);
                d.show();
            }
        });
    }

    private void toast(final String msg) {
        ui.post(new Runnable() { public void run() { heroSub.setText(msg); } });
    }

    private void updateProgress(final int pct) {
        ui.post(new Runnable() { public void run() {
            if (pct < 0) { bar.setVisibility(View.GONE); }
            else { bar.setVisibility(View.VISIBLE); bar.updateProgress(pct); }
        } });
    }

    // ---------- 状态 ----------
    private void refresh() {
        String root = execRoot("id");
        final boolean rootOk = root != null && root.contains("uid=0");
        final String kv = System.getProperty("os.version");
        boolean mod = false, hot = false;
        String localKo = "内置（随内核）";
        if (rootOk) {
            String mc = execRoot("grep -c kernelsu /proc/modules");
            mod = mc != null && mc.trim().startsWith("1");
            String sz = execRoot("stat -c %s " + META + " 2>/dev/null");
            hot = sz != null && sz.trim().length() > 0 && !sz.trim().equals("0");
            String v = execRoot("cat " + META_VER + " 2>/dev/null");
            if (v != null && v.trim().length() > 0) localKo = v.trim();
        }
        final boolean fMod = mod, fHot = hot;
        final String fLocalKo = localKo;
        ui.post(new Runnable() {
            public void run() {
                heroTitle.setText(rootOk ? (fMod ? "运行正常" : "模块未加载") : "需要 root 授权");
                StringBuilder sb = new StringBuilder();
                sb.append("内核 ").append(kv);
                if (!rootOk) sb.append("\n点操作按钮会弹出授权请求");
                heroSub.setText(sb.toString());
                chipRoot.setText(rootOk ? "root ✓" : "root ✗");
                chipMod.setText(fMod ? "模块 ✓" : "模块 ✗");
                chipHot.setText(fHot ? "热更新 ✓" : "热更新 —");
                koLocal.setText("本地 " + fLocalKo);
                bootLocal.setText("本地 " + kv);
            }
        });
    }

    private void loadManifest(final boolean showDialog) {
        JSONObject m = null;
        try {
            String txt = httpGet(R2_BASE + MANIFEST);
            if (txt != null && txt.length() > 10) m = new JSONObject(txt);
        } catch (Exception ignore) { }
        if (m == null) {
            ui.post(new Runnable() { public void run() {
                koBadge.setVisibility(View.GONE);
                bootBadge.setVisibility(View.GONE);
            } });
            return;
        }
        try {
            JSONObject ko = m.optJSONObject("ko");
            JSONObject boot = m.optJSONObject("boot");
            if (ko != null) {
                cloudKoVer = ko.optString("version", "?");
                cloudKoEpoch = ko.optLong("build_epoch", 0);
                cloudKoSha = ko.optString("sha256", "");
            }
            if (boot != null) {
                cloudBootVer = boot.optString("version", "?");
                cloudBootEpoch = boot.optLong("build_epoch", 0);
                cloudBootSha = boot.optString("sha256", "");
            }
        } catch (Exception ignore) { }

        final long localBootEpoch = parseKernelDate(exec("uname -v"));
        final String localKoVer = readLocalKoVersion();

        ui.post(new Runnable() {
            public void run() {
                boolean koNewer = cloudKoEpoch > 0 && (localKoEpoch(localKoVer) <= 0 || cloudKoEpoch > localKoEpoch(localKoVer));
                boolean bootNewer = cloudBootEpoch > localBootEpoch && localBootEpoch > 0;
                koLocal.setText("本地 " + (localKoVer == null ? "内置（随内核）" : localKoVer)
                        + "   ·   云端 " + (cloudKoVer == null ? "未知" : cloudKoVer));
                bootLocal.setText("本地 " + System.getProperty("os.version")
                        + "\n云端 " + (cloudBootVer == null ? "未知" : cloudBootVer));
                if (koNewer) showBadge(koBadge, "模块有新版", C_WARN); else hideBadge(koBadge);
                if (bootNewer) showBadge(bootBadge, "内核有新版", C_WARN); else hideBadge(bootBadge);
                if (showDialog && (koNewer || bootNewer)) {
                    String msg = (koNewer ? "内核模块：" + (localKoVer == null ? "内置" : localKoVer) + " → " + cloudKoVer : "")
                            + (koNewer && bootNewer ? "\n" : "")
                            + (bootNewer ? "内核：云端 " + cloudBootVer + " 已发布" : "");
                    AlertDialog d = new AlertDialog.Builder(MainActivity.this)
                            .setTitle("有新版本可用")
                            .setMessage(msg)
                            .setPositiveButton("安装并重启", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface x, int w) {
                                    new Thread(new Runnable() { public void run() {
                                        downloadKo();
                                    }}).start();
                                }
                            })
                            .setNegativeButton("以后再说", null)
                            .create();
                    d.setCanceledOnTouchOutside(true);
                    d.show();
                }
            }
        });
    }

    private void showBadge(TextView v, String text, int color) {
        v.setText(text);
        v.setTextColor(Color.WHITE);
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(12));
        v.setBackground(d);
        v.setVisibility(View.VISIBLE);
    }

    private void hideBadge(TextView v) { v.setVisibility(View.GONE); }

    private long localKoEpoch(String ver) {
        if (ver == null) return 0;
        try { return Long.parseLong(ver.trim()); } catch (Exception e) { return 0; }
    }

    private String readLocalKoVersion() {
        String v = execRoot("cat " + META_VER + " 2>/dev/null");
        if (v == null || v.trim().length() == 0) return null;
        return v.trim();
    }

    private long parseKernelDate(String unameV) {
        if (unameV == null) return 0;
        try {
            int idx = unameV.indexOf("SMP");
            if (idx < 0) return 0;
            String rest = unameV.substring(idx);
            String datePart = rest.replaceAll("^SMP[^0-9]*", "");
            SimpleDateFormat f = new SimpleDateFormat("EEE MMM d HH:mm:ss zzz yyyy", Locale.US);
            Date d = f.parse(datePart.trim());
            return d == null ? 0 : d.getTime() / 1000;
        } catch (Exception e) { return 0; }
    }

    // ---------- 网络 ----------
    private String httpGet(String url) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(6000); c.setReadTimeout(15000);
            c.setInstanceFollowRedirects(true);
            if (c.getResponseCode() != 200) return null;
            InputStream in = c.getInputStream();
            byte[] buf = new byte[8192];
            StringBuilder sb = new StringBuilder();
            int n;
            while ((n = in.read(buf)) > 0) sb.append(new String(buf, 0, n, "UTF-8"));
            in.close();
            return sb.toString();
        } catch (Exception e) { return null; }
    }

    private File download(String url, String name, long expectSize) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000); c.setReadTimeout(180000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "ColorArchive-Updater");
        if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
        long total = c.getContentLength();
        File out = new File(getCacheDir(), name);
        InputStream in = c.getInputStream();
        FileOutputStream fo = new FileOutputStream(out);
        byte[] buf = new byte[262144];
        int n; long done = 0; int lastPct = -1;
        while ((n = in.read(buf)) > 0) {
            fo.write(buf, 0, n); done += n;
            if (total > 0) {
                int pct = (int) (done * 100 / total);
                if (pct != lastPct && pct % 2 == 0) { lastPct = pct; updateProgress(pct); }
            }
        }
        fo.close(); in.close();
        updateProgress(-1);
        if (expectSize > 0 && done != expectSize) throw new Exception("size mismatch");
        return out;
    }

    private File downloadWithMirrors(String asset, String localName, long expectSize) {
        for (String u : candidateUrls(asset)) {
            try {
                toast("下载中… " + u.substring(0, Math.min(48, u.length())));
                File f = download(u, localName, expectSize);
                toast("下载完成 " + (f.length() / 1024) + " KB");
                return f;
            } catch (Exception ignore) { }
        }
        updateProgress(-1);
        toast("所有源都失败");
        return null;
    }

    private String[] candidateUrls(String asset) {
        String direct = "https://github.com/" + REPO + "/releases/download/" + TAG + "/" + asset;
        return new String[]{
                R2_BASE + asset,
                direct,
                "https://ghproxy.net/" + direct,
                "https://gh-proxy.com/" + direct,
        };
    }

    private String execRoot(String cmd) { return exec(new String[]{"su", "-c", cmd}); }
    private String exec(String cmd) { return exec(new String[]{"sh", "-c", cmd}); }

    private String exec(String[] argv) {
        try {
            Process p = Runtime.getRuntime().exec(argv);
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            p.waitFor();
            return sb.toString().trim();
        } catch (Exception e) { return null; }
    }

    // ---------- 功能 ----------
    private void downloadKo() {
        File ko = downloadWithMirrors(KO_NAME, "kernelsu.ko", 0);
        if (ko == null) return;
        String ver = cloudKoVer == null ? "" : cloudKoVer;
        String res = execRoot("mkdir -p /metadata/ksu && cp -f '" + ko.getAbsolutePath() + "' '" + META
                + "' && chmod 644 '" + META + "' && echo '" + ver + "' > " + META_VER
                + " && sync && stat -c %s '" + META + "'");
        if (res == null || res.trim().length() == 0) { toast("写入失败（root 未授权）"); return; }
        refresh();
        confirm("模块已更新", "已写入 " + res.trim() + " 字节" + (ver.isEmpty() ? "" : "（v" + ver + "）")
                + "\n重启后生效", "立即重启", "稍后",
                new Runnable() { public void run() { execRoot("reboot"); } });
    }

    private void flashBoot() {
        toast("准备下载 boot（约 192 MB）…");
        File img = downloadWithMirrors(BOOT_NAME, "boot.img", 0);
        if (img == null) return;
        if (img.length() < 64L * 1024 * 1024) { toast("镜像太小，已中止"); return; }
        String magic = execRoot("head -c 8 '" + img.getAbsolutePath() + "'");
        if (magic == null || !magic.startsWith("ANDROID!")) { toast("镜像头无效，已中止"); return; }
        String slot = execRoot("getprop ro.boot.slot_suffix");
        slot = slot == null ? "_a" : slot.trim();
        final String part = "/dev/block/by-name/boot" + slot;
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        final String backup = "/sdcard/Download/boot_backup_" + stamp + ".img";
        toast("校验通过，备份当前 boot…");
        String bk = execRoot("dd if=" + part + " of=" + backup + " bs=4M 2>&1 | tail -1; ls -l " + backup);
        if (bk == null || !bk.contains("boot_backup")) { toast("备份失败，已中止"); return; }
        toast("写入 " + part + " …");
        execRoot("dd if='" + img.getAbsolutePath() + "' of=" + part + " bs=4M conv=fsync 2>&1 | tail -2");
        String chk = execRoot("dd if=" + part + " bs=1 count=8 2>/dev/null");
        if (chk == null || !chk.startsWith("ANDROID!")) {
            toast("写入校验失败！请用备份恢复：" + backup);
            return;
        }
        confirm("刷写完成", "已写入 " + part + "\n备份：" + backup + "\n\n立即重启？", "立即重启", "稍后",
                new Runnable() { public void run() { execRoot("reboot"); } });
    }

    // ---------- 图标 ----------
    static class IconView extends View {
        static final int ICON_DOWNLOAD = 0;
        static final int ICON_BOLT = 1;
        private final int type;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        IconView(android.content.Context c, int type, int color) {
            super(c);
            this.type = type;
            p.setColor(color);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2.2f * getResources().getDisplayMetrics().density);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight();
            float cx = w / 2f, cy = h / 2f;
            Path path = new Path();
            if (type == ICON_DOWNLOAD) {
                cv.drawLine(cx, h * 0.18f, cx, h * 0.66f, p);
                path.moveTo(cx - w * 0.16f, h * 0.5f);
                path.lineTo(cx, h * 0.68f);
                path.lineTo(cx + w * 0.16f, h * 0.5f);
                cv.drawPath(path, p);
                cv.drawLine(w * 0.22f, h * 0.82f, w * 0.78f, h * 0.82f, p);
            } else {
                path.moveTo(cx + w * 0.12f, h * 0.12f);
                path.lineTo(cx - w * 0.22f, h * 0.54f);
                path.lineTo(cx + w * 0.02f, h * 0.54f);
                path.lineTo(cx - w * 0.12f, h * 0.88f);
                path.lineTo(cx + w * 0.24f, h * 0.42f);
                path.lineTo(cx - w * 0.02f, h * 0.42f);
                path.close();
                p.setStyle(Paint.Style.FILL);
                cv.drawPath(path, p);
            }
        }
    }
}