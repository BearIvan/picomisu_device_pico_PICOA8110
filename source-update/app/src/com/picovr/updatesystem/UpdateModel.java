// Picomisu Source Update: process-wide update state (PICO's UpdateFSM equivalent).
//
// Settings re-creates the embedded page on every stop/start, so the page only renders this model.
// Source packages come from a manifest (tools/source-ota.py publish) at the URL in
// Settings.Global source_update_url, or from /sdcard/dload; they are verified with
// RecoverySystem.verifyPackage against /system/etc/security/source_otacerts.zip and staged by
// source_updaterd (root), which reboots to the updater recovery.
package com.picovr.updatesystem;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.os.RecoverySystem;
import android.os.SystemProperties;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class UpdateModel {
    private static final String TAG = "SourceUpdate";

    public static final String KEY_URL = "source_update_url";
    public static final String KEY_RED_DOT = "pvr_update";
    public static final String KEY_AUTO_DOWNLOAD = "pvr_update_auto_upgrade";
    public static final String KEY_AUTO_UPDATE = "pvr_update_auto_update";
    static final File CERTS = new File("/system/etc/security/source_otacerts.zip");

    public enum State {
        IDLE, CHECKING, NEW_VERSION, DOWNLOAD_PREPARE, DOWNLOADING, DOWNLOAD_FAILED, VERIFYING,
        VERIFY_FAILED, READY, INSTALLING, INSTALL_FAILED
    }

    /** A package of the manifest or a local file. */
    public static final class Package {
        public String version;
        public String baseVersion;
        public String notes = "";
        public String url;
        public long bytes;
        public String sha256;
        public long createdSeconds;
        public File file;  // set for local packages and finished downloads
        public boolean local;
    }

    public interface Listener {
        void onModelChanged();
    }

    private static UpdateModel sInstance;

    private final Context mContext;
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private final List<Listener> mListeners = new ArrayList<>();

    private State mState = State.IDLE;
    private Package mPackage;
    private long mDone;
    private long mTotal;
    private long mRemainingSeconds = -1;
    private String mError = "";
    private int mErrorToast;  // R.string id of a pending toast, 0 if none
    private volatile boolean mCancel;
    private String mLastResult = "";

    public static synchronized UpdateModel get(Context context) {
        if (sInstance == null) sInstance = new UpdateModel(context.getApplicationContext());
        return sInstance;
    }

    private UpdateModel(Context context) {
        mContext = context;
    }

    // ------------------------------------------------------------------ state for the views

    public synchronized State state() { return mState; }
    public synchronized Package pkg() { return mPackage; }
    public synchronized long done() { return mDone; }
    public synchronized long total() { return mTotal; }
    public synchronized long remainingSeconds() { return mRemainingSeconds; }
    public synchronized String error() { return mError; }
    public synchronized String lastResult() { return mLastResult; }

    public synchronized int takeToast() {
        int toast = mErrorToast;
        mErrorToast = 0;
        return toast;
    }

    public void addListener(Listener l) { synchronized (mListeners) { mListeners.add(l); } }
    public void removeListener(Listener l) { synchronized (mListeners) { mListeners.remove(l); } }

    private void notifyChanged() {
        mMain.post(() -> {
            List<Listener> copy;
            synchronized (mListeners) { copy = new ArrayList<>(mListeners); }
            for (Listener l : copy) l.onModelChanged();
        });
    }

    private synchronized void set(State state) {
        mState = state;
        boolean available = state != State.IDLE && state != State.CHECKING && state != State.INSTALLING
                && mPackage != null;
        Settings.Global.putInt(mContext.getContentResolver(), KEY_RED_DOT, available ? 1 : 0);
        notifyChanged();
    }

    private synchronized void toast(int stringId) {
        mErrorToast = stringId;
    }

    // ------------------------------------------------------------------ environment

    public static String currentVersion() {
        return SystemProperties.get("ro.source.version", "");
    }

    public static String displayVersion() {
        return SystemProperties.get("ro.build.display.id", currentVersion());
    }

    public String serverUrl() {
        String url = Settings.Global.getString(mContext.getContentResolver(), KEY_URL);
        return url == null ? "" : url.trim();
    }

    public void setServerUrl(String url) {
        Settings.Global.putString(mContext.getContentResolver(), KEY_URL, url == null ? "" : url.trim());
    }

    public boolean isOnline() {
        ConnectivityManager cm = mContext.getSystemService(ConnectivityManager.class);
        NetworkInfo info = cm == null ? null : cm.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    public boolean isAcceptUpdate() {
        int ota = SystemProperties.getInt("persist.accept.systemupdates.ota", -1);
        return (ota != -1 ? ota : SystemProperties.getInt("persist.accept.systemupdates", 1)) == 1;
    }

    public int batteryLevel() {
        BatteryManager bm = mContext.getSystemService(BatteryManager.class);
        return bm == null ? 100 : bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
    }

    private File downloadDir() {
        File dir = new File(mContext.getFilesDir(), "ota");
        dir.mkdirs();
        return dir;
    }

    // ------------------------------------------------------------------ check

    /** Page open: like PICO, a check runs on every open while online and idle. */
    public void onPageOpened() {
        State state = state();
        if (state == State.IDLE || state == State.NEW_VERSION || state == State.READY) {
            refreshLastResult();
            if (!TextUtils.isEmpty(serverUrl()) && isOnline() && isAcceptUpdate()) check();
        }
    }

    public void check() {
        synchronized (this) {
            if (mState == State.CHECKING || mState == State.DOWNLOADING || mState == State.DOWNLOAD_PREPARE
                    || mState == State.VERIFYING || mState == State.INSTALLING) {
                return;
            }
        }
        set(State.CHECKING);
        new Thread(() -> {
            Package found = null;
            try {
                found = fetchManifest();
            } catch (Exception e) {
                Log.w(TAG, "check failed", e);
            }
            synchronized (this) {
                // PICO shows a failed check like "up to date".
                if (found == null) {
                    mPackage = null;
                    mState = State.IDLE;
                } else {
                    File done = new File(downloadDir(), fileName(found));
                    mPackage = found;
                    if (done.exists() && done.length() == found.bytes) {
                        found.file = done;
                        mState = State.VERIFYING;
                    } else {
                        mState = State.NEW_VERSION;
                    }
                }
            }
            set(state());
            if (state() == State.VERIFYING) verify();
        }, "source-update-check").start();
    }

    private static String fileName(Package p) {
        return "source-" + p.baseVersion + "-to-" + p.version + ".zip";
    }

    private Package fetchManifest() throws Exception {
        String url = serverUrl();
        if (TextUtils.isEmpty(url)) return null;
        if (!url.endsWith(".json")) url = url.endsWith("/") ? url + "manifest.json" : url + "/manifest.json";
        JSONObject manifest = new JSONObject(readUrl(url));
        JSONArray packages = manifest.getJSONArray("packages");
        String current = currentVersion();
        Package best = null;
        for (int i = 0; i < packages.length(); i++) {
            JSONObject row = packages.getJSONObject(i);
            if (!current.equals(row.optString("base_version"))) continue;
            Package p = new Package();
            p.version = row.getString("version");
            p.baseVersion = row.getString("base_version");
            p.notes = row.optString("notes", "");
            p.bytes = row.getLong("bytes");
            p.sha256 = row.getString("sha256");
            p.createdSeconds = parseIsoSeconds(row.optString("created_at_utc", ""));
            p.url = new URL(new URL(url), row.getString("url")).toString();
            if (best == null || compareVersions(p.version, best.version) > 0) best = p;
        }
        return best;
    }

    static int compareVersions(String a, String b) {
        String[] x = a.split("\\."), y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int u = i < x.length ? Integer.parseInt(x[i]) : 0, v = i < y.length ? Integer.parseInt(y[i]) : 0;
            if (u != v) return Integer.compare(u, v);
        }
        return 0;
    }

    private static long parseIsoSeconds(String iso) {
        try {
            return java.time.OffsetDateTime.parse(iso).toEpochSecond();
        } catch (Exception e) {
            try {
                return java.time.LocalDateTime.parse(iso).toEpochSecond(java.time.ZoneOffset.UTC);
            } catch (Exception ignored) {
                return 0;
            }
        }
    }

    private static String readUrl(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(15000);
        c.setUseCaches(false);
        try (InputStream in = c.getInputStream()) {
            return new String(readAll(in), "UTF-8");
        } finally {
            c.disconnect();
        }
    }

    // ------------------------------------------------------------------ download

    /** Action bar in NEW_VERSION / DOWNLOAD_FAILED / VERIFY_FAILED / INSTALL_FAILED. */
    public void startDownload() {
        Package p = pkg();
        if (p == null) return;
        if (p.local) {
            verify();
            return;
        }
        mCancel = false;
        synchronized (this) {
            mDone = 0;
            mTotal = p.bytes;
            mRemainingSeconds = -1;
        }
        set(State.DOWNLOAD_PREPARE);
        toast(R.string.forbid_copy);
        new Thread(() -> download(p), "source-update-download").start();
    }

    public void cancelDownload() {
        mCancel = true;
    }

    private void download(Package p) {
        File target = new File(downloadDir(), fileName(p));
        File part = new File(downloadDir(), fileName(p) + ".part");
        for (File old : downloadDir().listFiles()) {
            if (!old.equals(target) && !old.equals(part)) old.delete();
        }
        long start = part.exists() ? part.length() : 0;
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(p.url).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            if (start > 0) c.setRequestProperty("Range", "bytes=" + start + "-");
            int code = c.getResponseCode();
            if (code == 200) start = 0;
            else if (code != 206) throw new IOException("HTTP " + code);
            set(State.DOWNLOADING);
            long lastTime = System.currentTimeMillis(), lastDone = start;
            double speed = 0;
            try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(part, start > 0)) {
                byte[] buffer = new byte[1 << 16];
                long done = start;
                int n;
                while ((n = in.read(buffer)) > 0) {
                    if (mCancel) throw new InterruptedException();
                    out.write(buffer, 0, n);
                    done += n;
                    long now = System.currentTimeMillis();
                    if (now - lastTime >= 500) {
                        double instant = (done - lastDone) * 1000.0 / (now - lastTime);
                        speed = speed == 0 ? instant : speed * 0.8 + instant * 0.2;
                        synchronized (this) {
                            mDone = done;
                            mRemainingSeconds = speed > 0 ? (long) ((p.bytes - done) / speed) : -1;
                        }
                        lastTime = now;
                        lastDone = done;
                        notifyChanged();
                    }
                }
                out.getFD().sync();
            }
            if (part.length() != p.bytes || !part.renameTo(target)) throw new IOException("size " + part.length());
            p.file = target;
            verify();
        } catch (InterruptedException e) {
            part.delete();
            set(State.NEW_VERSION);
        } catch (Exception e) {
            Log.w(TAG, "download failed", e);
            if (!isOnline()) toast(R.string.net_error_tip);
            else if (downloadDir().getUsableSpace() < p.bytes) toast(R.string.clear_memory_content);
            set(State.DOWNLOAD_FAILED);
        } finally {
            if (c != null) c.disconnect();
        }
    }

    // ------------------------------------------------------------------ verify

    private void verify() {
        Package p = pkg();
        set(State.VERIFYING);
        new Thread(() -> {
            try {
                if (!p.local && !p.sha256.equalsIgnoreCase(sha256(p.file))) throw new IOException("sha256");
                RecoverySystem.verifyPackage(p.file, null, CERTS);
                Package inner = readPackageInfo(p.file);
                if (!currentVersion().equals(inner.baseVersion) || !inner.version.equals(p.version)) {
                    throw new IOException("package is " + inner.baseVersion + " -> " + inner.version);
                }
                if (TextUtils.isEmpty(p.notes)) p.notes = inner.notes;
                set(State.READY);
            } catch (Exception e) {
                Log.w(TAG, "verify failed", e);
                if (!p.local && p.file != null) p.file.delete();
                synchronized (this) { mError = String.valueOf(e.getMessage()); }
                set(State.VERIFY_FAILED);
            }
        }, "source-update-verify").start();
    }

    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[16384];
        int n;
        while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
        return out.toByteArray();
    }

    static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[1 << 16];
            int n;
            while ((n = in.read(buffer)) > 0) digest.update(buffer, 0, n);
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) hex.append(String.format("%02x", b));
        return hex.toString();
    }

    /** package.json of a Source OTA package. */
    static Package readPackageInfo(File file) throws Exception {
        try (ZipFile zip = new ZipFile(file)) {
            ZipEntry entry = zip.getEntry("package.json");
            if (entry == null) throw new IOException("not a Source package");
            JSONObject info;
            try (InputStream in = zip.getInputStream(entry)) {
                info = new JSONObject(new String(readAll(in), "UTF-8"));
            }
            Package p = new Package();
            p.version = info.getString("version");
            p.baseVersion = info.getString("base_version");
            p.notes = info.optString("notes", "");
            p.createdSeconds = parseIsoSeconds(info.optString("created_at_utc", ""));
            p.bytes = file.length();
            p.file = file;
            return p;
        }
    }

    // ------------------------------------------------------------------ offline packages

    public static final int LOCAL_NONE = 0, LOCAL_OK = 1, LOCAL_OTHER_BASE = 2;

    /** /sdcard/dload/*.zip and dload/ on removable volumes: the newest package for this release. */
    public int findLocal() {
        List<File> dirs = new ArrayList<>();
        File[] mounts = new File("/storage").listFiles();
        if (mounts != null) {
            for (File m : mounts) if (!m.getName().equals("self") && !m.getName().equals("emulated")) dirs.add(new File(m, "dload"));
        }
        dirs.add(new File("/sdcard/dload"));
        Package best = null;
        boolean otherBase = false;
        for (File dir : dirs) {
            File[] files = dir.listFiles((d, name) -> name.endsWith(".zip"));
            if (files == null) continue;
            for (File f : files) {
                try {
                    Package p = readPackageInfo(f);
                    if (!currentVersion().equals(p.baseVersion)) {
                        otherBase = true;
                        continue;
                    }
                    p.local = true;
                    if (best == null || compareVersions(p.version, best.version) > 0) best = p;
                } catch (Exception e) {
                    Log.i(TAG, "skip " + f + ": " + e.getMessage());
                }
            }
        }
        if (best == null) return otherBase ? LOCAL_OTHER_BASE : LOCAL_NONE;
        synchronized (this) { mPackage = best; }
        verify();
        return LOCAL_OK;
    }

    // ------------------------------------------------------------------ install

    public interface InstallCallback {
        void onProgress(int done, int total);
        void onResult(String error);  // null: staged, rebooting
    }

    /** Stage through source_updaterd and reboot to the updater recovery. */
    public void install(InstallCallback callback) {
        Package p = pkg();
        if (p == null || p.file == null || !p.file.exists()) {
            callback.onResult(mContext.getString(R.string.no_upgrade_package_tip));
            return;
        }
        set(State.INSTALLING);
        new Thread(() -> {
            String error = null;
            try {
                UpdaterClient.stage(p.file, (done, total) -> mMain.post(() -> callback.onProgress(done, total)));
                Settings.Global.putInt(mContext.getContentResolver(), KEY_RED_DOT, 0);
                Settings.Global.putInt(mContext.getContentResolver(), "pvr_update_silent", 1);
                UpdaterClient.reboot();
            } catch (Exception e) {
                Log.w(TAG, "install failed", e);
                error = String.valueOf(e.getMessage());
                synchronized (this) { mError = error; }
                set(State.INSTALL_FAILED);
            }
            String result = error;
            mMain.post(() -> callback.onResult(result));
        }, "source-update-install").start();
    }

    /** Result of the last updater run ("ok 2.13", "rejected: ..."), read from source_updaterd. */
    public void refreshLastResult() {
        new Thread(() -> {
            try {
                JSONObject status = UpdaterClient.status();
                synchronized (this) { mLastResult = status.optString("result", ""); }
                notifyChanged();
            } catch (Exception e) {
                Log.i(TAG, "status: " + e.getMessage());
            }
        }, "source-update-status").start();
    }
}
