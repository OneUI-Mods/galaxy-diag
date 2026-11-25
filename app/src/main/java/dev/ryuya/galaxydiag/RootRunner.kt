package dev.ryuya.galaxydiag

import android.content.Context

object RootRunner {
    val executor = BackgroundExecutors.serial("GalaxyDiagCommands")

    fun prefs(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    data class Result(val success: Boolean, val open: Boolean?, val message: String)

    @Synchronized
    fun run(context: Context, operation: String): Result {
        require(operation in setOf("enable", "disable", "status"))
        var process: Process? = null
        val result =
            try {
                val apk = DiagProtocol.shellQuote(context.applicationInfo.sourceDir)
                val command =
                    "CLASSPATH=$apk /system/bin/app_process /system/bin dev.ryuya.galaxydiag.RootMain $operation"
                process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
                val response = CommandOutput.collect(process, 70_000, 32_768)
                val output = response.text
                val open =
                    when {
                        "MPSS_OPEN=true" in output -> true
                        "MPSS_OPEN=false" in output -> false
                        else -> null
                    }
                val success = response.code == 0 && open != null
                val message =
                    when {
                        success && open == true -> "MPSS接続を確認しました。NSGで測定できます。"
                        success && operation == "disable" -> "DIAG許可を解除しました。"
                        success -> "MPSSは公開されていません。許可を適用してください。"
                        "PHONE_SERVICE_UNAVAILABLE" in output -> "この端末ではSamsungの電話サービスを利用できません。Galaxy以外では動作しません。"
                        "RIL_RESULT=0" in output -> "許可通知は送信済みですが、MPSSの状態を確認できませんでした。再適用してください。"
                        else -> "root許可と端末の状態を確認してください。\n" + output.trim().takeLast(500)
                    }
                Result(success, open, message)
            } catch (error: Exception) {
                if (error is InterruptedException) Thread.currentThread().interrupt()
                val message =
                    if (
                        error is java.io.IOException && error.message?.contains("error=2") == true
                    ) {
                        "KernelSUなどのroot管理アプリでGalaxy DIAGへのroot許可を有効にしてから、再適用してください。"
                    } else "実行できませんでした：" + (error.message ?: error.javaClass.simpleName)
                Result(false, null, message)
            } finally {
                if (process?.isAlive == true) process.destroyForcibly()
            }
        prefs(context)
            .edit()
            .putString("last_message", result.message)
            .putLong("last_at", System.currentTimeMillis())
            .putString("last_operation", operation)
            .putBoolean("last_success", result.success)
            .putString("last_connection", result.open?.toString() ?: "unknown")
            .apply()
        return result
    }
}
