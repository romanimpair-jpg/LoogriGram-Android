package org.telegram.ui.Stories.recorder;

import android.content.Context;
import android.hardware.Camera;

import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SharedConfig;

// LoogriGram: this was the story camera's front-and-back view, which the
// debug menu could switch on for more devices. Stories are not posted
// here; what is left is the check InstantCameraView makes for round videos.
public class DualCameraView {

    public static boolean roundDualAvailableStatic(Context context) {
        return MessagesController.getGlobalMainSettings().getBoolean("rounddual_available", roundDualAvailableDefault(context));
    }

    public static boolean roundDualAvailableDefault(Context context) {
        return (
            SharedConfig.getDevicePerformanceClass() >= SharedConfig.PERFORMANCE_CLASS_HIGH &&
            Camera.getNumberOfCameras() > 1 &&
            SharedConfig.allowPreparingHevcPlayers() &&
            context != null && context.getPackageManager().hasSystemFeature("android.hardware.camera.concurrent")
        );
    }
}
