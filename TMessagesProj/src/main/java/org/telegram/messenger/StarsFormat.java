package org.telegram.messenger;

import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;

import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.Components.ColoredImageSpan;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * LoogriGram: writing an amount of Stars, TON or diamonds as text.
 *
 * These lived as static members of StarsIntroActivity - a screen, and one of
 * the screens the money removal is working towards deleting. Nothing about
 * them belongs to a screen: they take a CharSequence and swap a currency
 * placeholder for the glyph that stands for it, or turn a StarsAmount into
 * digits. Ordinary message rendering needs that and will go on needing it,
 * because a received gift or a Stars transfer is still described in text by
 * MessageObject, ChatMessageCell and the chat list even though this build
 * offers no way to send or buy one. The same reasoning moved the currency
 * half of Play Billing out to CurrencyFormat; see that class.
 *
 * The logic is upstream's, unchanged. The one edit is the currency constant,
 * which was read from StarsController - that controller is part of what goes,
 * so the string is kept here instead. It is the TL currency code for Stars,
 * not a setting, and it appears in server-sent strings as a placeholder.
 */
public class StarsFormat {

    /** Upstream's StarsController.currency, kept here so this outlives it. */
    public static final String currency = "XTR";

    private StarsFormat() {
    }

    public static SpannableStringBuilder replaceStars(TL_stars.StarsAmount amount, CharSequence cs) {
        return replaceStars(amount instanceof TL_stars.TL_starsTonAmount, cs, 1.13f);
    }

    public static SpannableStringBuilder replaceStars(boolean ton, CharSequence cs) {
        return replaceStars(ton, cs, 1.13f);
    }

    public static SpannableStringBuilder replaceStars(CharSequence cs) {
        return replaceStars(cs, 1.13f);
    }

    public static SpannableStringBuilder replaceStars(boolean ton, CharSequence cs, final float scale) {
        return replaceStars(ton, cs, scale, null);
    }

    public static SpannableStringBuilder replaceStars(TL_stars.StarsAmount amount, CharSequence cs, final float scale) {
        return replaceStars(amount instanceof TL_stars.TL_starsTonAmount, cs, scale, null);
    }

    public static SpannableStringBuilder replaceStars(CharSequence cs, final float scale) {
        return replaceStars(cs, scale, null);
    }

    public static SpannableStringBuilder replaceStars(boolean ton, CharSequence cs, final float scale, ColoredImageSpan[] cache) {
        return replaceStars(ton, cs, scale, cache, 0, 0, 1.0f);
    }

    public static SpannableStringBuilder replaceStars(TL_stars.StarsAmount amount, CharSequence cs, final float scale, ColoredImageSpan[] cache) {
        return replaceStars(amount instanceof TL_stars.TL_starsTonAmount, cs, scale, cache, 0, 0, 1.0f);
    }

    public static SpannableStringBuilder replaceStars(CharSequence cs, final float scale, ColoredImageSpan[] cache) {
        return replaceStars(cs, scale, cache, 0, 0, 1.0f);
    }

    public static SpannableStringBuilder replaceStars(CharSequence cs, final float scale, ColoredImageSpan[] cache, final float tx, final float ty, final float sx) {
        return replaceStars(false, cs, scale, cache, tx, ty, sx);
    }

    public static SpannableStringBuilder replaceStars(TL_stars.StarsAmount amount, CharSequence cs, final float scale, ColoredImageSpan[] cache, final float tx, final float ty, final float sx) {
        return replaceStars(amount instanceof TL_stars.TL_starsTonAmount, cs, scale, cache, tx, ty, sx);
    }

    public static SpannableStringBuilder replaceStars(boolean ton, CharSequence cs, final float scale, ColoredImageSpan[] cache, final float tx, final float ty, final float sx) {
        if (cs == null) return null;
        SpannableStringBuilder ssb;
        if (!(cs instanceof SpannableStringBuilder)) {
            ssb = new SpannableStringBuilder(cs);
        } else {
            ssb = (SpannableStringBuilder) cs;
        }
        final String symbol = ton ? "TON" : "⭐";
        SpannableString spacedStar = new SpannableString(symbol + " ");
        ColoredImageSpan span;
        if (cache != null && cache[0] != null) {
            span = cache[0];
        } else {
            span = new ColoredImageSpan(ton ? R.drawable.mini_gram_72 : R.drawable.msg_premium_liststar);
            if (cache != null) {
                cache[0] = span;
            }
        }
        span.translate(tx, ty);
        span.spaceScaleX = sx;
        if (ton) {
            span.setScale(scale * 0.2f, scale * 0.2f);
        } else {
            span.setScale(scale, scale);
        }
        spacedStar.setSpan(span, 0, spacedStar.length() - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        AndroidUtilities.replaceMultipleCharSequence("⭐️", ssb, "⭐");
        AndroidUtilities.replaceMultipleCharSequence("⭐ ", ssb, "⭐");
        AndroidUtilities.replaceMultipleCharSequence("⭐", ssb, spacedStar);
        AndroidUtilities.replaceMultipleCharSequence(currency + " ", ssb, currency);
        AndroidUtilities.replaceMultipleCharSequence(currency, ssb, spacedStar);
        return ssb;
    }

    public static SpannableStringBuilder replaceDiamond(CharSequence cs) {
        return replaceDiamond(cs, 0.9f, null, 0, 0, 1.0f);
    }

    public static SpannableStringBuilder replaceDiamond(CharSequence cs, final float scale) {
        return replaceDiamond(cs, scale, null, 0, 0, 1.0f);
    }

    public static SpannableStringBuilder replaceDiamond(CharSequence cs, final float scale, ColoredImageSpan[] cache, final float tx, final float ty, float sx) {
        if (cs == null) return null;
        SpannableStringBuilder ssb;
        if (!(cs instanceof SpannableStringBuilder)) {
            ssb = new SpannableStringBuilder(cs);
        } else {
            ssb = (SpannableStringBuilder) cs;
        }
        SpannableString spacedStar = new SpannableString("💎 ");
        ColoredImageSpan span;
        if (cache != null && cache[0] != null) {
            span = cache[0];
        } else {
            span = new ColoredImageSpan(R.drawable.diamond);
            if (cache != null) {
                cache[0] = span;
            }
        }
        span.recolorDrawable = false;
        span.translate(tx, ty);
        span.spaceScaleX = sx;
        span.setScale(scale, scale);
        spacedStar.setSpan(span, 0, spacedStar.length() - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        AndroidUtilities.replaceMultipleCharSequence("💎️", ssb, "💎");
        AndroidUtilities.replaceMultipleCharSequence("💎 ", ssb, "💎");
        AndroidUtilities.replaceMultipleCharSequence("💎", ssb, spacedStar);
        AndroidUtilities.replaceMultipleCharSequence(currency + " ", ssb, currency);
        AndroidUtilities.replaceMultipleCharSequence(currency, ssb, spacedStar);
        return ssb;
    }

    public static SpannableStringBuilder replaceStars(CharSequence cs, ColoredImageSpan[] spanRef) {
        return replaceStars(false, cs, spanRef);
    }

    public static SpannableStringBuilder replaceStars(boolean ton, CharSequence cs, ColoredImageSpan[] spanRef) {
        if (cs == null) return null;
        SpannableStringBuilder ssb;
        if (!(cs instanceof SpannableStringBuilder)) {
            ssb = new SpannableStringBuilder(cs);
        } else {
            ssb = (SpannableStringBuilder) cs;
        }
        ColoredImageSpan span;
        if (spanRef != null && spanRef[0] != null) {
            span = spanRef[0];
        } else {
            span = new ColoredImageSpan(ton ? R.drawable.mini_gram_72 : R.drawable.msg_premium_liststar);
            span.setScale(ton ? 0.222f : 1.13f, ton ? 0.222f : 1.13f);
        }
        if (spanRef != null) {
            spanRef[0] = span;
        }
        SpannableString spacedStar = new SpannableString("⭐ ");
        spacedStar.setSpan(span, 0, spacedStar.length() - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        AndroidUtilities.replaceMultipleCharSequence("⭐️", ssb, "⭐");
        AndroidUtilities.replaceMultipleCharSequence("⭐ ", ssb, "⭐");
        AndroidUtilities.replaceMultipleCharSequence("⭐", ssb, spacedStar);
        AndroidUtilities.replaceMultipleCharSequence(currency + " ", ssb, currency);
        AndroidUtilities.replaceMultipleCharSequence(currency, ssb, spacedStar);
        return ssb;
    }

    public static SpannableStringBuilder replaceStarsWithPlain(CharSequence cs, float scale) {
        return replaceStarsWithPlain(cs, scale, null);
    }

    public static SpannableStringBuilder replaceStarsWithPlain(boolean ton, CharSequence cs, float scale) {
        return replaceStarsWithPlain(ton, cs, scale, null);
    }

    public static SpannableStringBuilder replaceStarsWithPlain(TL_stars.StarsAmount amount, CharSequence cs, float scale) {
        return replaceStarsWithPlain(amount instanceof TL_stars.TL_starsTonAmount, cs, scale, null);
    }

    public static SpannableStringBuilder replaceStarsWithPlain(CharSequence cs, float scale, ColoredImageSpan[] spanArr) {
        return replaceStarsWithPlain(false, cs, scale, spanArr);
    }

    public static SpannableStringBuilder replaceStarsWithPlain(TL_stars.StarsAmount amount, CharSequence cs, float scale, ColoredImageSpan[] spanArr) {
        return replaceStarsWithPlain(amount instanceof TL_stars.TL_starsTonAmount, cs, scale, spanArr);
    }

    public static SpannableStringBuilder replaceStarsWithPlain(boolean ton, CharSequence cs, float scale, ColoredImageSpan[] spanArr) {
        if (cs == null) return null;
        SpannableStringBuilder ssb;
        if (!(cs instanceof SpannableStringBuilder)) {
            ssb = new SpannableStringBuilder(cs);
        } else {
            ssb = (SpannableStringBuilder) cs;
        }
        final String symbol = ton ? "TON" : "⭐";
        final int resId = ton ? R.drawable.mini_gram_72 : R.drawable.star_small_inner;
        SpannableString spacedStar = new SpannableString(symbol + " ");
        ColoredImageSpan span;
        if (spanArr != null && spanArr[0] != null) {
            span = spanArr[0];
        } else if (spanArr != null && spanArr.length > 0) {
            span = spanArr[0] = new ColoredImageSpan(resId);
        } else {
            span = new ColoredImageSpan(resId);
        }
        if (ton) {
            scale *= .33f;
        } else {
            span.recolorDrawable = false;
        }
        span.setScale(scale, scale);
        spacedStar.setSpan(span, 0, spacedStar.length() - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        AndroidUtilities.replaceMultipleCharSequence("⭐️", ssb, "⭐");
        AndroidUtilities.replaceMultipleCharSequence("⭐ ", ssb, "⭐");
        AndroidUtilities.replaceMultipleCharSequence("⭐", ssb, spacedStar);
        AndroidUtilities.replaceMultipleCharSequence(currency + " ", ssb, currency);
        AndroidUtilities.replaceMultipleCharSequence(currency, ssb, spacedStar);
        return ssb;
    }

    private static DecimalFormat floatFormat2;

    public static String formatTON(long ton) {
        if (floatFormat2 == null)
            floatFormat2 = new DecimalFormat("0.####", new DecimalFormatSymbols(Locale.US));
        if (ton % 1_000_000_000 != 0) {
            return floatFormat2.format(ton / 1_000_000_000.0);
        } else {
            return (ton < 0 ? "-" : "") + LocaleController.formatNumber(Math.abs(ton / 1_000_000_000L), ',');
        }
    }

    public static CharSequence formatStarsAmount(TL_stars.StarsAmount starsAmount) {
        return formatStarsAmount(starsAmount, 0.777f, ',');
    }

    private static DecimalFormat floatFormat;

    public static CharSequence formatStarsAmount(TL_stars.StarsAmount starsAmount, float relativeSize, char symbol) {
        if (floatFormat == null)
            floatFormat = new DecimalFormat("0.################", new DecimalFormatSymbols(Locale.US));
        SpannableStringBuilder ssb = new SpannableStringBuilder();
        if (starsAmount instanceof TL_stars.TL_starsTonAmount) {
            if (starsAmount.amount % 1_000_000_000 != 0) {
                String str = floatFormat.format(starsAmount.amount / 1_000_000_000.0);
                ssb.append(str);
                int index;
                if ((index = str.indexOf(".")) >= 0) {
                    ssb.setSpan(new RelativeSizeSpan(relativeSize), index, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            } else {
                ssb.append((starsAmount.negative() ? "-" : "") + LocaleController.formatNumber(Math.abs(starsAmount.amount / 1_000_000_000L), symbol));
            }
        } else {
            final long amount = starsAmount.amount + (starsAmount.nanos < 0 && starsAmount.amount > 0 ? -1 : (starsAmount.nanos > 0 && starsAmount.amount < 0 ? +1 : 0));
            final boolean negative = starsAmount.amount == 0 ? starsAmount.nanos < 0 : starsAmount.amount < 0;
            if (starsAmount.nanos != 0) {
                ssb.append((negative ? "-" : "") + LocaleController.formatNumber(Math.abs(amount), symbol));
                String str = floatFormat.format((starsAmount.nanos < 0 ? 1e9 + starsAmount.nanos : starsAmount.nanos) / 1e9d);
                int index;
                if ((index = str.indexOf(".")) >= 0) {
                    int fromIndex = ssb.length();
                    ssb.append(str.substring(index));
                    ssb.setSpan(new RelativeSizeSpan(relativeSize), fromIndex + 1, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            } else {
                ssb.append((negative ? "-" : "") + LocaleController.formatNumber(Math.abs(amount), symbol));
            }
        }
        return ssb;
    }

    public static CharSequence formatStarsAmountShort(TL_stars.StarsAmount starsAmount) {
        return formatStarsAmountShort(starsAmount, 0.777f, ' ');
    }

    public static CharSequence formatStarsAmountShort(TL_stars.StarsAmount starsAmount, float relativeSize, char symbol) {
        if (floatFormat == null)
            floatFormat = new DecimalFormat("0.################", new DecimalFormatSymbols(Locale.US));
        SpannableStringBuilder ssb = new SpannableStringBuilder();
        if (starsAmount instanceof TL_stars.TL_starsTonAmount) {
            String str = floatFormat.format(starsAmount.amount / 1_000_000_000.0);
            ssb.append(str);
            int index;
            if ((index = str.indexOf(".")) >= 0) {
                ssb.setSpan(new RelativeSizeSpan(relativeSize), index, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        } else {
            final long amount = starsAmount.amount + (starsAmount.nanos < 0 && starsAmount.amount > 0 ? -1 : (starsAmount.nanos > 0 && starsAmount.amount < 0 ? +1 : 0));
            final boolean negative = starsAmount.amount == 0 ? starsAmount.nanos < 0 : starsAmount.amount < 0;
            if (Math.abs(amount) <= 1000 && starsAmount.nanos != 0) {
                ssb.append((negative ? "-" : "") + LocaleController.formatNumber(Math.abs(amount), symbol));
                String str = floatFormat.format((starsAmount.nanos < 0 ? 1e9 + starsAmount.nanos : starsAmount.nanos) / 1e9d);
                int index;
                if ((index = str.indexOf(".")) >= 0) {
                    int fromIndex = ssb.length();
                    String part = str.substring(index);
                    if (part.length() > 1) {
                        ssb.append(part.substring(0, Math.min(part.length(), 3)));
                        ssb.setSpan(new RelativeSizeSpan(relativeSize), fromIndex + 1, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }
            } else if (starsAmount.amount <= 1000) {
                ssb.append((negative ? "-" : "") + LocaleController.formatNumber(Math.abs(amount), symbol));
            } else {
                ssb.append((negative ? "-" : "") + AndroidUtilities.formatWholeNumber((int) Math.abs(amount), 0));
            }
        }
        return ssb;
    }
}
