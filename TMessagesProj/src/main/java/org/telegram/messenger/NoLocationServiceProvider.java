package org.telegram.messenger;

import android.content.Context;
import android.location.Location;

import androidx.core.util.Consumer;

// LoogriGram: replaces GoogleLocationProvider. This build cannot geolocate at
// all - the location permissions are gone from every manifest, which is the
// guarantee that matters because the OS enforces it and it is visible in the
// system app info rather than being a promise about our own code.
//
// So this provider exists only to satisfy ApplicationLoader, which calls
// init() on whatever it gets back and would fail on null. It reports no
// services and never delivers a fix. Callbacks are deliberately not invoked
// with a null Location: LocationController's consumers are written for a real
// fix, and never answering is both honest and safer than handing them a null
// to dereference. The practical effect is that anything waiting on a location
// waits forever, which only the removed send-location UI ever did.
public class NoLocationServiceProvider implements ILocationServiceProvider {

    @Override
    public void init(Context context) {
    }

    @Override
    public ILocationRequest onCreateLocationRequest() {
        return new ILocationRequest() {
            @Override
            public void setPriority(int priority) {
            }

            @Override
            public void setInterval(long interval) {
            }

            @Override
            public void setFastestInterval(long interval) {
            }
        };
    }

    @Override
    public IMapApiClient onCreateLocationServicesAPI(Context context, IAPIConnectionCallbacks connectionCallbacks, IAPIOnConnectionFailedListener failedListener) {
        return new IMapApiClient() {
            @Override
            public void connect() {
            }

            @Override
            public void disconnect() {
            }
        };
    }

    @Override
    public boolean checkServices() {
        return false;
    }

    @Override
    public void getLastLocation(Consumer<Location> callback) {
    }

    @Override
    public void requestLocationUpdates(ILocationRequest request, ILocationListener locationListener) {
    }

    @Override
    public void removeLocationUpdates(ILocationListener locationListener) {
    }

    @Override
    public void checkLocationSettings(ILocationRequest request, Consumer<Integer> callback) {
        if (callback != null) {
            callback.accept(STATUS_SETTINGS_CHANGE_UNAVAILABLE);
        }
    }
}
