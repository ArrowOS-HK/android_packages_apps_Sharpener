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

package com.arrow.support.fragments;

import com.android.internal.logging.nano.MetricsProto;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.os.Bundle;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.UserHandle;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.net.Uri;
import android.widget.Toast;
import com.arrow.support.provider.InvCustomImageProvider;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceCategory;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceFragment;
import androidx.preference.SwitchPreference;
import android.provider.Settings;
import com.android.settings.R;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import android.text.TextUtils;
import android.view.View;

import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;
import android.util.Log;

import com.android.internal.util.arrow.SystemRestartUtils;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

public class QuickSettings extends SettingsPreferenceFragment implements OnPreferenceChangeListener {

    private static final int REQUEST_PICK_INVQS_IMAGE = 9001;
    private static final String KEY_INVQS_ENABLED = "qs_invqs_enabled";
    private static final String KEY_INVQS_USERNAME = "invos_qs_custom_username";
    private static final String KEY_INVQS_SUBTITLE = "invos_qs_custom_subtitle";
    private static final String KEY_INVQS_IMAGE_SELECT = "invos_qs_custom_image_select";
    private static final String KEY_INVQS_IMAGE_CLEAR = "invos_qs_custom_image_clear";
    private static final String[] qsCustPreferences = { "qs_tile_shape",
            "qqs_num_columns", "qqs_num_columns_landscape",
            "qs_num_columns", "qs_num_columns_landscape" };

    private Preference mInvQsEnabledPref;
    private EditTextPreference mInvQsUsernamePref;
    private EditTextPreference mInvQsSubtitlePref;
    private PreferenceCategory mBrightnessCategory;
    private PreferenceCategory mTileCategory;
    private boolean mQsStyleRound = true;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.sharpener_quicksettings);
        PreferenceScreen prefScreen = getPreferenceScreen();
        ContentResolver resolver = getContext().getContentResolver();

        mInvQsEnabledPref = findPreference(KEY_INVQS_ENABLED);
        if (mInvQsEnabledPref != null) {
            mInvQsEnabledPref.setOnPreferenceChangeListener(this);
        }

        mInvQsUsernamePref = findPreference(KEY_INVQS_USERNAME);
        if (mInvQsUsernamePref != null) {
            String curUser = Settings.System.getStringForUser(
                    resolver, KEY_INVQS_USERNAME, UserHandle.USER_CURRENT);
            mInvQsUsernamePref.setText(curUser);
            mInvQsUsernamePref.setSummary(TextUtils.isEmpty(curUser) ? "ArrowOS" : curUser);
            mInvQsUsernamePref.setOnPreferenceChangeListener(this);
        }

        mInvQsSubtitlePref = findPreference(KEY_INVQS_SUBTITLE);
        if (mInvQsSubtitlePref != null) {
            String curSub = Settings.System.getStringForUser(
                    resolver, KEY_INVQS_SUBTITLE, UserHandle.USER_CURRENT);
            mInvQsSubtitlePref.setText(curSub);
            mInvQsSubtitlePref.setSummary(
                    TextUtils.isEmpty(curSub) ? "Extended QS" : curSub);
            mInvQsSubtitlePref.setOnPreferenceChangeListener(this);
        }

        Preference imageSelectPref = findPreference(KEY_INVQS_IMAGE_SELECT);
        if (imageSelectPref != null) {
            imageSelectPref.setOnPreferenceClickListener(pref -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/*");
                startActivityForResult(intent, REQUEST_PICK_INVQS_IMAGE);
                return true;
            });
        }

        Preference imageClearPref = findPreference(KEY_INVQS_IMAGE_CLEAR);
        if (imageClearPref != null) {
            imageClearPref.setOnPreferenceClickListener(pref -> {
                Context ctx = getContext();
                if (ctx != null) {
                    try {
                        new File(ctx.createDeviceProtectedStorageContext().getFilesDir(),
                                InvCustomImageProvider.FILE_NAME).delete();
                        new File(ctx.getFilesDir(), InvCustomImageProvider.FILE_NAME).delete();
                    } catch (Throwable ignored) {
                    }
                }
                Settings.System.putStringForUser(
                        resolver, "invos_qs_custom_image", "", UserHandle.USER_CURRENT);
                if (ctx != null) {
                    Toast.makeText(ctx, "Custom image cleared", Toast.LENGTH_SHORT).show();
                }
                return true;
            });
        }

        mQsStyleRound = Settings.Secure.getIntForUser(resolver,
                Settings.Secure.QS_STYLE_ROUND, 1, UserHandle.USER_CURRENT) == 1;

        mBrightnessCategory = (PreferenceCategory) prefScreen.findPreference(
                "quick_settings_brighness_category");
        mTileCategory = (PreferenceCategory) prefScreen.findPreference(
                "quick_settings_tile_category");

        boolean invQsEnabled = Settings.System.getIntForUser(resolver,
                KEY_INVQS_ENABLED, 1, UserHandle.USER_CURRENT) != 0;
        applyQsDependencyStates(invQsEnabled);
    }

    /**
     * The stock brightness slider and tile options have no effect while Extended QS is active,
     * because the InvQS panel renders its own controls/tiles. Keep them visible but greyed out
     * so the page still reflects what is available when Extended QS is disabled again.
     */
    private void applyQsDependencyStates(boolean invQsEnabled) {
        setCategoryEnabled(mBrightnessCategory, !invQsEnabled);
        if (mTileCategory == null) {
            return;
        }
        if (invQsEnabled) {
            setCategoryEnabled(mTileCategory, false);
            return;
        }
        setCategoryEnabled(mTileCategory, true);
        if (!mQsStyleRound) {
            for (String key : qsCustPreferences) {
                Preference preference = mTileCategory.findPreference(key);
                if (preference != null) {
                    preference.setEnabled(false);
                }
            }
        }
    }

    private void setCategoryEnabled(PreferenceCategory category, boolean enabled) {
        if (category == null) {
            return;
        }
        category.setEnabled(enabled);
        for (int i = 0; i < category.getPreferenceCount(); i++) {
            category.getPreference(i).setEnabled(enabled);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_INVQS_IMAGE && resultCode == Activity.RESULT_OK
                && data != null && data.getData() != null) {
            Uri uri = data.getData();
            Context ctx = getContext();
            if (ctx != null) {
                String contentUri = persistPickedImage(ctx, uri);
                if (contentUri != null) {
                    try {
                        Settings.System.putStringForUser(ctx.getContentResolver(),
                                "invos_qs_custom_image", contentUri, UserHandle.USER_CURRENT);
                        Toast.makeText(ctx, "Custom image applied", Toast.LENGTH_SHORT).show();
                    } catch (Throwable t) {
                        Log.e("QuickSettings", "Failed saving custom image URI", t);
                        Toast.makeText(ctx, "Failed to apply custom image", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(ctx, "Failed to load selected image", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private String persistPickedImage(Context ctx, Uri sourceUri) {
        try {
            ContentResolver cr = ctx.getContentResolver();
            String mime = cr.getType(sourceUri);
            boolean isGifOrWebp = mime != null && (mime.contains("gif") || mime.contains("webp"));
            File deDir = ctx.createDeviceProtectedStorageContext().getFilesDir();
            if (!deDir.exists()) deDir.mkdirs();
            File outFile = new File(deDir, InvCustomImageProvider.FILE_NAME);

            if (isGifOrWebp) {
                byte[] rawBytes = readAllBytesLimited(cr, sourceUri, 6 * 1024 * 1024);
                if (rawBytes != null && rawBytes.length > 0) {
                    try (OutputStream out = new FileOutputStream(outFile)) {
                        out.write(rawBytes);
                    }
                    return "content://" + InvCustomImageProvider.AUTHORITY
                            + "/custom?t=" + System.currentTimeMillis();
                }
            }

            ImageDecoder.Source src = ImageDecoder.createSource(cr, sourceUri);
            Bitmap bmp = ImageDecoder.decodeBitmap(src, (decoder, info, s) -> {
                int w = info.getSize().getWidth();
                int h = info.getSize().getHeight();
                int maxDim = 800;
                if (w > maxDim || h > maxDim) {
                    int scale = Math.max(1, Math.max(w / maxDim, h / maxDim));
                    decoder.setTargetSampleSize(scale);
                }
            });

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, 88, baos);
            bmp.recycle();
            byte[] jpeg = baos.toByteArray();
            if (jpeg.length == 0) {
                return null;
            }
            try (OutputStream out = new FileOutputStream(outFile)) {
                out.write(jpeg);
            }
            return "content://" + InvCustomImageProvider.AUTHORITY
                    + "/custom?t=" + System.currentTimeMillis();
        } catch (Throwable t) {
            Log.e("QuickSettings", "persistPickedImage failed", t);
            return null;
        }
    }

    private byte[] readAllBytesLimited(ContentResolver cr, Uri uri, int maxBytes) {
        try (InputStream in = cr.openInputStream(uri)) {
            if (in == null) return null;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int total = 0;
            int len;
            while ((len = in.read(buf)) > 0) {
                total += len;
                if (total > maxBytes) return null;
                baos.write(buf, 0, len);
            }
            return baos.toByteArray();
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        Context mContext = getActivity().getApplicationContext();
        ContentResolver resolver = mContext.getContentResolver();
        
        if (preference == mInvQsEnabledPref) {
            applyQsDependencyStates(newValue instanceof Boolean && (Boolean) newValue);
            SystemRestartUtils.showSystemUIRestartDialog(getActivity());
            return true;
        }
        if (preference == mInvQsUsernamePref && newValue instanceof String) {
            String value = ((String) newValue).trim();
            Settings.System.putStringForUser(
                    resolver, KEY_INVQS_USERNAME, value, UserHandle.USER_CURRENT);
            mInvQsUsernamePref.setSummary(TextUtils.isEmpty(value) ? "ArrowOS" : value);
            return true;
        }
        if (preference == mInvQsSubtitlePref && newValue instanceof String) {
            String value = ((String) newValue).trim();
            Settings.System.putStringForUser(
                    resolver, KEY_INVQS_SUBTITLE, value, UserHandle.USER_CURRENT);
            mInvQsSubtitlePref.setSummary(
                    TextUtils.isEmpty(value) ? "Personalize in Sharpener" : value);
            return true;
        }
        return false;
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.ARROW;
    }

}