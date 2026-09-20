package org.telegram.messenger;


import java.util.ArrayList;

// LoogriGram: no Google Play Billing, and nothing left worth keeping.
//
// The currency formatting that used to justify this file has moved to
// CurrencyFormat, so what remains is the purchase path: a client that never
// connects, a readiness flag that is always false, and listeners that are
// never run. Its last eleven callers are all inside the premium and Stars
// screens, which go next; this file goes with them.
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

    // LoogriGram: the currency formatting moved to CurrencyFormat, which is
    // not named after a removed feature and is what the fifty-odd callers
    // actually wanted. Nothing about rendering a price needs Play Billing.




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
