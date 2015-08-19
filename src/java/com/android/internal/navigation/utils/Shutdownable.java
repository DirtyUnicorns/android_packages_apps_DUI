
package com.android.internal.navigation.utils;

public interface Shutdownable {
    public void shutdown(Callbacks callbacks);

    public interface Callbacks {
        public void onShutdown();
    }
}
