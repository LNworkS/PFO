package com.smartaccounting.mobile;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 应用内更新插件：下载安装包并调起系统安装器。
 * 只从自建更新源下载，不上传任何数据。
 */
@CapacitorPlugin(name = "ApkUpdater")
public class ApkUpdaterPlugin extends Plugin {

    /** 查询是否已获得「安装未知应用」权限 */
    @PluginMethod
    public void canInstall(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("allowed", hasInstallPermission());
        call.resolve(ret);
    }

    /** 跳转到系统的「安装未知应用」授权页 */
    @PluginMethod
    public void openInstallSettings(PluginCall call) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                intent.setData(Uri.parse("package:" + getContext().getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
            }
            call.resolve();
        } catch (Exception e) {
            call.reject("无法打开授权页面：" + e.getMessage());
        }
    }

    /** 下载安装包并调起安装器 */
    @PluginMethod
    public void downloadAndInstall(final PluginCall call) {
        final String url = call.getString("url");
        final String fileName = call.getString("fileName", "update.apk");
        if (url == null || url.isEmpty()) {
            call.reject("缺少下载地址");
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File apk = download(url, fileName, call);
                    if (apk == null) {
                        return;
                    }
                    install(apk);
                    JSObject ret = new JSObject();
                    ret.put("ok", true);
                    call.resolve(ret);
                } catch (Exception e) {
                    call.reject("更新失败：" + e.getMessage());
                }
            }
        }).start();
    }

    private boolean hasInstallPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return getContext().getPackageManager().canRequestPackageInstalls();
        }
        return true;
    }

    private File download(String url, String fileName, PluginCall call) throws Exception {
        File dir = new File(getContext().getCacheDir(), "updates");
        if (!dir.exists() && !dir.mkdirs()) {
            call.reject("无法创建下载目录");
            return null;
        }
        File apk = new File(dir, fileName);
        if (apk.exists() && !apk.delete()) {
            call.reject("无法清理旧的安装包");
            return null;
        }

        String current = url;
        HttpURLConnection conn = null;
        for (int hop = 0; hop < 5; hop++) {
            conn = (HttpURLConnection) new URL(current).openConnection();
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(60000);
            conn.setInstanceFollowRedirects(false);
            conn.connect();
            int code = conn.getResponseCode();
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                String location = conn.getHeaderField("Location");
                conn.disconnect();
                if (location == null) {
                    call.reject("下载跳转失败");
                    return null;
                }
                current = location;
                continue;
            }
            if (code != HttpURLConnection.HTTP_OK) {
                conn.disconnect();
                call.reject("下载失败，HTTP " + code);
                return null;
            }
            break;
        }
        if (conn == null) {
            call.reject("下载失败");
            return null;
        }

        int total = conn.getContentLength();
        InputStream in = conn.getInputStream();
        FileOutputStream out = new FileOutputStream(apk);
        try {
            byte[] buf = new byte[16384];
            long done = 0;
            int lastPercent = -1;
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                done += n;
                if (total > 0) {
                    int percent = (int) (done * 100 / total);
                    if (percent != lastPercent) {
                        lastPercent = percent;
                        notifyProgress(percent);
                    }
                }
            }
        } finally {
            out.close();
            in.close();
            conn.disconnect();
        }

        if (apk.length() == 0) {
            call.reject("下载内容为空");
            return null;
        }
        return apk;
    }

    private void notifyProgress(final int percent) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                JSObject data = new JSObject();
                data.put("progress", percent);
                notifyListeners("downloadProgress", data);
            }
        });
    }

    private void install(final File apk) {
        final Uri uri = FileProvider.getUriForFile(
                getContext(), getContext().getPackageName() + ".fileprovider", apk);
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(uri, "application/vnd.android.package-archive");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
            }
        });
    }
}