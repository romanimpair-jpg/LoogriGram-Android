package org.telegram.messenger.chromecast;

import android.net.Uri;

// LoogriGram: the media description, minus Google. What went is everything that
// only made sense to a Cast receiver: the gms MediaMetadata this carried,
// buildMediaInfo which wrapped it in a gms MediaInfo, and buildMetadata which
// mapped our mime types onto MEDIA_TYPE_PHOTO / _MOVIE / _MUSIC_TRACK.
//
// The rest is a plain value object, and callers still construct it through the
// Builder, so it is kept rather than deleted - see ChromecastController for why
// the cast API keeps its shape instead of being torn out of three very large
// files.
public class ChromecastMedia {
    public static final String IMAGE_JPEG = "image/jpeg";
    public static final String IMAGE_PNG = "image/png";
    public static final String VIDEO_MP4 = "video/mp4";
    public static final String APPLICATION_X_MPEG_URL = "application/x-mpegURL";

    public final String mimeType;

    public final Uri internalUri;
    public final String externalPath;

    public final int width;
    public final int height;

    public final String title;
    public final String subtitle;

    private ChromecastMedia(ChromecastMedia.Builder b) {
        this.mimeType = b.mimeType;
        this.internalUri = b.internalUri;
        this.externalPath = b.externalPath;
        this.width = b.width;
        this.height = b.height;
        this.title = b.title;
        this.subtitle = b.subtitle;
    }

    public String getExternalUri(String host) {
        return ChromecastFileServer.getUrlToSource(host, externalPath);
    }

    /* */

    public static class Builder {
        private final String mimeType;
        private final Uri internalUri;
        private final String externalPath;

        private int width;
        private int height;
        private String title;
        private String subtitle;

        private Builder(String mime, Uri internalUri, String externalPath) {
            this.mimeType = mime;
            this.internalUri = internalUri;
            this.externalPath = externalPath;
        }

        public static Builder fromUri(Uri internalUri, String externalPath, String mimeType) {
            return new Builder(mimeType, internalUri, externalPath);
        }

        public Builder setTitle(String title) {
            this.title = title;
            return this;
        }

        public Builder setSubtitle(String subtitle) {
            this.subtitle = subtitle;
            return this;
        }

        public Builder setSize(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        public ChromecastMedia build() {
            return new ChromecastMedia(this);
        }
    }
}
