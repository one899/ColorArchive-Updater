package com.colorarchive.updater;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

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
    private static final String KO_NAME = "kernelsu-stripped.ko";
    private static final String BOOT_NAME = "boot-lkm-9rt.img";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private int cPrimary, cSurface, cSurfaceVar, cOnSurface, cOnSurfaceVar;
    private int cPrimaryContainer, cOnPrimaryContainer, cOutline;

    private TextView heroTitle, heroSub, chipRoot, chipMod, chipHot, footer;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        resolveColors();
        edgeToEdge();

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(cSurface);
        sv.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int h = dp(20);
        root.setPadding(h, dp(12) + statusBarHeight(), h, dp(28));

        root.addView(header());

        TextView sec1 = sectionLabel("设备状态");
        root.addView(sec1);
        root.addView(heroCard());

        root.addView(sectionLabel("操作"));
        root.addView(filledCard("更新内核模块", "0.4 MB · 写入 /metadata，重启后生效",
                new Runnable() { public void run() { downloadKo(); } }));
        root.addView(tonalCard("刷写 boot 镜像", "192 MB · 校验并备份后直接写入 boot 分区",
                new Runnable() { public void run() { flashBoot(); } }));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(4), 0, 0);
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
                public void run() { refresh(); } }).start(); }
        });
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(reboot, lp1);
        row.addView(refresh, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(row);

        footer = new TextView(this);
        footer.setText("v2.0 · lkm.colorarchive.cloud");
        footer.setTextSize(11);
        footer.setTextColor(cOnSurfaceVar);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(28), 0, 0);
        root.addView(footer);

        sv.addView(root);
        setContentView(sv);
        refresh();
    }

    // ---------------- 主题色 ----------------
    private void resolveColors() {
        cPrimary = 0xFF6750A4;
        cSurface = 0xFFFDF8FF;
        cSurfaceVar = 0xFFF3EDF7;
        cOnSurface = 0xFF1D1B20;
        cOnSurfaceVar = 0xFF79747E;
        cPrimaryContainer = 0xFFEADDFF;
        cOnPrimaryContainer = 0xFF21005D;
        cOutline = 0xFFCAC4D0;
        if (Build.VERSION.SDK_INT >= 31) {
            cPrimary = sys(android.R.color.system_accent1_500, cPrimary);
            cSurface = sys(android.R.color.system_neutral1_50, cSurface);
            cSurfaceVar = sys(android.R.color.system_neutral2_50, cSurfaceVar);
            cOnSurface = sys(android.R.color.system_neutral1_900, cOnSurface);
            cOnSurfaceVar = sys(android.R.color.system_neutral1_600, cOnSurfaceVar);
            cPrimaryContainer = sys(android.R.color.system_accent1_100, cPrimaryContainer);
            cOnPrimaryContainer = sys(android.R.color.system_accent1_900, cOnPrimaryContainer);
            cOutline = sys(android.R.color.system_neutral1_400, cOutline);
        }
    }

    private int sys(int id, int fb) {
        try { return getResources().getColor(id, getTheme()); } catch (Throwable t) { return fb; }
    }

    private void edgeToEdge() {
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().setNavigationBarColor(cSurface);
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

    private View ripple(View v, int color, int r) {
        GradientDrawable base = bg(color, r);
        if (Build.VERSION.SDK_INT >= 21) {
            v.setBackground(new RippleDrawable(ColorStateList.valueOf(alpha(cPrimary, 0.12f)), base, null));
        } else {
            v.setBackground(base);
        }
        return v;
    }

    private int alpha(int c, float a) {
        return Color.argb((int) (255 * a), Color.red(c), Color.green(c), Color.blue(c));
    }

    // ---------------- 组件 ----------------
    private View header() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(20));

        TextView logo = new TextView(this);
        logo.setText("CA");
        logo.setTextSize(16);
        logo.setTypeface(null, Typeface.BOLD);
        logo.setTextColor(0xFFFFFFFF);
        logo.setGravity(Gravity.CENTER);
        GradientDrawable circle = new GradientDrawable();
        circle.setColor(cPrimary);
        circle.setShape(GradientDrawable.OVAL);
        logo.setBackground(circle);
        int s = dp(44);
        row.addView(logo, new LinearLayout.LayoutParams(s, s));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(dp(14), 0, 0, 0);
        TextView t1 = new TextView(this);
        t1.setText("ColorArchive");
        t1.setTextSize(22);
        t1.setTypeface(null, Typeface.BOLD);
        t1.setTextColor(cOnSurface);
        TextView t2 = new TextView(this);
        t2.setText("LKM 更新器");
        t2.setTextSize(13);
        t2.setTextColor(cOnSurfaceVar);
        text.addView(t1);
        text.addView(t2);
        row.addView(text);
        return row;
    }

    private TextView sectionLabel(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(12);
        t.setTextColor(cOnSurfaceVar);
        t.setLetterSpacing(0.08f);
        t.setPadding(dp(4), dp(16), 0, dp(10));
        return t;
    }

    private View heroCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(bg(cPrimaryContainer, 24));
        card.setPadding(dp(20), dp(20), dp(20), dp(20));

        heroTitle = new TextView(this);
        heroTitle.setText("检测中…");
        heroTitle.setTextSize(20);
        heroTitle.setTypeface(null, Typeface.BOLD);
        heroTitle.setTextColor(cOnPrimaryContainer);
        card.addView(heroTitle);

        heroSub = new TextView(this);
        heroSub.setText("");
        heroSub.setTextSize(12);
        heroSub.setTextColor(alpha(cOnPrimaryContainer, 0.75f));
        heroSub.setPadding(0, dp(4), 0, dp(14));
        card.addView(heroSub);

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chipRoot = chip("root");
        chipMod = chip("模块");
        chipHot = chip("热更新");
        chips.addView(chipRoot);
        LinearLayout.LayoutParams g = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        g.leftMargin = dp(8);
        chips.addView(chipMod, g);
        LinearLayout.LayoutParams g2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        g2.leftMargin = dp(8);
        chips.addView(chipHot, g2);
        card.addView(chips);

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT) {{ bottomMargin = dp(4); }});
        wrap.addView(card);
        return wrap;
    }

    private TextView chip(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(11);
        t.setTextColor(cOnPrimaryContainer);
        t.setPadding(dp(10), dp(5), dp(10), dp(5));
        GradientDrawable d = new GradientDrawable();
        d.setColor(alpha(0xFFFFFFFF, 0.55f));
        d.setCornerRadius(dp(20));
        t.setBackground(d);
        return t;
    }

    private View filledCard(final String title, final String desc, final Runnable action) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        ripple(card, cPrimary, 24);
        card.setPadding(dp(20), dp(18), dp(20), dp(18));
        card.setClickable(true);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(16);
        t.setTypeface(null, Typeface.BOLD);
        t.setTextColor(0xFFFFFFFF);
        card.addView(t);

        TextView d = new TextView(this);
        d.setText(desc);
        d.setTextSize(12);
        d.setTextColor(alpha(0xFFFFFFFF, 0.8f));
        d.setPadding(0, dp(4), 0, 0);
        card.addView(d);

        card.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { new Thread(action).start(); } });
        return withMargin(card);
    }

    private View tonalCard(final String title, final String desc, final Runnable action) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        ripple(card, cSurfaceVar, 24);
        card.setPadding(dp(20), dp(18), dp(20), dp(18));
        card.setClickable(true);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(16);
        t.setTypeface(null, Typeface.BOLD);
        t.setTextColor(cOnSurface);
        card.addView(t);

        TextView d = new TextView(this);
        d.setText(desc);
        d.setTextSize(12);
        d.setTextColor(cOnSurfaceVar);
        d.setPadding(0, dp(4), 0, 0);
        card.addView(d);

        card.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { new Thread(action).start(); } });
        return withMargin(card);
    }

    private View withMargin(View v) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        v.setLayoutParams(lp);
        return v;
    }

    private TextView textButton(final String label) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(14);
        t.setTypeface(null, Typeface.BOLD);
        t.setTextColor(cPrimary);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(14), 0, dp(14));
        if (Build.VERSION.SDK_INT >= 21) {
            t.setBackground(new RippleDrawable(ColorStateList.valueOf(alpha(cPrimary, 0.12f)), null, null));
        }
        t.setClickable(true);
        return t;
    }

    private void confirm(String title, String msg, String ok, String cancel, final Runnable onOk) {
        ui.post(new Runnable() {
            public void run() {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(title).setMessage(msg)
                        .setPositiveButton(ok, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int w) {
                                new Thread(onOk).start(); } })
                        .setNegativeButton(cancel, null).show();
            }
        });
    }

    private void toast(final String msg) {
        ui.post(new Runnable() { public void run() {
            heroSub.setText(msg);
        } });
    }

    private void refresh() {
        String root = execRoot("id");
        final boolean rootOk = root != null && root.contains("uid=0");
        final String kv = System.getProperty("os.version");
        final StringBuilder sub = new StringBuilder();
        boolean mod = false, hot = false;
        if (rootOk) {
            String mc = execRoot("grep -c kernelsu /proc/modules");
            mod = mc != null && mc.trim().startsWith("1");
            String sz = execRoot("stat -c %s " + META + " 2>/dev/null");
            hot = sz != null && sz.trim().length() > 0 && !sz.trim().equals("0");
            if (hot) sub.append("热更新模块 ").append(Long.parseLong(sz.trim()) / 1024).append(" KB");
            String free = execRoot("df -h /metadata 2>/dev/null | tail -1 | awk '{print $4}'");
            if (free != null && free.trim().length() > 0) sub.append(" · metadata 可用 ").append(free.trim());
            String slot = execRoot("getprop ro.boot.slot_suffix");
            if (slot != null && slot.trim().length() > 0) sub.append(" · 槽位 ").append(slot.trim());
        }
        final boolean fMod = mod, fHot = hot;
        ui.post(new Runnable() {
            public void run() {
                heroTitle.setText(rootOk ? (fMod ? "运行正常" : "模块未加载") : "需要 root 授权");
                StringBuilder sb = new StringBuilder();
                sb.append("内核 ").append(kv);
                if (!rootOk) sb.append("\n点任意操作会弹出授权请求");
                String detail = sub.toString();
                if (detail.length() > 0) sb.append("\n").append(detail);
                heroSub.setText(sb.toString());
                chipRoot.setText(rootOk ? "root ✓" : "root ✗");
                chipMod.setText(fMod ? "模块 ✓" : "模块 ✗");
                chipHot.setText(fHot ? "热更新 ✓" : "热更新 —");
            }
        });
    }

    // ---------------- 功能 ----------------
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

    private String[] candidateUrls(String asset) {
        String direct = "https://github.com/" + REPO + "/releases/download/" + TAG + "/" + asset;
        return new String[]{
                R2_BASE + asset, direct,
                "https://ghproxy.net/" + direct, "https://gh-proxy.com/" + direct,
                "https://ghfast.top/" + direct, direct.replace("github.com", "kkgithub.com"),
        };
    }

    private File download(String url, String name) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000); c.setReadTimeout(120000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "ColorArchive-Updater");
        if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
        File out = new File(getCacheDir(), name);
        InputStream in = c.getInputStream();
        FileOutputStream fo = new FileOutputStream(out);
        byte[] buf = new byte[65536];
        int n; long total = 0;
        while ((n = in.read(buf)) > 0) { fo.write(buf, 0, n); total += n; }
        fo.close(); in.close();
        if (total < 1024) throw new Exception("too small");
        return out;
    }

    private File downloadWithMirrors(String asset, String localName) {
        for (String u : candidateUrls(asset)) {
            try {
                toast("下载中… " + u);
                File f = download(u, localName);
                toast("下载完成 " + (f.length() / 1024) + " KB");
                return f;
            } catch (Exception ignore) { }
        }
        toast("所有源都失败");
        return null;
    }

    private void downloadKo() {
        File ko = downloadWithMirrors(KO_NAME, "kernelsu.ko");
        if (ko == null) return;
        String res = execRoot("mkdir -p /metadata/ksu && cp -f '" + ko.getAbsolutePath()
                + "' '" + META + "' && chmod 644 '" + META + "' && sync && stat -c %s '" + META + "'");
        if (res == null || res.trim().length() == 0) { toast("写入失败（root 未授权）"); return; }
        refresh();
        confirm("模块已更新", "已写入 " + res.trim() + " 字节\n重启后生效", "立即重启", "稍后",
                new Runnable() { public void run() { execRoot("reboot"); } });
    }

    private void flashBoot() {
        toast("准备下载 boot（约 192 MB）…");
        File img = downloadWithMirrors(BOOT_NAME, "boot.img");
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
}