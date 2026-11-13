package edu.hitsz.sky.android;

import android.annotation.TargetApi;
import android.app.Activity;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

@TargetApi(33)
final class ModernBackNavigation {
    private ModernBackNavigation() { }
    static Runnable attach(Activity activity, Runnable navigate) {
        OnBackInvokedCallback callback = navigate::run;
        OnBackInvokedDispatcher dispatcher = activity.getOnBackInvokedDispatcher();
        dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback);
        return () -> dispatcher.unregisterOnBackInvokedCallback(callback);
    }
}
