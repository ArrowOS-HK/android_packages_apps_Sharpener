/*
 * Copyright (C) 2026 ArrowOS-T
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

package com.arrow.support.widget;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.ImageView;

/**
 * ImageView that renders a downscaled, stack-blurred copy of the current system wallpaper
 * for the Sharpener lock screen preview card.
 */
public class BlurBackgroundImageView extends ImageView {

    private static final int BLUR_RADIUS = 8;
    private static final int TARGET_WIDTH = 96;
    private static final int FALLBACK_COLOR = 0xFF1E2228;

    public BlurBackgroundImageView(Context context) {
        super(context);
        loadWallpaper(context);
    }

    public BlurBackgroundImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        loadWallpaper(context);
    }

    public BlurBackgroundImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        loadWallpaper(context);
    }

    private void loadWallpaper(Context context) {
        Bitmap wallpaper = getWallpaperBitmapSafely(context);
        if (wallpaper == null) {
            setBackgroundColor(FALLBACK_COLOR);
            return;
        }
        Bitmap blurred = stackBlur(wallpaper, BLUR_RADIUS);
        if (blurred != null) {
            setImageBitmap(blurred);
        } else {
            setBackgroundColor(FALLBACK_COLOR);
        }
    }

    private static Bitmap getWallpaperBitmapSafely(Context context) {
        try {
            WallpaperManager wm = WallpaperManager.getInstance(context);
            if (wm == null) {
                return null;
            }
            Bitmap bmp = wm.getBitmap();
            if (bmp != null) {
                return bmp;
            }
            Drawable drawable = wm.getDrawable();
            if (drawable instanceof BitmapDrawable) {
                return ((BitmapDrawable) drawable).getBitmap();
            }
            if (drawable != null) {
                int w = Math.max(1, drawable.getIntrinsicWidth());
                int h = Math.max(1, drawable.getIntrinsicHeight());
                Bitmap out = Bitmap.createBitmap(
                        Math.min(w, TARGET_WIDTH),
                        Math.max(1, Math.round((float) h * Math.min(w, TARGET_WIDTH) / w)),
                        Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(out);
                drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                drawable.draw(canvas);
                return out;
            }
        } catch (Throwable ignored) {
            // Live wallpaper or restricted wallpaper access falls back to solid surface.
        }
        return null;
    }

    private static Bitmap stackBlur(Bitmap source, int radius) {
        if (source == null || radius < 1 || source.getWidth() <= 0 || source.getHeight() <= 0) {
            return null;
        }
        int scaledHeight = Math.max(1,
                Math.round((float) source.getHeight() * TARGET_WIDTH / source.getWidth()));
        Bitmap bitmap = Bitmap.createScaledBitmap(source, TARGET_WIDTH, scaledHeight, true);
        if (bitmap.getConfig() != Bitmap.Config.ARGB_8888) {
            Bitmap argb = bitmap.copy(Bitmap.Config.ARGB_8888, true);
            bitmap.recycle();
            bitmap = argb;
        }

        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int[] pix = new int[w * h];
        bitmap.getPixels(pix, 0, w, 0, 0, w, h);

        int wm = w - 1;
        int hm = h - 1;
        int wh = w * h;
        int div = radius + radius + 1;

        int[] r = new int[wh];
        int[] g = new int[wh];
        int[] b = new int[wh];
        int[] vmin = new int[Math.max(w, h)];

        int divsum = (div + 1) >> 1;
        divsum *= divsum;
        int[] dv = new int[256 * divsum];
        for (int i = 0; i < dv.length; i++) {
            dv[i] = i / divsum;
        }

        int[][] stack = new int[div][3];
        int r1 = radius + 1;
        int yw = 0;
        int yi = 0;

        for (int y = 0; y < h; y++) {
            int rinsum = 0, ginsum = 0, binsum = 0;
            int routsum = 0, goutsum = 0, boutsum = 0;
            int rsum = 0, gsum = 0, bsum = 0;

            for (int i = -radius; i <= radius; i++) {
                int p = pix[yi + Math.min(wm, Math.max(i, 0))];
                int[] sir = stack[i + radius];
                sir[0] = (p & 0xff0000) >> 16;
                sir[1] = (p & 0x00ff00) >> 8;
                sir[2] = p & 0x0000ff;
                int rbs = r1 - Math.abs(i);
                rsum += sir[0] * rbs;
                gsum += sir[1] * rbs;
                bsum += sir[2] * rbs;
                if (i > 0) {
                    rinsum += sir[0];
                    ginsum += sir[1];
                    binsum += sir[2];
                } else {
                    routsum += sir[0];
                    goutsum += sir[1];
                    boutsum += sir[2];
                }
            }
            int stackpointer = radius;

            for (int x = 0; x < w; x++) {
                r[yi] = dv[rsum];
                g[yi] = dv[gsum];
                b[yi] = dv[bsum];

                rsum -= routsum;
                gsum -= goutsum;
                bsum -= boutsum;

                int stackstart = stackpointer - radius + div;
                int[] sir = stack[stackstart % div];

                routsum -= sir[0];
                goutsum -= sir[1];
                boutsum -= sir[2];

                if (y == 0) {
                    vmin[x] = Math.min(x + radius + 1, wm);
                }
                int p = pix[yw + vmin[x]];
                sir[0] = (p & 0xff0000) >> 16;
                sir[1] = (p & 0x00ff00) >> 8;
                sir[2] = p & 0x0000ff;

                rinsum += sir[0];
                ginsum += sir[1];
                binsum += sir[2];

                rsum += rinsum;
                gsum += ginsum;
                bsum += binsum;

                stackpointer = (stackpointer + 1) % div;
                sir = stack[stackpointer];

                routsum += sir[0];
                goutsum += sir[1];
                boutsum += sir[2];

                rinsum -= sir[0];
                ginsum -= sir[1];
                binsum -= sir[2];

                yi++;
            }
            yw += w;
        }

        for (int x = 0; x < w; x++) {
            int rinsum = 0, ginsum = 0, binsum = 0;
            int routsum = 0, goutsum = 0, boutsum = 0;
            int rsum = 0, gsum = 0, bsum = 0;
            int yp = -radius * w;

            for (int i = -radius; i <= radius; i++) {
                yi = Math.max(0, yp) + x;
                int[] sir = stack[i + radius];
                sir[0] = r[yi];
                sir[1] = g[yi];
                sir[2] = b[yi];
                int rbs = r1 - Math.abs(i);
                rsum += r[yi] * rbs;
                gsum += g[yi] * rbs;
                bsum += b[yi] * rbs;
                if (i > 0) {
                    rinsum += sir[0];
                    ginsum += sir[1];
                    binsum += sir[2];
                } else {
                    routsum += sir[0];
                    goutsum += sir[1];
                    boutsum += sir[2];
                }
                if (i < hm) {
                    yp += w;
                }
            }
            yi = x;
            int stackpointer = radius;

            for (int y = 0; y < h; y++) {
                pix[yi] = (0xff000000 & pix[yi]) | (dv[rsum] << 16) | (dv[gsum] << 8) | dv[bsum];

                rsum -= routsum;
                gsum -= goutsum;
                bsum -= boutsum;

                int stackstart = stackpointer - radius + div;
                int[] sir = stack[stackstart % div];

                routsum -= sir[0];
                goutsum -= sir[1];
                boutsum -= sir[2];

                if (x == 0) {
                    vmin[y] = Math.min(y + r1, hm) * w;
                }
                int p = x + vmin[y];
                sir[0] = r[p];
                sir[1] = g[p];
                sir[2] = b[p];

                rinsum += sir[0];
                ginsum += sir[1];
                binsum += sir[2];

                rsum += rinsum;
                gsum += ginsum;
                bsum += binsum;

                stackpointer = (stackpointer + 1) % div;
                sir = stack[stackpointer];

                routsum += sir[0];
                goutsum += sir[1];
                boutsum += sir[2];

                rinsum -= sir[0];
                ginsum -= sir[1];
                binsum -= sir[2];

                yi += w;
            }
        }

        bitmap.setPixels(pix, 0, w, 0, 0, w, h);
        return bitmap;
    }
}
