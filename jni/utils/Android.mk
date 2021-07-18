LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

### libnativehelper ###
LIBNATIVEHELPER_DIR := libnativehelper
include $(CLEAR_VARS)
LOCAL_MODULE    := libnvpnativehelper
LOCAL_SRC_FILES := ../../../native/$(LIBNATIVEHELPER_DIR)/obj/local/$(TARGET_ARCH_ABI)/libnvpnativehelper.so
LOCAL_EXPORT_C_INCLUDES :=  ../native/$(LIBNATIVEHELPER_DIR)/include/nativehelper
include $(PREBUILT_SHARED_LIBRARY)


include $(CLEAR_VARS)

# This is the target being built.
LOCAL_MODULE:= libfilecoreutils

LOCAL_LDLIBS := -L$(TARGET_OUT) -llog

# All of the source files that we will compile.
LOCAL_SRC_FILES:= \
    filecoreutils.cpp \
    pingttl2.c \
    JNIPing.c

ifeq ($(TARGET_ARCH),arm)
LOCAL_ARM_MODE := arm
LOCAL_SRC_FILES += sendfile64.S
LOCAL_CFLAGS += -DCONFIG_ARM
endif

LOCAL_SHARED_LIBRARIES := libnvpnativehelper

$(warning libfilecorelibrary local module: $(LOCAL_MODULE))
$(warning libfilecorelibrary target_out: $(TARGET_OUT))
$(warning libfilecorelibrary local_ldlibs: $(LOCAL_LDLIBS))
$(warning libfilecorelibrary local_cflags: $(LOCAL_CFLAGS))
$(warning libfilecorelibrary local_shared_libraries: $(LOCAL_SHARED_LIBRARIES))
$(warning libfilecorelibrary build_android_libs: $(BUILD_ANDROID_LIBS))
$(warning libfilecorelibrary build_shared_library: $(BUILD_SHARED_LIBRARY))

include $(BUILD_ANDROID_LIBS)
include $(BUILD_SHARED_LIBRARY)
