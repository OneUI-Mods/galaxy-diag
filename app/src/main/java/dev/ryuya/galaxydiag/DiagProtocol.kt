package dev.ryuya.galaxydiag

/** No caller-supplied RIL bytes or phone IDs are accepted. */
object DiagProtocol {
    fun request(enable: Boolean) = byteArrayOf(17, 176.toByte(), 0, 5, if (enable) 1 else 0)

    fun hasMpssEndpoints(list: String): Boolean {
        val instances =
            list
                .lineSequence()
                .mapNotNull { line ->
                    val fields = line.trim().split(Regex("\\s+"))
                    if (fields.size >= 5 && fields[0] == "4097" && fields[3] == "0")
                        fields[2].toIntOrNull()
                    else null
                }
                .toSet()
        return 1 in instances && 3 in instances
    }

    fun shellQuote(value: String) = "'" + value.replace("'", "'\"'\"'") + "'"

    fun shouldRetry(attempt: Int, enabled: Boolean) = enabled && attempt in 0..1
}
