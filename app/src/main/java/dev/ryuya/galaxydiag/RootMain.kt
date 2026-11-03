package dev.ryuya.galaxydiag

import android.os.Build
import android.os.IBinder
import android.system.Os
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/** Short-lived privileged entry point in the signed APK; never a daemon. */
object RootMain {
    @JvmStatic fun main(args: Array<String>) {
        try {
            require(args.size == 1 && args[0] in setOf("enable", "disable", "status")) { "INVALID_OPERATION" }
            check(Os.getuid() == 0) { "ROOT_REQUIRED" }
            check(Build.MANUFACTURER.equals("samsung", true) && Build.MODEL == "SM-S948Q") { "UNSUPPORTED_DEVICE" }
            val operation = args[0]
            if (operation != "status") {
                // The stock Samsung API rejects root/shell UIDs; root authorizes
                // this helper to drop to system UID for the fixed notification.
                Os.setgid(1000); Os.setuid(1000)
                val binder = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java)
                    .invoke(null, "isemtelephony") as? IBinder ?: error("PHONE_SERVICE_UNAVAILABLE")
                val service = Class.forName("com.android.internal.telephony.ISemTelephony\$Stub")
                    .getMethod("asInterface", IBinder::class.java).invoke(null, binder)
                val result = service.javaClass.getMethod("invokeOemRilRequestRawForPhone", Int::class.javaPrimitiveType, ByteArray::class.java, ByteArray::class.java)
                    .invoke(service, 0, DiagProtocol.request(operation == "enable"), ByteArray(200)) as Int
                println("RIL_RESULT=$result")
                check(result >= 0) { "RIL_REJECTED" }
            }
            val attempts = if (operation == "status") 1 else 5
            for (attempt in 0 until attempts) {
                val proc = ProcessBuilder("/vendor/bin/qrtr-lookup").redirectErrorStream(true).start()
                if (!proc.waitFor(5, TimeUnit.SECONDS)) { proc.destroyForcibly(); error("QRTR_TIMEOUT") }
                val text = proc.inputStream.bufferedReader().use { it.readText() }
                check(proc.exitValue() == 0) { "QRTR_FAILED" }
                val open = DiagProtocol.hasMpssEndpoints(text)
                if (operation == "status" || open == (operation == "enable") || attempt == attempts - 1) {
                    println("MPSS_OPEN=$open")
                    if (operation != "status" && open != (operation == "enable")) exitProcess(6)
                    return
                }
                Thread.sleep(3000)
            }
        } catch (error: Throwable) {
            val cause = error.cause ?: error
            System.err.println("DIAG_ERROR=" + cause.javaClass.simpleName + ":" + cause.message)
            exitProcess(5)
        }
    }
}
