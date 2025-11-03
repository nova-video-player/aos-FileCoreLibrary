ifeq (,$(NDK_APP_ABI))
APP_ABI := armeabi-v7a
else
APP_ABI := $(filter-out armeabi,$(NDK_APP_ABI))
endif
APP_STL := c++_static
APP_PLATFORM := android-21
