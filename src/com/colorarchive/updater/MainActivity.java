package com.colorarchive.updater;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {
    private static final String REPO = "one899/ColorArchive-Updater";
    private static final String TAG = "lkm-latest";
    private static final String META = "/metadata/ksu/kernelsu.ko";
    private static final String KO_NAME = "kernelsu-stripped.ko";
    private static final String BOOT_NAME = "boot-lkm-9rt.img";

    private TextView status;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (16 * getResources().getDisplayMetrics().density);
        root.setPadding(p, p, p, p);

        TextView title = new TextView(this);
        title.setText("ColorArchive LKM 更新器");
        title.setTextSize(20);
        root.addView(title);

        status = new TextView(this);
        status.setTextSize(13);
        status.setPadding(0, p, 0, p);
        root.addView(status);

        root.addView(btn("① 更新模块 (.ko 热更新)", new Runnable() {
            public void run() { downloadKo(); }
        }));
        root.addView(btn("② 下载最新 boot 镜像", new Runnable() {
            public void run() { downloadBoot(); }
        }));
        root.addView(btn("③ 重启设备", new Runnable() {
            public void run() { execRoot("reboot"); }
        }));
        root.addView(btn("刷新状态", new Runnable() {
            public void run() { refresh(); }
        }));

        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);
        refresh();
    }

    private Button btn(String text, final Runnable action) {
        Button b = new Button(this);
        b.setText(text);
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { new Thread(action).start(); }
        });
        return b;
    }

    private void toast(final String msg) {
        ui.post(new Runnable() { public void run() { status.setText(msg); } });
    }

    private void refresh() {
        new Thread(new Runnable() {
            public void run() {
                StringBuilder sb = new StringBuilder();
                sb.append("内核: ").append(System.getProperty("os.version")).append("\n");
                String root = execRoot("id");
                boolean rootOk = root != null && root.contains("uid=0");
                sb.append("root: ").append(rootOk ? "已授权" : "未授权（点按钮会弹出授权）").append("\n");
                if (rootOk) {
                    String mc = execRoot("grep -c kernelsu /proc/modules");
                    sb.append("模块: ").append(mc != null && mc.trim().startsWith("1") ? "已加载" : "未加载").append("\n");
                    String sz = execRoot("stat -c %s " + META + " 2>/dev/null");
                    if (sz != null && sz.trim().length() > 0 && !sz.trim().equals("0")) {
                        sb.append("热更新: 已放置 (").append(sz.trim()).append(" 字节)\n");
                    } else {
                        sb.append("热更新: 未放置（用 ramdisk 内置）\n");
                    }
                    String free = execRoot("df -h /metadata 2>/dev/null | tail -1 | awk '{print $4}'");
                    if (free != null && free.trim().length() > 0) sb.append("metadata 可用: ").append(free.trim()).append("\n");
                } else {
                    sb.append("模块: 需 root 才能检测（已对非 root 隐藏）\n");
                }
                toast(sb.toString());
            }
        }).start();
    }

    private String execRoot(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            p.waitFor();
            return sb.toString().trim();
        } catch (Exception e) { return null; }
    }

    private String exec(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            p.waitFor();
            return sb.toString().trim();
        } catch (Exception e) { return null; }
    }

    /** 国内可达的 GitHub 加速前缀，按顺序尝试 */
    private String[] candidateUrls(String asset) {
        String direct = "https://github.com/" + REPO + "/releases/download/" + TAG + "/" + asset;
        return new String[]{
                direct,
                "https://ghproxy.net/" + direct,
                "https://gh-proxy.com/" + direct,
                "https://ghfast.top/" + direct,
                "https://ghp.ci/" + direct,
                direct.replace("github.com", "kkgithub.com"),
                direct.replace("github.com", "bgithub.xyz"),
        };
    }

    private File download(String url, String name) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(60000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "ColorArchive-Updater");
        if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
        File out = new File(getCacheDir(), name);
        InputStream in = c.getInputStream();
        FileOutputStream fo = new FileOutputStream(out);
        byte[] buf = new byte[65536];
        int n;
        long total = 0;
        while ((n = in.read(buf)) > 0) { fo.write(buf, 0, n); total += n; }
        fo.close();
        in.close();
        if (total < 1024) throw new Exception("too small: " + total);
        return out;
    }

    private File downloadWithMirrors(String asset, String localName) {
        for (String u : candidateUrls(asset)) {
            try {
                toast("尝试源:\n" + u);
                File f = download(u, localName);
                toast("下载成功:\n" + u + "\n" + (f.length() / 1024) + " KB");
                return f;
            } catch (Exception e) {
                // try next
            }
        }
        toast("所有源都失败（国内网络可能需要代理）");
        return null;
    }

    private void downloadKo() {
        toast("开始下载模块...");
        File ko = downloadWithMirrors(KO_NAME, "kernelsu.ko");
        if (ko == null) return;
        String cmd = "mkdir -p /metadata/ksu && cp -f '" + ko.getAbsolutePath()
                + "' '" + META + "' && chmod 644 '" + META + "' && sync && stat -c %s '" + META + "'";
        String res = execRoot(cmd);
        if (res == null || res.trim().length() == 0 || res.contains("No such") || res.contains("denied")) {
            toast("写入失败（root 未授权或空间不足）\n" + (res == null ? "" : res));
            return;
        }
        ui.post(new Runnable() {
            public void run() {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("完成")
                        .setMessage("模块已写入 /metadata/ksu/kernelsu.ko\n重启后生效")
                        .setPositiveButton("立即重启", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int w) { execRoot("reboot"); }
                        })
                        .setNegativeButton("稍后", null)
                        .show();
            }
        });
        toast("已写入，大小 " + res.trim() + " 字节");
    }

    private void downloadBoot() {
        toast("开始下载 boot...");
        File img = downloadWithMirrors(BOOT_NAME, "boot.img");
        if (img == null) return;
        String cp = "cp -f '" + img.getAbsolutePath() + "' /sdcard/Download/" + BOOT_NAME + " && stat -c %s /sdcard/Download/" + BOOT_NAME;
        String res = exec(cp);
        toast("已保存 /sdcard/Download/" + BOOT_NAME + "（" + (res == null ? "?" : res.trim()) + " 字节）\n\n"
                + "电脑上执行:\n adb reboot bootloader\n fastboot flash boot_a " + BOOT_NAME + "\n fastboot reboot");
    }
}