/*
 * Copyright (C) 2024 ArrowOS-T
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.arrow.support.util;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import androidx.preference.PreferenceScreen;
import com.android.settings.R;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settingslib.widget.LayoutPreference;

import java.util.HashMap;
import java.util.Map;

public class SharpenerMenuController extends AbstractPreferenceController {

    private static final String KEY_MENU_LAYOUT = "sharpener_menu_layout";

    public SharpenerMenuController(Context context) {
        super(context);
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);

        LayoutPreference sharpenerMenu = (LayoutPreference) screen.findPreference(KEY_MENU_LAYOUT);
        if (sharpenerMenu == null) {
            return;
        }

        Map<Integer, String> clickMap = new HashMap<>();
        clickMap.put(R.id.sharpener_lockscreen, "com.android.settings.Settings$SharpenerLockScreenActivity");
        clickMap.put(R.id.sharpener_themes, "com.android.settings.Settings$SharpenerThemesActivity");
        clickMap.put(R.id.sharpener_notifications, "com.android.settings.Settings$SharpenerNotificationActivity");
        clickMap.put(R.id.sharpener_quicksettings, "com.android.settings.Settings$SharpenerQuickSettingsActivity");
        clickMap.put(R.id.sharpener_statusbar, "com.android.settings.Settings$SharpenerStatusBarActivity");
        clickMap.put(R.id.sharpener_about, "com.android.settings.Settings$SharpenerAboutActivity");
        clickMap.put(R.id.sharpener_gestures, "com.android.settings.Settings$SettingsGestureActivity");
        clickMap.put(R.id.sharpener_buttons, "com.android.settings.Settings$SharpenerButtonsActivity");
        clickMap.put(R.id.sharpener_misc, "com.android.settings.Settings$SharpenerMiscActivity");

        for (Map.Entry<Integer, String> entry : clickMap.entrySet()) {
            View view = sharpenerMenu.findViewById(entry.getKey());
            if (view != null) {
                Intent intent = new Intent().setComponent(
                        new ComponentName("com.android.settings", entry.getValue()));
                view.setOnClickListener(v -> mContext.startActivity(intent));
            }
        }
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String getPreferenceKey() {
        return KEY_MENU_LAYOUT;
    }
}