package org.telegram.messenger;

import android.content.Context;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * LoogriGram: rendering an amount of money as text.
 *
 * This lived in BillingController, which was Google Play Billing and is going
 * away. Formatting a price has nothing to do with buying anything: a message
 * that merely mentions a gift, a star amount or an invoice total still has to
 * render, and the statistics charts label their axes with it. So the currency
 * half moved here, where nothing about it is named after a removed feature,
 * and the logic itself is upstream's, unchanged.
 *
 * The exponent table comes from an asset Telegram ships - currencies.json,
 * mapping a currency to how many digits it keeps after the point - and it is
 * read once, on first use.
 */
public class CurrencyFormat {

    private static final String CURRENCY_FILE = "currencies.json";
    private static final String CURRENCY_EXP = "exp";

    private static final Map<String, Integer> currencyExpMap = new HashMap<>();
    private static NumberFormat currencyInstance;

    private CurrencyFormat() {
    }

    public static String format(long amount, String currency) {
        return format(amount, currency, getExp(currency));
    }

    public static String format(long amount, String currency, int exp) {
        return format(amount, currency, exp, false);
    }

    public static String format(long amount, String currency, int exp, boolean rounded) {
        if (currency == null || currency.isEmpty()) {
            return String.valueOf(amount);
        }
        if ("TON".equalsIgnoreCase(currency)) {
            return "TON " + (amount / 1_000_000_000.0);
        }
        if ("XTR".equalsIgnoreCase(currency)) {
            return "XTR " + LocaleController.formatNumber(amount, ',');
        }
        Currency cur = Currency.getInstance(currency);
        if (cur != null) {
            if (currencyInstance == null) {
                currencyInstance = NumberFormat.getCurrencyInstance();
            }
            currencyInstance.setCurrency(cur);
            if (rounded) {
                currencyInstance.setMaximumFractionDigits(0);
                currencyInstance.setMinimumFractionDigits(0);
                return currencyInstance.format(Math.round(amount / Math.pow(10, exp)));
            }
            final int defaultFractionDigits = cur.getDefaultFractionDigits();
            currencyInstance.setMinimumFractionDigits(defaultFractionDigits);
            currencyInstance.setMaximumFractionDigits(defaultFractionDigits);
            return currencyInstance.format(amount / Math.pow(10, exp));
        }
        return amount + " " + currency;
    }

    /** How many digits this currency keeps after the point. */
    public static int getExp(String currency) {
        loadExponents();
        final Integer exp = currencyExpMap.get(currency);
        return exp == null ? 0 : exp;
    }

    private static void loadExponents() {
        if (!currencyExpMap.isEmpty()) {
            return;
        }
        try {
            final Context context = ApplicationLoader.applicationContext;
            final InputStream in = context.getAssets().open(CURRENCY_FILE);
            // read to the end rather than trusting available(), which is not
            // required to report the whole asset
            final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            final byte[] chunk = new byte[16 * 1024];
            int n;
            while ((n = in.read(chunk)) > 0) {
                buffer.write(chunk, 0, n);
            }
            in.close();
            final JSONObject object = new JSONObject(new String(buffer.toByteArray(), "UTF-8"));
            final Iterator<String> it = object.keys();
            while (it.hasNext()) {
                final String key = it.next();
                final JSONObject currency = object.optJSONObject(key);
                if (currency != null) {
                    currencyExpMap.put(key, currency.optInt(CURRENCY_EXP));
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }
}
