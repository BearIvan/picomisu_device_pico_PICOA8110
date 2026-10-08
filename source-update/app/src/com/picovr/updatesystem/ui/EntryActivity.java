// Picomisu Source Update: the page PICO Settings embeds for pvr.intent.action.UPDATESYSTEM
// (System Version row), laid out like PICO's SystemUpdate2 EntryActivity/MainFragment/SettingFragment
// (outputs/audit/pico-system-update-ui-spec.md).
package com.picovr.updatesystem.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.picovr.updatesystem.R;
import com.picovr.updatesystem.ServerDiscovery;
import com.picovr.updatesystem.UpdateModel;
import com.picovr.updatesystem.UpdateModel.State;

import java.util.List;
import java.util.Locale;

public class EntryActivity extends Activity implements UpdateModel.Listener {
    static final int TIPS_NORMAL = 0x66FFFFFF;
    static final int TIPS_ERROR = 0xFFFF5752;

    private UpdateModel mModel;
    private View mRoot, mMainPage, mSettingPage;
    private ImageView mRedDot;
    private TextView mVersion, mTips, mChangelog, mAction;
    private ScrollView mChangelogContainer;
    private ProgressBar mProgress;
    private View mActionBar;
    private Switch mAutoDownload, mAutoUpdate;
    private TextView mServerValue;
    private int mDialogs;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_entry);
        mModel = UpdateModel.get(this);
        mRoot = findViewById(R.id.rootView);
        mMainPage = findViewById(R.id.main_page);
        mSettingPage = findViewById(R.id.setting_page);
        mRedDot = findViewById(R.id.red_dot);
        mVersion = findViewById(R.id.pui_version_info);
        mTips = findViewById(R.id.update_tips);
        mProgress = findViewById(R.id.download_progress_bar);
        mChangelogContainer = findViewById(R.id.pui_version_desc_container);
        mChangelog = findViewById(R.id.pui_version_desc);
        mActionBar = findViewById(R.id.update_action);
        mAction = findViewById(R.id.update_action_desc);
        mAutoDownload = findViewById(R.id.open_auto_upgrade);
        mAutoUpdate = findViewById(R.id.open_auto_update);
        mServerValue = findViewById(R.id.update_server_value);

        findViewById(R.id.back).setOnClickListener(v -> onBackPressed());
        findViewById(R.id.setting).setOnClickListener(v -> showSettings(true));
        mActionBar.setOnClickListener(v -> onAction());
        findViewById(R.id.update_offline).setOnClickListener(v -> onOffline());
        findViewById(R.id.update_server).setOnClickListener(v -> findServer());
        mAutoDownload.setOnCheckedChangeListener((b, checked) -> putGlobal(UpdateModel.KEY_AUTO_DOWNLOAD, checked));
        mAutoUpdate.setOnCheckedChangeListener((b, checked) -> {
            putGlobal(UpdateModel.KEY_AUTO_UPDATE, checked);
            adjustSwitches();
        });
        if ("keep_latest_popup".equals(getIntent().getStringExtra("enter_from"))) showSettings(true);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mModel.addListener(this);
        render();
        renderSettings();
        if (!mModel.isAcceptUpdate()) {
            toast(R.string.forbid_update, false);
        } else if (!mModel.isOnline() && !TextUtils.isEmpty(mModel.serverUrl())) {
            toast(R.string.net_error_tip, false);
        }
        mModel.onPageOpened();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mModel.removeListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mDialogs > 0) {
            mDialogs = 0;
            sendBroadcast(new Intent("com.picovr.broadcast.DialogShowReceiver.stateDismiss"));
        }
    }

    @Override
    public void onBackPressed() {
        if (mSettingPage.getVisibility() == View.VISIBLE) {
            showSettings(false);
        } else {
            finish();  // closes the page in PICO Settings
        }
    }

    @Override
    public void onModelChanged() {
        render();
        int toast = mModel.takeToast();
        if (toast != 0) toast(toast, toast == R.string.clear_memory_content);
    }

    // ------------------------------------------------------------------ main page

    private void render() {
        State state = mModel.state();
        UpdateModel.Package p = mModel.pkg();
        boolean hasNew = p != null && state != State.IDLE && state != State.CHECKING;

        mVersion.setText(hasNew ? getString(R.string.update_version_number, p.version + ".S")
                : getString(R.string.current_version, UpdateModel.displayVersion()));
        mRedDot.setVisibility(hasNew ? View.VISIBLE : View.GONE);
        mProgress.setVisibility(View.GONE);
        mTips.setTextColor(TIPS_NORMAL);

        if (hasNew) {
            mChangelog.setText(p.notes);
            mChangelogContainer.setVisibility(TextUtils.isEmpty(p.notes) ? View.GONE : View.VISIBLE);
        } else {
            String last = mModel.lastResult();
            mChangelogContainer.setVisibility(TextUtils.isEmpty(last) ? View.GONE : View.VISIBLE);
            mChangelog.setText(last.startsWith("ok ") ? getString(R.string.last_update_ok, last.substring(3))
                    : TextUtils.isEmpty(last) ? "" : getString(R.string.last_update_result, last));
        }

        switch (state) {
            case IDLE:
                mTips.setText(TextUtils.isEmpty(mModel.serverUrl()) ? getString(R.string.no_server_tip)
                        : getString(R.string.device_latest_version));
                action(null, false);
                break;
            case CHECKING:
                mTips.setText(R.string.update_checking);
                action(null, false);
                break;
            case NEW_VERSION:
                mTips.setText(sizeAndDate(p));
                action(getString(p.local ? R.string.update_now : R.string.online_update), true);
                break;
            case DOWNLOAD_PREPARE:
                mTips.setText(R.string.prepare_download);
                progress(0, 1);
                action(getString(R.string.cancel_download), false);
                break;
            case DOWNLOADING: {
                long remaining = mModel.remainingSeconds();
                mTips.setText(remaining < 0 || remaining <= 60 ? getString(R.string.remain_dowanload_less_than_one_minutes)
                        : getResources().getQuantityString(R.plurals.remain_dowanload_n_minutes,
                                (int) (remaining / 60), (int) (remaining / 60)));
                progress(mModel.done(), mModel.total());
                action(getString(R.string.cancel_download), true);
                break;
            }
            case DOWNLOAD_FAILED:
                mTips.setText(R.string.download_error_retry);
                mTips.setTextColor(TIPS_ERROR);
                action(getString(R.string.redownload), true);
                break;
            case VERIFYING:
                mTips.setText(R.string.checking_installation_package);
                progress(1, 1);
                action(getString(R.string.update_now), false);
                break;
            case VERIFY_FAILED:
                mTips.setText(p != null && p.local ? getString(R.string.no_suitable_installation_package_tip)
                        : getString(R.string.installation_package_error_content));
                mTips.setTextColor(TIPS_ERROR);
                action(getString(p != null && p.local ? R.string.update_now : R.string.redownload), p == null || !p.local);
                break;
            case READY:
                mTips.setText(sizeAndDate(p));
                action(getString(R.string.update_now), true);
                break;
            case INSTALLING:
                mTips.setText(R.string.system_upgrading);
                action(getString(R.string.update_now), false);
                break;
            case INSTALL_FAILED:
                mTips.setText(getString(R.string.install_error, mModel.error()));
                mTips.setTextColor(TIPS_ERROR);
                action(getString(R.string.update_now), true);
                break;
        }
    }

    private String sizeAndDate(UpdateModel.Package p) {
        if (p == null) return "";
        String size = p.bytes < (1L << 30) ? (p.bytes / 1048576) + "MB"
                : String.format(Locale.ROOT, "%.1fGB", p.bytes / (double) (1L << 30));
        String date = p.createdSeconds <= 0 ? "1900.01.01"
                : DateFormat.format("yyyy.MM.dd", p.createdSeconds * 1000).toString();
        return getString(R.string.size, size) + "   " + getString(R.string.public_date, date);
    }

    private void progress(long done, long total) {
        mProgress.setVisibility(View.VISIBLE);
        mProgress.setProgress(total <= 0 ? 0 : (int) (done * 1000.0 / total));
    }

    private void action(String label, boolean enabled) {
        if (label == null) {
            mActionBar.setVisibility(View.GONE);
            return;
        }
        mActionBar.setVisibility(View.VISIBLE);
        mAction.setText(label);
        mActionBar.setClickable(enabled);
        mAction.setEnabled(enabled);
    }

    private void onAction() {
        switch (mModel.state()) {
            case NEW_VERSION:
            case DOWNLOAD_FAILED:
            case VERIFY_FAILED:
                mModel.startDownload();
                break;
            case DOWNLOADING:
                new CommonDialog(this, R.string.cancel_download_title, getString(R.string.cancel_download_content),
                        R.string.cancel_download, R.string.continue_download, () -> mModel.cancelDownload()).show();
                break;
            case READY:
            case INSTALL_FAILED:
                confirmInstall();
                break;
            default:
                break;
        }
    }

    private void confirmInstall() {
        new CommonDialog(this, R.string.system_upgrade_title, getString(R.string.system_upgrade_content),
                R.string.upgrade, android.R.string.cancel, () -> mRoot.postDelayed(this::install, 50)).show();
    }

    private void install() {
        if (mModel.batteryLevel() < 40) {
            new CommonDialog(this, R.string.low_battery_title, getString(R.string.battery_low_content),
                    R.string.i_know, 0, null).show();
            return;
        }
        ProgressDialog progress = new ProgressDialog(this);
        progress.show();
        mModel.install(new UpdateModel.InstallCallback() {
            @Override
            public void onProgress(int done, int total) {
                progress.setProgress(done, total);
            }

            @Override
            public void onResult(String error) {
                if (error != null) {
                    progress.dismiss();
                    toast(error, true);
                }
                // Otherwise the headset reboots to the updater recovery.
            }
        });
    }

    // ------------------------------------------------------------------ settings page

    private void showSettings(boolean show) {
        mSettingPage.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) renderSettings();
    }

    private void renderSettings() {
        int download = Settings.Global.getInt(getContentResolver(), UpdateModel.KEY_AUTO_DOWNLOAD, -1);
        mAutoDownload.setChecked(download == -1 ? mModel.isAcceptUpdate() : download == 1);
        mAutoUpdate.setChecked(Settings.Global.getInt(getContentResolver(), UpdateModel.KEY_AUTO_UPDATE, -1) == 1);
        adjustSwitches();
        String url = mModel.serverUrl();
        mServerValue.setText(TextUtils.isEmpty(url) ? getString(R.string.server_not_set) : url);
        State state = mModel.state();
        boolean busy = state == State.DOWNLOADING || state == State.DOWNLOAD_PREPARE || state == State.VERIFYING;
        View offline = findViewById(R.id.update_offline);
        offline.setEnabled(!busy);
        offline.setAlpha(busy ? 0.6f : 1f);
    }

    private void adjustSwitches() {
        if (mAutoUpdate.isChecked()) {
            mAutoDownload.setChecked(true);
            mAutoDownload.setEnabled(false);
            mAutoDownload.setAlpha(0.3f);
        } else {
            mAutoDownload.setEnabled(true);
            mAutoDownload.setAlpha(1f);
        }
    }

    private void putGlobal(String key, boolean value) {
        Settings.Global.putInt(getContentResolver(), key, value ? 1 : 0);
    }

    private void onOffline() {
        int found = mModel.findLocal();
        if (found == UpdateModel.LOCAL_OK) {
            showSettings(false);
        } else if (found == UpdateModel.LOCAL_OTHER_BASE) {
            new CommonDialog(this, R.string.offline_upgrade, getString(R.string.incompatible_package_tips),
                    R.string.i_know, 0, null).show();
        } else {
            toast(R.string.no_upgrade_package_tip, false);
        }
    }

    /** The embedded page gets no keyboard: find servers on the network first, typing is the fallback. */
    private void findServer() {
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_servers, null);
        TextView status = content.findViewById(R.id.servers_status);
        LinearLayout list = content.findViewById(R.id.servers_list);
        status.setText(R.string.searching_server);
        CommonDialog dialog = new CommonDialog(this, R.string.update_server, getString(R.string.find_server_desc),
                R.string.enter_manually, android.R.string.cancel, this::editServer);
        dialog.setContent(content);
        dialog.show();
        new Thread(() -> {
            List<ServerDiscovery.Server> servers = ServerDiscovery.find(2000);
            runOnUiThread(() -> {
                if (!dialog.isShowing()) return;
                if (servers.isEmpty()) {
                    status.setText(R.string.server_not_found);
                    return;
                }
                status.setVisibility(View.GONE);
                for (ServerDiscovery.Server server : servers) {
                    list.addView(serverRow(server, () -> {
                        dialog.dismiss();
                        useServer(server.url);
                    }));
                }
            });
        }, "SourceUpdateDiscovery").start();
    }

    private View serverRow(ServerDiscovery.Server server, Runnable onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackgroundResource(R.drawable.row_bg);
        row.setPadding(dp(16), 0, dp(16), 0);
        TextView title = new TextView(this, null, 0, R.style.ItemTitle);
        title.setText(server.name);
        TextView url = new TextView(this, null, 0, R.style.ItemSubtitle);
        url.setText(server.url);
        url.setSingleLine(true);
        url.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = dp(16);
        row.addView(title, titleParams);
        row.addView(url);
        row.setOnClickListener(v -> onClick.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(4);
        row.setLayoutParams(params);
        return row;
    }

    private void useServer(String url) {
        mModel.setServerUrl(url);
        renderSettings();
        showSettings(false);
        mModel.check();
    }

    private void editServer() {
        EditText input = (EditText) LayoutInflater.from(this).inflate(R.layout.dialog_edit, null);
        input.setText(mModel.serverUrl());
        input.setHint(R.string.server_hint);
        CommonDialog dialog = new CommonDialog(this, R.string.update_server, getString(R.string.server_desc),
                R.string.save, android.R.string.cancel, () -> useServer(input.getText().toString()));
        dialog.setContent(input);
        dialog.show();
    }

    // ------------------------------------------------------------------ dialogs and toasts

    /** PICO contract: dim this page and tell Settings to dim its sidebar while a dialog is shown. */
    void onDialogShown() {
        if (mDialogs++ == 0) {
            mRoot.setForeground(getDrawable(R.drawable.dialog_bg_mask));
            sendBroadcast(new Intent("com.picovr.broadcast.DialogShowReceiver.state"));
        }
    }

    void onDialogDismissed() {
        if (mDialogs > 0 && --mDialogs == 0) {
            mRoot.setForeground(null);
            sendBroadcast(new Intent("com.picovr.broadcast.DialogShowReceiver.stateDismiss"));
        }
    }

    private void toast(int text, boolean error) {
        toast(getString(text), error);
    }

    void toast(String text, boolean error) {
        View view = LayoutInflater.from(this).inflate(R.layout.toast, null);
        TextView label = view.findViewById(R.id.toast_text);
        label.setText(text);
        label.setCompoundDrawablesRelativeWithIntrinsicBounds(error ? R.drawable.warning_red : 0, 0, 0, 0);
        Toast toast = new Toast(this);
        toast.setView(view);
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.CENTER, -dp(120), 0);
        toast.show();
    }

    int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    static ColorDrawable transparent() {
        return new ColorDrawable(0);
    }
}
