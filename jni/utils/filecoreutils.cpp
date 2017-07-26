#include <jni.h>

#include "JNIHelp.h"
#include "jni.h"


#ifdef CONFIG_ARM
ssize_t sendfile64(int out_fd, int in_fd, loff_t *offset, size_t count) asm ("sendfile64");
#else
#include <sys/sendfile.h>
#endif

jint sendfile_64(JNIEnv *jenv, jobject obj, jobject out, jobject in, jlong position, jlong count) 
{   
    int ret = 0;
    if (out == NULL) {
        return -1;
    }
    int outfd = jniGetFDFromFileDescriptor(jenv, out);

    if (in == NULL) {
        return -2;
    }
    int infd = jniGetFDFromFileDescriptor(jenv, in);
#ifdef CONFIG_ARM
    ret = sendfile64(outfd, infd, &position, count);
#else
    ret = sendfile(outfd, infd, (off_t *)&position, count);
#endif
    
    return ret;
}

static JNINativeMethod sMethods[] = {
    {"native_sendfile_64", "(Ljava/io/FileDescriptor;Ljava/io/FileDescriptor;JJ)I", (void*)sendfile_64},
};

jint JNI_OnLoad(JavaVM* vm, void* reserved) {

    JNIEnv* jenv = NULL;

    if (vm->GetEnv((void**) &jenv, JNI_VERSION_1_4) != JNI_OK) {
        return -1;
    }

    
    jniRegisterNativeMethods(jenv, "com/archos/filecorelibrary/ArchosFileChannel",
            sMethods, NELEM(sMethods));

    return JNI_VERSION_1_4;
}

