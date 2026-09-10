package org.telegram.ui.Components;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;

// LoogriGram: the cast button, with nothing behind it.
//
// This extended androidx.mediarouter's MediaRouteButton, which discovers Cast
// receivers and shows the route picker - the one place a user could start
// casting. Both the picker and the discovery are Google Cast, so the button has
// nothing to offer and is permanently hidden.
//
// It stays as a View subclass because PhotoViewer and AudioPlayerAlert each
// declare a field of this type and construct an anonymous subclass overriding
// stateUpdated, so the type and that hook have to exist for them to compile.
// Being GONE from construction means neither ever lays it out.
//
// The two themed dialog fragments that used to live here went with mediarouter;
// they only re-skinned its chooser and controller dialogs.
public class CastMediaRouteButton extends View {

    public CastMediaRouteButton(@NonNull Context context) {
        super(context);
        setVisibility(GONE);
    }

    /** Never connected: there is no route to connect to. */
    public boolean isConnected() {
        return false;
    }

    /** Overridden by both call sites; never invoked, since the state cannot change. */
    public void stateUpdated(boolean connected) {
    }
}
