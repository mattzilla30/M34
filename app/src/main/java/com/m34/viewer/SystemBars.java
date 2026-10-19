package com.m34.viewer;

import android.view.View;
import android.view.WindowInsetsController;

/** A window's decor/controller need not exist during Activity.onCreate. */
final class SystemBars {
    static void bind(View content,boolean light) {
        if(content.isAttachedToWindow()) {
            apply(content,light);
            return;
        }
        content.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) {
                view.removeOnAttachStateChangeListener(this);
                apply(view,light);
            }
            @Override public void onViewDetachedFromWindow(View view) { }
        });
    }

    private static void apply(View view,boolean light) {
        WindowInsetsController controller=view.getWindowInsetsController();
        if(controller==null) return;
        int mask=WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
        controller.setSystemBarsAppearance(light?mask:0,mask);
    }
}
