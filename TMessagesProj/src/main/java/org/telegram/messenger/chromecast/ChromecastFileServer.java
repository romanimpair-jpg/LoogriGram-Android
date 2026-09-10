package org.telegram.messenger.chromecast;

// LoogriGram: what remains of the local file server.
//
// Upstream ran an actual HTTP server on the phone - NanoHTTPD, 423 lines - so a
// Cast receiver on the same network could pull the media, the cover art and a
// rewritten HLS manifest back out of the app. With casting gone there is no
// receiver to serve, and running a listening socket for nobody is worth
// removing on its own account; dropping it also drops the nanohttpd dependency.
//
// The two accessors survive because ChromecastMedia and the callers still build
// URLs with them. They return null now, which is what a caller gets today
// anyway: every path to them is behind a cast session that can no longer exist.
public class ChromecastFileServer {

    public static String getUrlToSource(String host, String path) {
        return null;
    }

    public static String getHost() {
        return null;
    }
}
