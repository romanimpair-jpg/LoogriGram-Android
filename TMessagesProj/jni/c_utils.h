#ifndef log_h
#define log_h

#include <android/log.h>
#include <jni.h>

#define LOG_TAG "tmessages_native"
#ifndef LOG_DISABLED
// LoogriGram: libtgvoip/logging.h defines these first in the voip translation
// units, and this header has always overridden them there. The #undefs say
// that outright - the behaviour is unchanged, the redefinition warnings go.
#undef LOGI
#undef LOGD
#undef LOGE
#undef LOGV
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__)
#else
#define LOGI(...)
#define LOGD(...)
#define LOGE(...)
#define LOGV(...)
#endif

#ifndef MAX
#define MAX(x, y) ((x) > (y)) ? (x) : (y)
#endif
#ifndef MIN
#define MIN(x, y) ((x) < (y)) ? (x) : (y)
#endif

#endif
