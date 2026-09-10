package org.telegram.messenger;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.Map;

// LoogriGram: no Google Play Billing.
//
// This class is kept, gutted, for one reason: formatCurrency. Sixty-five call
// sites use it to render a price - gift costs, Stars amounts, invoice totals,
// chart labels - and none of them have anything to do with buying anything from
// Google. Those, plus getCurrencyExp behind them, are the whole reason the file
// still exists; the currency logic is untouched and was never Google's.
//
// Everything that talked to Play is gone: the BillingClient itself, the product
// query and its details, launchBillingFlow, the purchase listeners and the
// consume/acknowledge paths. isReady() reports false and billingClientEmpty is
// true, which is how upstream describes a device where Play Billing does not
// exist - and every purchase path is already unreachable before this, since
// premiumPurchaseBlocked(), IS_BILLING_UNAVAILABLE and useInvoiceBilling() all
// say no.
public class BillingController {

    public final static String PREMIUM_PRODUCT_ID = "telegram_premium";

    /**
     * Upstream sets this false once the billing client reports a usable
     * connection. It stays true here, which is what makes
     * BuildVars.useInvoiceBilling() true and routes everything that remains
     * through Telegram's own invoices rather than Google's.
     */
    public static boolean billingClientEmpty = true;

    private static BillingController instance;

    private final Map<String, Integer> currencyExpMap = new HashMap<>();

    public static BillingController getInstance() {
        if (instance == null) {
            instance = new BillingController();
        }
        return instance;
    }

    private BillingController() {
    }

    public void setOnCanceled(Runnable onCanceled) {
        // No purchase can be started, so none can be cancelled.
    }

    public String getLastPremiumTransaction() {
        return null;
    }

    public String getLastPremiumToken() {
        return null;
    }

    /* Currency formatting - the reason this class survives. Unchanged. */

    public String formatCurrency(long amount, String currency) {
        return formatCurrency(amount, currency, getCurrencyExp(currency));
    }

    public String formatCurrency(long amount, String currency, int exp) {
        return formatCurrency(amount, currency, exp, false);
    }

    private static NumberFormat currencyInstance;
    public String formatCurrency(long amount, String currency, int exp, boolean rounded) {
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

    @SuppressWarnings("ConstantConditions")
    public int getCurrencyExp(String currency) {
        BillingUtilities.extractCurrencyExp(currencyExpMap);
        return currencyExpMap.getOrDefault(currency, 0);
    }

    /* Everything below is inert. */

    public void startConnection() {
        // Nothing to connect to.
    }

    public boolean isReady() {
        return false;
    }

    private final ArrayList<Runnable> setupListeners = new ArrayList<>();

    /**
     * Upstream drains these when the billing client finishes setting up. That
     * never happens, so a listener registered here is never run - which is the
     * correct outcome: each one goes on to start a purchase.
     */
    public void whenSetuped(Runnable listener) {
        setupListeners.add(listener);
    }

    /**
     * Named a Play Billing response code for logs. No such code can arrive now,
     * so rather than carry Google's constant table this answers the only true
     * thing about billing here.
     */
    public static String getResponseCodeString(int code) {
        return "BILLING_UNAVAILABLE";
    }
}
