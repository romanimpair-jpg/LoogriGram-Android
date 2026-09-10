package org.telegram.ui;

// LoogriGram: the player-to-receiver sync layer, with the receiver removed.
//
// Upstream kept a Cast session's position, volume, speed and play state in step
// with the in-app player, listening to a gms RemoteMediaClient and pushing
// changes back to it. There is no session to sync with now, so every accessor
// reports "not casting" and every setter does nothing.
//
// This is a stub rather than a deletion because the media UI asks it questions
// constantly - isActive() alone is checked in eighteen places across PhotoViewer
// and AudioPlayerAlert, always as "is playback happening somewhere else, so
// leave the local player alone". Answering false is exactly the no-receiver
// case those branches already handle, and it keeps a very large diff out of two
// very large files. See ChromecastController for the same reasoning.
//
// Only the members something actually references are kept; the gms-typed
// getClient() and the volume-observer plumbing had no callers outside this
// class and are gone.
public class CastSync {

    public static final int TYPE_PHOTOVIEWER = 0;
    public static final int TYPE_MUSIC = 1;

    public static int type;

    public static void check(int type) {
        CastSync.type = type;
    }

    public static void stop() {
    }

    public static boolean isActive() {
        return false;
    }

    public static long getPosition() {
        return 0;
    }

    public static void seekTo(long position) {
    }

    public static void syncPosition(long position) {
    }

    public static float getVolume() {
        return 0;
    }

    public static boolean isPlaying() {
        return false;
    }

    public static void setPlaying(boolean play) {
    }

    public static void setSpeed(float speed) {
    }

    /**
     * Upstream used this to tell the UI a change had been sent to the receiver
     * but not yet acknowledged, so it could avoid fighting the incoming state.
     * Nothing is ever in flight now.
     */
    public static boolean isUpdatePending() {
        return false;
    }

    public static float getSpeed() {
        return 1f;
    }

    public static float getDeviceVolume() {
        return 0;
    }
}
