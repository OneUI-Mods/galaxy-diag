package dev.ryuya.galaxydiag

import android.os.Build
import java.util.Locale

internal object DeviceSupport {
    private const val MIN_ANDROID_SDK = 37
    private const val MIN_ONE_UI_VERSION = 90_000
    private val socModelPattern = Regex("(SM[0-9]{4})Q?(?:-[A-Z0-9]+)*")
    private val supportedSocs = setOf("SM8650", "SM8750", "SM8845", "SM8850", "SM8950", "SM8975")

    val current: Boolean by lazy {
        matches(Build.VERSION.SDK_INT, Build.MANUFACTURER, Build.SOC_MODEL, ::readOneUiVersion)
    }

    fun matches(sdk: Int, manufacturer: String, socModel: String, oneUiVersion: () -> Int?): Boolean {
        if (sdk < MIN_ANDROID_SDK || !manufacturer.equals("samsung", ignoreCase = true)) return false
        val model = socModelPattern.matchEntire(socModel.trim().uppercase(Locale.ROOT))?.groupValues?.get(1)
            ?: return false
        if (model !in supportedSocs) return false
        return (oneUiVersion() ?: return false) >= MIN_ONE_UI_VERSION
    }

    private fun readOneUiVersion(): Int? = runCatching {
        val process = ProcessBuilder("/system/bin/getprop", "ro.build.version.oneui").start()
        val result = CommandOutput.collect(process, timeoutMillis = 250, maximumBytes = 128)
        result.text.trim().toIntOrNull()?.takeIf { result.code == 0 }
    }.getOrNull()
}
