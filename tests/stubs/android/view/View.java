package android.view;

import java.util.ArrayList;
import java.util.List;

/** Host test double: accessing insets before attach reproduces launch failure. */
public final class View {
    public interface OnAttachStateChangeListener {
        void onViewAttachedToWindow(View view);
        void onViewDetachedFromWindow(View view);
    }
    private final List<OnAttachStateChangeListener> listeners=new ArrayList<>();
    private boolean attached;
    private WindowInsetsController controller;
    public int requests;
    public boolean isAttachedToWindow() { return attached; }
    public WindowInsetsController getWindowInsetsController() {
        requests++;
        if(!attached)throw new IllegalStateException("Window not attached: decor/controller unavailable");
        return controller;
    }
    public void addOnAttachStateChangeListener(OnAttachStateChangeListener l) {listeners.add(l);}
    public void removeOnAttachStateChangeListener(OnAttachStateChangeListener l) {listeners.remove(l);}
    public void attach(WindowInsetsController c) {controller=c;attached=true;for(var listener:new ArrayList<>(listeners))listener.onViewAttachedToWindow(this);}
    public void detach() {attached=false;for(var listener:new ArrayList<>(listeners))listener.onViewDetachedFromWindow(this);}
}
