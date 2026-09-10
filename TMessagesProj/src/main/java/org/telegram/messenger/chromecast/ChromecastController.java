package org.telegram.messenger.chromecast;

import java.io.File;

// LoogriGram: casting is gone, and this is what is left of the controller.
//
// Chromecast is Google Cast: play-services-cast-framework talks to the
// receiver, and there is no non-Google implementation of the protocol to swap
// in - so rather than a partial port, the feature is off and this keeps the
// shape its callers expect. MediaController, PhotoViewer and AudioPlayerAlert
// between them reference this API in about a hundred places, all of them
// already guarded on isCasting() or on a null media description, so reporting
// "not casting" leaves every one of those paths taking the branch it already
// takes when no receiver is connected.
//
// Kept as a stub rather than deleted for exactly that reason: deleting it would
// mean editing three files of 4k, 24k and 6k lines to remove branches that are
// inert anyway, which is a much larger diff to re-audit on every rebase for no
// behavioural difference.
public class ChromecastController {

    private static ChromecastController instance;

    public static ChromecastController getInstance() {
        if (instance == null) {
            instance = new ChromecastController();
        }
        return instance;
    }

    private ChromecastController() {}

    public boolean isCasting() {
        return false;
    }

    public void setCurrentMediaAndCastIfNeeded(ChromecastMediaVariations newMedia) {
    }

    public boolean isPlaying(ChromecastMediaVariations media) {
        return false;
    }

    /**
     * Upstream copied the cover into the file server's directory and returned
     * the path it would be served under. There is no file server now, so there
     * is no path; callers only use the result to build a URL for the receiver.
     */
    public String setCover(File file) {
        return null;
    }

    // Pure comparisons, no Google involved - kept as upstream wrote them in
    // case anything still asks whether two descriptions match.
    public static boolean eq(ChromecastMediaVariations a, ChromecastMediaVariations b) {
        if (a == null || b == null) {
            return a == b;
        }
        if (a.getVariationsCount() != b.getVariationsCount()) {
            return false;
        }
        for (int i = 0; i < a.getVariationsCount(); ++i) {
            if (!eq(a.getVariation(i), b.getVariation(i))) {
                return false;
            }
        }
        return true;
    }

    public static boolean eq(ChromecastMedia a, ChromecastMedia b) {
        if (a == null || b == null) {
            return a == b;
        }
        if (a.width != b.width || a.height != b.height) {
            return false;
        }
        if (a.mimeType == null ? b.mimeType != null : !a.mimeType.equals(b.mimeType)) {
            return false;
        }
        if (a.externalPath == null ? b.externalPath != null : !a.externalPath.equals(b.externalPath)) {
            return false;
        }
        return a.internalUri == null ? b.internalUri == null : a.internalUri.equals(b.internalUri);
    }
}
