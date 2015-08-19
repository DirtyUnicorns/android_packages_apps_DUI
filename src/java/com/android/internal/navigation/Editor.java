package com.android.internal.navigation;

public interface Editor {
    public abstract void onCommitChanges();
    public void changeEditMode(int mode);
    public void prepareToReorient();
    public void reorient(boolean isLandscape);
}
