LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_JAVA_LIBRARIES := org.teameos.utils

LOCAL_STATIC_JAVA_LIBRARIES := \
    android-visualizer \
    trail-drawing

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_MODULE_TAGS := optional

LOCAL_MODULE := org.teameos.navigation-internal

include $(BUILD_JAVA_LIBRARY)


include $(CLEAR_VARS)

LOCAL_JAVA_LIBRARIES := org.teameos.utils

LOCAL_STATIC_JAVA_LIBRARIES := \
    android-visualizer \
    trail-drawing

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_MODULE_TAGS := optional

LOCAL_MODULE := org.teameos.navigation-static-internal

include $(BUILD_STATIC_JAVA_LIBRARY)

