/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package de.mirsolutions.android.mirkbd;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.util.Log;
import android.view.ViewConfiguration;
import android.view.inputmethod.EditorInfo;

import java.util.List;
import java.util.Locale;

public class LatinKeyboard extends Keyboard {

    private static final boolean DEBUG_PREFERRED_LETTER = true;
    private static final String TAG = "KBDmir2U/LK";
    private static final int OPACITY_FULLY_OPAQUE = 255;
    private static final int SPACE_LED_LENGTH_PERCENT = 80;

    private Drawable mShiftLockIcon;
    private Drawable mShiftLockPreviewIcon;
    private Drawable mOldShiftIcon;
    private Drawable mSpaceIcon;
    private Drawable mSpaceAutoCompletionIndicator;
    private Drawable mSpacePreviewIcon;
    private Drawable mMicIcon;
    private Drawable mMicPreviewIcon;
    private Drawable mSettingsIcon;
    private Drawable mSettingsPreviewIcon;
    private Drawable m123MicIcon;
    private Drawable m123MicPreviewIcon;
    private final Drawable mButtonArrowLeftIcon;
    private final Drawable mButtonArrowRightIcon;
    private Key mShiftKey;
    private Key mEnterKey;
    private Key mF1Key;
    private final Drawable mHintIcon;
    private Key mSpaceKey;
    private Key m123Key;
    private final int[] mSpaceKeyIndexArray;
    private int mSpaceDragStartX;
    private int mSpaceDragLastDiff;
    private Locale mLocale;
    private LanguageSwitcher mLanguageSwitcher;
    private final Resources mRes;
    private final Context mContext;
    private int mMode;
    private final boolean mIsAlphaKeyboard;
    private final boolean mIsAlphaFullKeyboard;
    private final boolean mIsFnFullKeyboard;
    private CharSequence m123Label;
    private int mPrefLetter;
    private int mPrefLetterX;
    private int mPrefLetterY;
    private int mPrefDistance;

    // TODO: remove this attribute when either Keyboard.mDefaultVerticalGap or Key.parent becomes
    // non-private.
    private final int mVerticalGap;

    private static final float SPACEBAR_DRAG_THRESHOLD = 0.51f;
    private static final float OVERLAP_PERCENTAGE_LOW_PROB = 0.70f;
    private static final float OVERLAP_PERCENTAGE_HIGH_PROB = 0.85f;
    // Minimum width of space key preview (proportional to keyboard width)
    private static final float SPACEBAR_POPUP_MIN_RATIO = 0.4f;
    // Minimum width of space key preview (proportional to screen height)
    private static final float SPACEBAR_POPUP_MAX_RATIO = 0.4f;
    // Height in space key the language name will be drawn. (proportional to space key height)
    private static final float SPACEBAR_LANGUAGE_BASELINE = 0.6f;
    // If the full language name needs to be smaller than this value to be drawn on space key,
    // its short language name will be used instead.
    private static final float MINIMUM_SCALE_OF_LANGUAGE_NAME = 0.8f;

    private static int sSpacebarVerticalCorrection;

    public LatinKeyboard(Context context, int xmlLayoutResId) {
        this(context, xmlLayoutResId, 0, 0);
    }

    public LatinKeyboard(Context context, int xmlLayoutResId, int mode, float kbHeightPercent) {
        super(context, 0, xmlLayoutResId, mode, kbHeightPercent);
        final Resources res = context.getResources();
        //Log.i(TAG, "keyHeight=" + this.getKeyHeight());
        //this.setKeyHeight(30); // is useless, see http://code.google.com/p/android/issues/detail?id=4532
        mContext = context;
        mMode = mode;
        mRes = res;
        mShiftLockIcon = res.getDrawable(R.drawable.sym_keyboard_shift_locked);
        mShiftLockPreviewIcon = res.getDrawable(R.drawable.sym_keyboard_feedback_shift_locked);
        setDefaultBounds(mShiftLockPreviewIcon);
        mSpaceIcon = res.getDrawable(R.drawable.sym_keyboard_space);
        mSpaceAutoCompletionIndicator = res.getDrawable(R.drawable.sym_keyboard_space_led);
        mSpacePreviewIcon = res.getDrawable(R.drawable.sym_keyboard_feedback_space);
        mMicIcon = res.getDrawable(R.drawable.sym_keyboard_mic);
        mMicPreviewIcon = res.getDrawable(R.drawable.sym_keyboard_feedback_mic);
        mSettingsIcon = res.getDrawable(R.drawable.sym_keyboard_settings);
        mSettingsPreviewIcon = res.getDrawable(R.drawable.sym_keyboard_feedback_settings);
        setDefaultBounds(mMicPreviewIcon);
        mButtonArrowLeftIcon = res.getDrawable(R.drawable.sym_keyboard_language_arrows_left);
        mButtonArrowRightIcon = res.getDrawable(R.drawable.sym_keyboard_language_arrows_right);
        m123MicIcon = res.getDrawable(R.drawable.sym_keyboard_123_mic);
        m123MicPreviewIcon = res.getDrawable(R.drawable.sym_keyboard_feedback_123_mic);
        mHintIcon = res.getDrawable(R.drawable.hint_popup);
        setDefaultBounds(m123MicPreviewIcon);
        sSpacebarVerticalCorrection = res.getDimensionPixelOffset(
                R.dimen.spacebar_vertical_correction);
        mIsAlphaKeyboard = xmlLayoutResId == R.xml.kbd_qwerty;
        mIsAlphaFullKeyboard = xmlLayoutResId == R.xml.kbd_full;
        mIsFnFullKeyboard = xmlLayoutResId == R.xml.kbd_full_fn;
        // The index of space key is available only after Keyboard constructor has finished.
        mSpaceKeyIndexArray = new int[] { indexOf(LatinIME.ASCII_SPACE) };
        // TODO remove this initialization after cleanup
        mVerticalGap = super.getVerticalGap();
    }

    @Override
    protected Key createKeyFromXml(Resources res, Row parent, int x, int y,
            XmlResourceParser parser) {
        Key key = new LatinKey(res, parent, x, y, parser);
        if (key.codes == null) return key;
        switch (key.codes[0]) {
        case LatinIME.ASCII_ENTER:
            mEnterKey = key;
            break;
        case LatinKeyboardView.KEYCODE_F1:
            mF1Key = key;
            break;
        case LatinIME.ASCII_SPACE:
            mSpaceKey = key;
            break;
        case KEYCODE_MODE_CHANGE:
            m123Key = key;
            m123Label = key.label;
            break;
        }

        return key;
    }

    void setImeOptions(Resources res, int mode, int options) {
        mMode = mode;
        // TODO should clean up this method
        if (mEnterKey != null) {
            // Reset some of the rarely used attributes.
            mEnterKey.popupCharacters = null;
            mEnterKey.popupResId = 0;
            mEnterKey.text = null;
            switch (options&(EditorInfo.IME_MASK_ACTION|EditorInfo.IME_FLAG_NO_ENTER_ACTION)) {
                case EditorInfo.IME_ACTION_GO:
                    mEnterKey.iconPreview = null;
                    mEnterKey.icon = null;
                    mEnterKey.label = res.getText(R.string.label_go_key);
                    break;
                case EditorInfo.IME_ACTION_NEXT:
                    mEnterKey.iconPreview = null;
                    mEnterKey.icon = null;
                    mEnterKey.label = res.getText(R.string.label_next_key);
                    break;
                case EditorInfo.IME_ACTION_DONE:
                    mEnterKey.iconPreview = null;
                    mEnterKey.icon = null;
                    mEnterKey.label = res.getText(R.string.label_done_key);
                    break;
                case EditorInfo.IME_ACTION_SEARCH:
                    mEnterKey.iconPreview = res.getDrawable(
                            R.drawable.sym_keyboard_feedback_search);
                    mEnterKey.icon = res.getDrawable(R.drawable.sym_keyboard_search);
                    mEnterKey.label = null;
                    break;
                case EditorInfo.IME_ACTION_SEND:
                    mEnterKey.iconPreview = null;
                    mEnterKey.icon = null;
                    mEnterKey.label = res.getText(R.string.label_send_key);
                    break;
                default:
                    // Keep Return key in IM mode, we have a dedicated smiley key.
                    mEnterKey.iconPreview = res.getDrawable(
                            R.drawable.sym_keyboard_feedback_return);
                    mEnterKey.icon = res.getDrawable(R.drawable.sym_keyboard_return);
                    mEnterKey.label = null;
                    break;
            }
            // Set the initial size of the preview icon
            if (mEnterKey.iconPreview != null) {
                setDefaultBounds(mEnterKey.iconPreview);
            }
        }
    }

    void enableShiftLock() {
        int index = getShiftKeyIndex();
        if (index >= 0) {
            mShiftKey = getKeys().get(index);
            mOldShiftIcon = mShiftKey.icon;
        }
    }

    @Override
    public boolean setShiftState(int shiftState) {
        if (mShiftKey != null) {
            // Tri-state LED tracks "on" and "lock" states, icon shows Caps state.
            mShiftKey.on = shiftState == SHIFT_ON || shiftState == SHIFT_LOCKED;
            mShiftKey.locked = shiftState == SHIFT_LOCKED || shiftState == SHIFT_CAPS_LOCKED;
            mShiftKey.icon = (shiftState == SHIFT_OFF || shiftState == SHIFT_ON || shiftState == SHIFT_LOCKED) ?
                    mOldShiftIcon : mShiftLockIcon;
            return super.setShiftState(shiftState, false);
        } else {
            return super.setShiftState(shiftState, true);
        }
    }

    /* package */ boolean isAlphaKeyboard() {
        return mIsAlphaKeyboard;
    }

    public void updateSymbolIcons(boolean isAutoCompletion) {
        updateDynamicKeys();
        updateSpaceBarForLocale(isAutoCompletion);
    }

    private void setDefaultBounds(Drawable drawable) {
        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
    }

    private void updateDynamicKeys() {
        update123Key();
        updateF1Key();
    }

    private void update123Key() {
        // Update KEYCODE_MODE_CHANGE key only on alphabet mode, not on symbol mode.
        if (m123Key != null && mIsAlphaKeyboard) {
                m123Key.icon = null;
                m123Key.iconPreview = null;
                m123Key.label = m123Label;
        }
    }

    private void updateF1Key() {
        // Update KEYCODE_F1 key. Please note that some keyboard layouts have no F1 key.
        if (mF1Key == null)
            return;

        if (mIsAlphaKeyboard) {
            if (mMode == KeyboardSwitcher.MODE_URL) {
                setNonMicF1Key(mF1Key, "/", R.xml.popup_slash);
            } else if (mMode == KeyboardSwitcher.MODE_EMAIL) {
                setNonMicF1Key(mF1Key, "@", R.xml.popup_at);
            } else {
                    setNonMicF1Key(mF1Key, ",", R.xml.popup_comma);
            }
        } else if (mIsAlphaFullKeyboard) {
        		setSettingsF1Key(mF1Key);
        } else if (mIsFnFullKeyboard) {
		//XXX TODO nope
        		setSettingsF1Key(mF1Key);
        } else {  // Symbols keyboard
                setNonMicF1Key(mF1Key, ",", R.xml.popup_comma);
        }
    }

    private void setSettingsF1Key(Key key) {
        if (key.shiftLabel != null && key.label != null) {
            key.codes = new int[] { key.label.charAt(0) };
            return; // leave key otherwise unmodified
        }
        final Drawable settingsHintDrawable = new BitmapDrawable(mRes,
                drawSynthesizedSettingsHintImage(key.width, key.height, mSettingsIcon, mHintIcon));
    	key.label = null;
    	key.icon = settingsHintDrawable;
    	key.codes = new int[] { LatinKeyboardView.KEYCODE_OPTIONS };
    	key.popupResId = R.xml.popup_smileys; //XXX was popup_mic
    	key.iconPreview = mSettingsPreviewIcon;
    }
    
    private void setNonMicF1Key(Key key, String label, int popupResId) {
        if (key.shiftLabel != null) {
            key.codes = new int[] { key.label.charAt(0) };
            return; // leave key unmodified
        }
        key.label = label;
        key.codes = new int[] { label.charAt(0) };
        key.popupResId = popupResId;
        key.icon = mHintIcon;
        key.iconPreview = null;
    }

    public boolean isF1Key(Key key) {
        return key == mF1Key;
    }

    public static boolean hasPuncOrSmileysPopup(Key key) {
        return key.popupResId == R.xml.popup_punctuation || key.popupResId == R.xml.popup_smileys;
    }

    /**
     * @return a key which should be invalidated.
     */
    public Key onAutoCompletionStateChanged(boolean isAutoCompletion) {
        updateSpaceBarForLocale(isAutoCompletion);
        return mSpaceKey;
    }

    public boolean isLanguageSwitchEnabled() {
        return mLocale != null;
    }

    private void updateSpaceBarForLocale(boolean isAutoCompletion) {
        if (mSpaceKey == null) return;
        // If application locales are explicitly selected.
        if (mLocale != null) {
            mSpaceKey.icon = new BitmapDrawable(mRes,
                    drawSpaceBar(OPACITY_FULLY_OPAQUE, isAutoCompletion));
        } else {
            // sym_keyboard_space_led can be shared with Black and White symbol themes.
            if (isAutoCompletion) {
                mSpaceKey.icon = new BitmapDrawable(mRes,
                        drawSpaceBar(OPACITY_FULLY_OPAQUE, isAutoCompletion));
            } else {
                mSpaceKey.icon = mRes.getDrawable(R.drawable.sym_keyboard_space);
            }
        }
    }

    // Compute width of text with specified text size using paint.
    private static int getTextWidth(Paint paint, String text, float textSize, Rect bounds) {
        paint.setTextSize(textSize);
        paint.getTextBounds(text, 0, text.length(), bounds);
        return bounds.width();
    }

    // Overlay two images: mainIcon and hintIcon.
    private Bitmap drawSynthesizedSettingsHintImage(
            int width, int height, Drawable mainIcon, Drawable hintIcon) {
        if (mainIcon == null || hintIcon == null)
            return null;
        Rect hintIconPadding = new Rect(0, 0, 0, 0);
        hintIcon.getPadding(hintIconPadding);
        final Bitmap buffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(buffer);
        canvas.drawColor(mRes.getColor(R.color.latinkeyboard_transparent), PorterDuff.Mode.CLEAR);

        // Draw main icon at the center of the key visual
        // Assuming the hintIcon shares the same padding with the key's background drawable
        final int drawableX = (width + hintIconPadding.left - hintIconPadding.right
                - mainIcon.getIntrinsicWidth()) / 2;
        final int drawableY = (height + hintIconPadding.top - hintIconPadding.bottom
                - mainIcon.getIntrinsicHeight()) / 2;
        setDefaultBounds(mainIcon);
        canvas.translate(drawableX, drawableY);
        mainIcon.draw(canvas);
        canvas.translate(-drawableX, -drawableY);

        // Draw hint icon fully in the key
        hintIcon.setBounds(0, 0, width, height);
        hintIcon.draw(canvas);
        return buffer;
    }

    // Layout local language name and left and right arrow on space bar.
    private static String layoutSpaceBar(Paint paint, Locale locale, Drawable lArrow,
            Drawable rArrow, int width, int height, float origTextSize,
            boolean allowVariableTextSize) {
        final float arrowWidth = lArrow.getIntrinsicWidth();
        final float arrowHeight = lArrow.getIntrinsicHeight();
        final float maxTextWidth = width - (arrowWidth + arrowWidth);
        final Rect bounds = new Rect();

        // Estimate appropriate language name text size to fit in maxTextWidth.
        String language = LanguageSwitcher.toTitleCase(locale.getDisplayLanguage(locale), locale);
        int textWidth = getTextWidth(paint, language, origTextSize, bounds);
        // Assuming text width and text size are proportional to each other.
        float textSize = origTextSize * Math.min(maxTextWidth / textWidth, 1.0f);

        final boolean useShortName;
        if (allowVariableTextSize) {
            textWidth = getTextWidth(paint, language, textSize, bounds);
            // If text size goes too small or text does not fit, use short name
            useShortName = textSize / origTextSize < MINIMUM_SCALE_OF_LANGUAGE_NAME
                    || textWidth > maxTextWidth;
        } else {
            useShortName = textWidth > maxTextWidth;
            textSize = origTextSize;
        }
        if (useShortName) {
            language = LanguageSwitcher.toTitleCase(locale.getLanguage(), locale);
            textWidth = getTextWidth(paint, language, origTextSize, bounds);
            textSize = origTextSize * Math.min(maxTextWidth / textWidth, 1.0f);
        }
        paint.setTextSize(textSize);

        // Place left and right arrow just before and after language text.
        final float baseline = height * SPACEBAR_LANGUAGE_BASELINE;
        final int top = (int)(baseline - arrowHeight);
        final float remains = (width - textWidth) / 2;
        lArrow.setBounds((int)(remains - arrowWidth), top, (int)remains, (int)baseline);
        rArrow.setBounds((int)(remains + textWidth), top, (int)(remains + textWidth + arrowWidth),
                (int)baseline);

        return language;
    }

    private Bitmap drawSpaceBar(int opacity, boolean isAutoCompletion) {
        final int width = mSpaceKey.width;
        final int height = mSpaceIcon.getIntrinsicHeight();
        final Bitmap buffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(buffer);
        canvas.drawColor(mRes.getColor(R.color.latinkeyboard_transparent), PorterDuff.Mode.CLEAR);

        // If application locales are explicitly selected.
        if (mLocale != null) {
            final Paint paint = new Paint();
            paint.setAlpha(opacity);
            paint.setAntiAlias(true);
            paint.setTextAlign(Align.CENTER);

            final boolean allowVariableTextSize = true;
            Locale locale = mLanguageSwitcher.getInputLocale();
            //Log.i(TAG, "input locale: " + locale);
            final String language = layoutSpaceBar(paint, locale,
                    mButtonArrowLeftIcon, mButtonArrowRightIcon, width, height,
                    getTextSizeFromTheme(android.R.style.TextAppearance_Small, 14),
                    allowVariableTextSize);

            // Draw language text with shadow
            final int shadowColor = mRes.getColor(R.color.latinkeyboard_bar_language_shadow_white);
            final float baseline = height * SPACEBAR_LANGUAGE_BASELINE;
            final float descent = paint.descent();
            paint.setColor(shadowColor);
            canvas.drawText(language, width / 2, baseline - descent - 1, paint);
            paint.setColor(mRes.getColor(R.color.latinkeyboard_dim_color_white));

            canvas.drawText(language, width / 2, baseline - descent, paint);

            // Put arrows that are already layed out on either side of the text
            if (mLanguageSwitcher.getLocaleCount() > 1) {
                mButtonArrowLeftIcon.draw(canvas);
                mButtonArrowRightIcon.draw(canvas);
            }
        }

        // Draw the spacebar icon at the bottom
        if (isAutoCompletion) {
            final int iconWidth = width * SPACE_LED_LENGTH_PERCENT / 100;
            final int iconHeight = mSpaceAutoCompletionIndicator.getIntrinsicHeight();
            int x = (width - iconWidth) / 2;
            int y = height - iconHeight;
            mSpaceAutoCompletionIndicator.setBounds(x, y, x + iconWidth, y + iconHeight);
            mSpaceAutoCompletionIndicator.draw(canvas);
        } else {
            final int iconWidth = mSpaceIcon.getIntrinsicWidth();
            final int iconHeight = mSpaceIcon.getIntrinsicHeight();
            int x = (width - iconWidth) / 2;
            int y = height - iconHeight;
            mSpaceIcon.setBounds(x, y, x + iconWidth, y + iconHeight);
            mSpaceIcon.draw(canvas);
        }
        return buffer;
    }

    private int getSpacePreviewWidth() {
        final int width = Math.min(
        		Math.max(mSpaceKey.width, (int)(getMinWidth() * SPACEBAR_POPUP_MIN_RATIO)), 
        		(int)(getScreenHeight() * SPACEBAR_POPUP_MAX_RATIO));
        return width;
    }
    
    public int getLanguageChangeDirection() {
        if (mSpaceKey == null || mLanguageSwitcher.getLocaleCount() < 2
                || Math.abs(mSpaceDragLastDiff) < getSpacePreviewWidth() * SPACEBAR_DRAG_THRESHOLD) {
            return 0; // No change
        }
        return mSpaceDragLastDiff > 0 ? 1 : -1;
    }

    public void setLanguageSwitcher(LanguageSwitcher switcher, boolean isAutoCompletion) {
        mLanguageSwitcher = switcher;
        Locale locale = mLanguageSwitcher.getLocaleCount() > 0
                ? mLanguageSwitcher.getInputLocale()
                : null;
        // If the language count is 1 and is the same as the system language, don't show it.
        if (locale != null
                && mLanguageSwitcher.getLocaleCount() == 1
                && mLanguageSwitcher.getSystemLocale().getLanguage()
                   .equalsIgnoreCase(locale.getLanguage())) {
            locale = null;
        }
        mLocale = locale;
        updateSymbolIcons(isAutoCompletion);
    }

    public Locale getInputLocale() {
        return (mLocale != null) ? mLocale : mLanguageSwitcher.getSystemLocale();
    }

    void keyReleased() {
        mSpaceDragLastDiff = 0;
        mPrefLetter = 0;
        mPrefLetterX = 0;
        mPrefLetterY = 0;
        mPrefDistance = Integer.MAX_VALUE;
        if (mSpaceKey != null) {
	    //XXX mSpaceKey probably eliminate
            mSpaceKey.iconPreview = mSpacePreviewIcon;
            mSpaceKey.iconPreview.invalidateSelf();
        }
    }

    /**
     * Does the magic of locking the touch gesture into the spacebar when
     * switching input languages.
     */
    boolean isInside(LatinKey key, int x, int y) {
        final int code = key.codes[0];
        if (code == KEYCODE_SHIFT ||
                code == KEYCODE_DELETE) {
        	// Adjust target area for these keys
            y -= key.height / 10;
            if (code == KEYCODE_SHIFT) {
            	if (key.x == 0) {
            		x += key.width / 6;  // left shift
            	} else {
            		x -= key.width / 6;  // right shift
            	}
            }
            if (code == KEYCODE_DELETE) x -= key.width / 6;
        } else if (code == LatinIME.ASCII_SPACE) {
            y += LatinKeyboard.sSpacebarVerticalCorrection;
        }

        return key.isInsideSuper(x, y);
    }

    private boolean inPrefList(int code, int[] pref) {
        if (code < pref.length && code >= 0) return pref[code] > 0;
        return false;
    }

    private int distanceFrom(Key k, int x, int y) {
        if (y > k.y && y < k.y + k.height) {
            return Math.abs(k.x + k.width / 2 - x);
        } else {
            return Integer.MAX_VALUE;
        }
    }

    @Override
    public int[] getNearestKeys(int x, int y) {
            // Avoid dead pixels at edges of the keyboard
            return super.getNearestKeys(Math.max(0, Math.min(x, getMinWidth() - 1)),
                    Math.max(0, Math.min(y, getHeight() - 1)));
    }

    private int indexOf(int code) {
        List<Key> keys = getKeys();
        int count = keys.size();
        for (int i = 0; i < count; i++) {
            if (keys.get(i).codes[0] == code) return i;
        }
        return -1;
    }

    private int getTextSizeFromTheme(int style, int defValue) {
        TypedArray array = mContext.getTheme().obtainStyledAttributes(
                style, new int[] { android.R.attr.textSize });
        int resId = array.getResourceId(0, 0);
        if (resId >= array.length()) {
            Log.i(TAG, "getTextSizeFromTheme error: resId " + resId + " > " + array.length());
            return defValue;
        }
        int textSize = array.getDimensionPixelSize(resId, defValue);
        return textSize;
    }

    // TODO LatinKey could be static class
    class LatinKey extends Key {

        // functional normal state (with properties)
        private final int[] KEY_STATE_FUNCTIONAL_NORMAL = {
                android.R.attr.state_single
        };

        // functional pressed state (with properties)
        private final int[] KEY_STATE_FUNCTIONAL_PRESSED = {
                android.R.attr.state_single,
                android.R.attr.state_pressed
        };

        public LatinKey(Resources res, Keyboard.Row parent, int x, int y,
                XmlResourceParser parser) {
            super(res, parent, x, y, parser);
        }

        // sticky is used for shift key.  If a key is not sticky and is modifier,
        // the key will be treated as functional.
        private boolean isFunctionalKey() {
            return !sticky && modifier;
        }

        /**
         * Overriding this method so that we can reduce the target area for certain keys.
         */
        @Override
        public boolean isInside(int x, int y) {
            // TODO This should be done by parent.isInside(this, x, y)
            // if Key.parent were protected.
            boolean result = LatinKeyboard.this.isInside(this, x, y);
            return result;
        }

        boolean isInsideSuper(int x, int y) {
            return super.isInside(x, y);
        }

        @Override
        public int[] getCurrentDrawableState() {
            if (isFunctionalKey()) {
                if (pressed) {
                    return KEY_STATE_FUNCTIONAL_PRESSED;
                } else {
                    return KEY_STATE_FUNCTIONAL_NORMAL;
                }
            }
            return super.getCurrentDrawableState();
        }

        @Override
        public int squaredDistanceFrom(int x, int y) {
            // We should count vertical gap between rows to calculate the center of this Key.
            final int verticalGap = LatinKeyboard.this.mVerticalGap;
            final int xDist = this.x + width / 2 - x;
            final int yDist = this.y + (height + verticalGap) / 2 - y;
            return xDist * xDist + yDist * yDist;
        }
    }
}
