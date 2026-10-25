package com.colorarchive.updater;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.BufferedReader;
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

    private TextView status;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private int cPrimary, cSurface, cSurfaceVar, cOnSurface, cOnSurfaceVar, cPrimaryContainer, cOnPrimaryContainer;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        resolveColors();

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(cSurface);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(20);
        root.setPadding(p, dp(28), p, dp(28));

        TextView title = new TextView(this);
        title.setText("ColorArchive");
        title.setTextSize(30);
        title.setTextColor(cOnSurface);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("LKM 更新器");
        sub.setTextSize(14);
        sub.setTextColor(cOnSurfaceVar);
        sub.setPadding(0, dp(2), 0, dp(20));
        root.addView(sub);

        status = new TextView(this);
        status.setTextSize(13);
        status.setTextColor(cOnPrimaryContainer);
        status.setLineSpacing(dp(3), 1f);
        LinearLayout statusCard = card(status, cPrimaryContainer, dp(20), dp(16));
        root.addView(statusCard);

        root.addView(actionCard("① 更新内核模块", "下载最新 .ko 写入 /metadata，重启生效",
                new Runnable() { public void run() { downloadKo(); } }));
        root.addView(actionCard("② 刷写 boot 镜像", "下载 → 校验 → 备份 → 直接写入 boot 分区",
                new Runnable() { public void run() { flashBoot(); } }));
        root.addView(actionCard("③ 重启设备", "应用新模块需要重启", new Runnable() {
            public void run() { confirm("确认重启？", "设备将立即重启", "重启", "取消", new Runnable() {
                public void run() { execRoot("reboot"); } }); }
        }));
        root.addView(actionCard("刷新状态", "重新检测 root / 模块 / 存储", new Runnable() {
            public void run() { refresh(); } }));

        sv.addView(root);
        setContentView(sv);
        refresh();
    }

    // ---------- Material 3 配色（Monet 动态取色，API31+）----------
    private void resolveColors() {
        cPrimary = 0xFF6750A4;
        cSurface = 0xFFFDF8FF;
        cSurfaceVar = 0xFFE7E0EC;
        cOnSurface = 0xFF1C1B1F;
        cOnSurfaceVar = 0xFF49454F;
        cPrimaryContainer = 0xFFEADDFF;
        cOnPrimaryContainer = 0xFF21005D;
        if (Build.VERSION.SDK_INT >= 31) {
            cPrimary = sysColor(android.R.color.system_accent1_500, cPrimary);
            cSurface = sysColor(android.R.color.system_neutral1_50, cSurface);
            cSurfaceVar = sysColor(android.R.color.system_neutral2_100, cSurfaceVar);
            cOnSurface = sysColor(android.R.color.system_neutral1_900, cOnSurface);
            cOnSurfaceVar = sysColor(android.R.color.system_neutral1_700, cOnSurfaceVar);
            cPrimaryContainer = sysColor(android.R.color.system_accent1_100, cPrimaryContainer);
            cOnPrimaryContainer = sysColor(android.R.color.system_accent1_900, cOnPrimaryContainer);
        }
    }

    private int sysColor(int resId, int fallback) {
        try {
            return getResources().getColor(resId, getTheme());
        } catch (Throwable t) {
            return fallback;
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private LinearLayout card(View content, int bgColor, int radius, int pad) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(rounded(bgColor, radius));
        box.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        box.setLayoutParams(lp);
        box.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return box;
    }

    private View actionCard(final String title, final String desc, final Runnable action) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(17);
        t.setTextColor(cOnSurface);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        box.addView(t);

        TextView d = new TextView(this);
        d.setText(desc);
        d.setTextSize(13);
        d.setTextColor(cOnSurfaceVar);
        d.setPadding(0, dp(4), 0, 0);
        box.addView(d);

        GradientDrawable bg = rounded(cSurfaceVar, 20);
        if (Build.VERSION.SDK_INT >= 21) {
            RippleDrawable rip = new RippleDrawable(
                    ColorStateList.valueOf(alpha(cPrimary, 0.25f)), bg, null);
            box.setBackground(rip);
        } else {
            box.setBackground(bg);
        }
        int pad = dp(16);
        box.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        box.setLayoutParams(lp);
        box.setClickable(true);
        box.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { new Thread(action).start(); }
        });
        return box;
    }

    private int alpha(int color, float a) {
        return Color.argb((int) (255 * a), Color.red(color), Color.green(color), Color.blue(color));
    }

    private void toast(final String msg) {
        ui.post(new Runnable() { public void run() { status.setText(msg); } });
    }

    private void confirm(String title, String msg, String ok, String cancel, final Runnable onOk) {
        ui.post(new Runnable() {
            public void run() {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(title).setMessage(msg)
                        .setPositiveButton(ok, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int w) { new Thread(onOk).start(); }
                        })
                        .setNegativeButton(cancel, null).show();
            }
        });
    }

    private void refresh() {
        new Thread(new Runnable() {
            public void run() {
                StringBuilder sb = new StringBuilder();
                sb.append("内核  ").append(System.getProperty("os.version")).append("\n");
                String root = execRoot("id");
                boolean rootOk = root != null && root.contains("uid=0");
                sb.append("root  ").append(rootOk ? "已授权" : "未授权（点按钮会弹授权）").append("\n");
                if (rootOk) {
                    String mc = execRoot("grep -c kernelsu /proc/modules");
                    sb.append("模块  ").append(mc != null && mc.trim().startsWith("1") ? "已加载" : "未加载").append("\n");
                    String sz = execRoot("stat -c %s " + META + " 2>/dev/null");
                    if (sz != null && sz.trim().length() > 0 && !sz.trim().equals("0")) {
                        sb.append("热更新 已放置 ").append(sz.trim()).append(" 字节\n");
                    } else {
                        sb.append("热更新 未放置（用 ramdisk 内置）\n");
                    }
                    String slot = execRoot("getprop ro.boot.slot_suffix");
                    sb.append("槽位  ").append(slot == null ? "?" : slot.trim()).append("\n");
                    String free = execRoot("df -h /metadata 2>/dev/null | tail -1 | awk '{print $4}'");
                    if (free != null && free.trim().length() > 0) sb.append("/metadata 可用 ").append(free.trim());
                }
                toast(sb.toString());
            }
        }).start();
    }

    private String execRoot(String cmd) {
        return exec(new String[]{"su", "-c", cmd});
    }

    private String exec(String cmd) {
        return exec(new String[]{"sh", "-c", cmd});
    }

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
                R2_BASE + asset,
                direct,
                "https://ghproxy.net/" + direct,
                "https://gh-proxy.com/" + direct,
                "https://ghfast.top/" + direct,
                direct.replace("github.com", "kkgithub.com"),
        };
    }

    private File download(String url, String name) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(120000);
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
                toast("下载中…\n" + u);
                File f = download(u, localName);
                toast("下载成功（" + (f.length() / 1024) + " KB）\n" + u);
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
        confirm("模块已更新", "已写入 " + res.trim() + " 字节\n重启后生效", "立即重启", "稍后",
                new Runnable() { public void run() { execRoot("reboot"); } });
    }

    /** ② 下载 → 校验 → 备份 → 直接 dd 刷入当前槽位 boot 分区 */
    private void flashBoot() {
        toast("准备下载 boot（约 192 MB）…");
        File img = downloadWithMirrors(BOOT_NAME, "boot.img");
        if (img == null) return;
        if (img.length() < 64L * 1024 * 1024) { toast("镜像太小，拒绝刷写：" + img.length()); return; }
        String magic = execRoot("head -c 8 '" + img.getAbsolutePath() + "'");
        if (magic == null || !magic.startsWith("ANDROID!")) {
            toast("镜像头不是 ANDROID!，拒绝刷写\n" + (magic == null ? "" : magic));
            return;
        }
        String slot = execRoot("getprop ro.boot.slot_suffix");
        slot = slot == null ? "_a" : slot.trim();
        final String part = "/dev/block/by-name/boot" + slot;
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        final String backup = "/sdcard/Download/boot_backup_" + stamp + ".img";
        toast("校验通过，正在备份当前 boot…");
        String bk = execRoot("dd if=" + part + " of=" + backup + " bs=4M 2>&1 | tail -1; ls -l " + backup);
        if (bk == null || !bk.contains("boot_backup")) { toast("备份失败，已中止\n" + (bk == null ? "" : bk)); return; }
        toast("备份完成\n" + backup + "\n开始写入 " + part + "…");
        String wr = execRoot("dd if='" + img.getAbsolutePath() + "' of=" + part + " bs=4M conv=fsync 2>&1 | tail -2");
        String chk = execRoot("dd if=" + part + " bs=1 count=8 2>/dev/null");
        if (chk == null || !chk.startsWith("ANDROID!")) {
            toast("写入后校验失败！请用备份恢复：\n" + backup + "\n" + (wr == null ? "" : wr));
            return;
        }
        confirm("刷写完成", "已写入 " + part + "\n备份：" + backup + "\n\n立即重启？", "立即重启", "稍后",
                new Runnable() { public void run() { execRoot("reboot"); } });
    }
}