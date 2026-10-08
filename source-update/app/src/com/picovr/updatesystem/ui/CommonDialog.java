// Picomisu Source Update: PICO DialogCommon (400 u card, #333333 r24, title, subtitle, grey cancel
// on the left, blue confirm on the right; stacked when a label does not fit; moved 120 u left).
package com.picovr.updatesystem.ui;

import android.app.Dialog;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.picovr.updatesystem.R;

class CommonDialog extends Dialog {
    private final EntryActivity mActivity;

    CommonDialog(EntryActivity activity, int title, String subtitle, int confirm, int cancel, Runnable onConfirm) {
        super(activity, R.style.SourceDialog);
        mActivity = activity;
        setContentView(R.layout.dialog_common);
        setCanceledOnTouchOutside(false);
        ((TextView) findViewById(R.id.dialog_title)).setText(title);
        TextView sub = findViewById(R.id.dialog_subtitle);
        sub.setText(subtitle);
        sub.setVisibility(subtitle == null || subtitle.isEmpty() ? View.GONE : View.VISIBLE);
        Button ok = findViewById(R.id.dialog_confirm);
        Button no = findViewById(R.id.dialog_cancel);
        ok.setText(confirm);
        ok.setOnClickListener(v -> {
            dismiss();
            if (onConfirm != null) onConfirm.run();
        });
        if (cancel == 0) {
            no.setVisibility(View.GONE);
        } else {
            no.setText(cancel);
            no.setOnClickListener(v -> dismiss());
        }
        stackIfNeeded(ok, no);
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

    void setContent(View view) {
        FrameLayout slot = findViewById(R.id.dialog_content);
        slot.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        slot.setVisibility(View.VISIBLE);
    }

    /** PICO: if a label is wider than 160 u or a button is hidden, both become full width, confirm on top. */
    private void stackIfNeeded(Button ok, Button no) {
        int max = mActivity.dp(160) - 2 * mActivity.dp(12);
        boolean stack = no.getVisibility() == View.GONE
                || ok.getPaint().measureText(ok.getText().toString()) > max
                || no.getPaint().measureText(no.getText().toString()) > max;
        if (!stack) return;
        LinearLayout row = findViewById(R.id.dialog_buttons);
        row.setOrientation(LinearLayout.VERTICAL);
        row.removeAllViews();
        LinearLayout.LayoutParams top = new LinearLayout.LayoutParams(mActivity.dp(336), mActivity.dp(48));
        row.addView(ok, top);
        if (no.getVisibility() != View.GONE) {
            LinearLayout.LayoutParams bottom = new LinearLayout.LayoutParams(mActivity.dp(336), mActivity.dp(48));
            bottom.topMargin = mActivity.dp(12);
            row.addView(no, bottom);
        }
    }
}
