package org.telegram.messenger;

// LoogriGram: no on-device language detection.
//
// This wrapped ML Kit's language identifier, the one Google library here that
// genuinely worked without Play Services - its model ships inside the APK and
// nothing is sent anywhere. It goes because auto-translation is not wanted in
// this build, so the only consumer of the detection was a feature being
// removed, and keeping about a megabyte of Google bytecode to serve it would
// have made "no Google classes in the APK" a weaker thing to verify.
//
// hasSupport() reporting false is a path upstream already handles: it is what
// every caller checks before offering to detect anything, and TranslateController
// treats an undetectable language as simply not offering a translation.
public class LanguageDetector {
    public interface StringCallback {
        void run(String str);
    }
    public interface ExceptionCallback {
        void run(Exception e);
    }

    public static boolean hasSupport() {
        return false;
    }

    public static void detectLanguage(String text, StringCallback onSuccess, ExceptionCallback onFail) {
        detectLanguage(text, onSuccess, onFail, false);
    }

    public static void detectLanguage(String text, StringCallback onSuccess, ExceptionCallback onFail, boolean initializeFirst) {
        // Answered rather than dropped: callers pass a failure callback and some
        // of them use it to move on, so staying silent would leave them waiting.
        if (onFail != null) {
            onFail.run(new UnsupportedOperationException("language detection removed"));
        }
    }
}
