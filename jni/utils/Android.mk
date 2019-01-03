LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

### libnativehelper ###
LIBNATIVEHELPER_DIR := libnativehelper
include $(CLEAR_VARS)
LOCAL_MODULE    := libnativehelper
LOCAL_SRC_FILES := ../../../native/$(LIBNATIVEHELPER_DIR)/obj/local/$(TARGET_ARCH_ABI)/libnativehelper.so
LOCAL_EXPORT_C_INCLUDES :=  ../native/$(LIBNATIVEHELPER_DIR)/include/nativehelper
include $(PREBUILT_SHARED_LIBRARY)


include $(CLEAR_VARS)

# This is the target being built.
LOCAL_MODULE:= libfilecoreutils

LOCAL_LDLIBS := -L$(TARGET_OUT) -llog
ifneq ($(TARGET_ARCH_ABI),x86)
LOCAL_LDLIBS += -fuse-ld=bfd
endif

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

LOCAL_SHARED_LIBRARIES := libnativehelper

include $(BUILD_ANDROID_LIBS)
include $(BUILD_SHARED_LIBRARY)
