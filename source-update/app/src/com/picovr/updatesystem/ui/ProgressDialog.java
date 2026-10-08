// Picomisu Source Update: staging progress (PICO dialog_progress look: title, subtitle, 6 u bar,
// progress text). Not cancelable: source_updaterd finishes or rejects the package by itself.
package com.picovr.updatesystem.ui;

import android.app.Dialog;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.picovr.updatesystem.R;

class ProgressDialog extends Dialog {
    private final EntryActivity mActivity;
    private final ProgressBar mBar;
    private final TextView mText;

    ProgressDialog(EntryActivity activity) {
        super(activity, R.style.SourceDialog);
        mActivity = activity;
        setContentView(R.layout.dialog_progress);
        setCancelable(false);
        ((TextView) findViewById(R.id.dialog_title)).setText(R.string.system_upgrade_title);
        ((TextView) findViewById(R.id.dialog_subtitle)).setText(R.string.preparing_update);
        mBar = findViewById(R.id.dialog_progress_bar);
        mText = findViewById(R.id.dialog_progress_msg);
        findViewById(R.id.dialog_buttons).setVisibility(View.GONE);
        Window window = getWindow();
        window.setBackgroundDrawable(EntryActivity.transparent());
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams lp = window.getAttributes();
        lp.gravity = Gravity.CENTER;
        lp.x = -activity.dp(120);
        window.setAttributes(lp);
        setOnShowListener(d -> mActivity.onDialogShown());
        setOnDismissListener(d -> mActivity.onDialogDismissed());
    }

    void setProgress(int done, int total) {
        mBar.setProgress(total <= 0 ? 0 : done * 1000 / total);
        mText.setText(mActivity.getString(R.string.staging_progress, done, total));
    }
}
