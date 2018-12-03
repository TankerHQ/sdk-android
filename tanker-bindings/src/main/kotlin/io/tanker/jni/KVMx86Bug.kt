package io.tanker.jni

import android.os.Build

class KVMx86Bug {
    companion object {
        private fun isEmulator(): Boolean {
            return (Build.FINGERPRINT?.startsWith("generic") == true
                    || Build.FINGERPRINT?.startsWith("unknown") == true
                    || Build.MODEL?.contains("google_sdk") == true
                    || Build.MODEL?.contains("Emulator") == true
                    || Build.MODEL?.contains("Android SDK built for x86") == true
                    || Build.MANUFACTURER?.contains("Genymotion") == true
                    || Build.BRAND?.startsWith("generic")  == true && Build.DEVICE?.startsWith("generic")  == true
                    || "google_sdk" == Build.PRODUCT)
        }

        fun hasBug(): Boolean {
            if (!isEmulator())
                return false

            System.loadLibrary(TANKER_BINDINGS_JNI_LIB)
            return isKVMx86()
        }
    }
}
