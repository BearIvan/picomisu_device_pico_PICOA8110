// Picomisu Source Update: launcher / pvr.intent.action.SYSTEM_UPDATE trampoline, kept at the factory
// component name com.picovr.updatesystem/.MainActivity because PICO Settings (VersionManager
// fallback), Provision, Matrix and VersionManager start it explicitly. Like the factory one it opens
// the update page embedded in PICO Settings (pui.settings.action.COMMON_ACTIVITY_VIEW), or the page
// on its own panel if Settings cannot show it.
package com.picovr.updatesystem;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;

import com.picovr.updatesystem.ui.EntryActivity;

public class MainActivity extends Activity {
    private static final String SETTINGS = "com.picovr.settings";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        String from = getIntent().getStringExtra("enter_from");
        if (from == null) from = "push";
        Intent page = new Intent("pvr.intent.action.UPDATESYSTEM").putExtra("enter_from", from);
        boolean shown = false;
        if (settingsShowsUpdatePage()) {
            try {
                startActivity(new Intent("pui.settings.action.COMMON_ACTIVITY_VIEW").setPackage(SETTINGS)
                        .putExtra(Intent.EXTRA_INTENT, page).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                shown = true;
            } catch (ActivityNotFoundException ignored) {
            }
        }
        if (!shown) {
            startActivity(new Intent(this, EntryActivity.class).putExtra("enter_from", from)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        finish();
    }

    private boolean settingsShowsUpdatePage() {
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(SETTINGS, PackageManager.GET_META_DATA);
            return info.metaData != null && info.metaData.getInt("support_show_update_page", 0) == 1;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
