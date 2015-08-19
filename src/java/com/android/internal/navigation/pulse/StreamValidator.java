
package com.android.internal.navigation.pulse;

public interface StreamValidator {
    public void analyze(byte[] data);
    public boolean isValidStream();
    public void reset();
    public void addCallbacks(Callbacks callbacks);
    public void removeCallbacks(Callbacks callbacks);

    public interface Callbacks {
        public void onStreamAnalyzed(boolean isValid);
    }
}
