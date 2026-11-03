package dev.ryuya.galaxydiag

import org.junit.Assert.*
import org.junit.Test

class DiagProtocolTest {
    @Test
    fun fixedPermissionBytes() {
        assertArrayEquals(byteArrayOf(0x11, 0xb0.toByte(), 0, 5, 1), DiagProtocol.request(true))
        assertArrayEquals(byteArrayOf(0x11, 0xb0.toByte(), 0, 5, 0), DiagProtocol.request(false))
    }

    @Test
    fun localRouterAndOtherProcessorsDoNotProveMpss() {
        assertFalse(
            DiagProtocol.hasMpssEndpoints(
                "4097 N/A 0 1 16419\n4097 N/A 2 1 16421\n4097 N/A 65 5 56"
            )
        )
        assertFalse(DiagProtocol.hasMpssEndpoints("4097 N/A 1 1 380\n4097 N/A 3 1 383"))
        assertFalse(DiagProtocol.hasMpssEndpoints("4097 N/A 1 0 380"))
    }

    @Test
    fun remoteInstancesAndNodeBothRequired() {
        assertTrue(
            DiagProtocol.hasMpssEndpoints(
                " 4097 N/A 1 0 389 DIAG service (MODEM:CMD)\n 4097 N/A 3 0 392 DIAG service (MODEM:DCI_CMD)"
            )
        )
        assertFalse(DiagProtocol.hasMpssEndpoints("4097 N/A 1 0 389\n4097 N/A 3 24 392"))
        assertFalse(
            DiagProtocol.hasMpssEndpoints("SERVICE VERSION INSTANCE NODE PORT\ngarbage\n4097")
        )
    }

    @Test
    fun shellQuotesCannotExecutePathMetacharacters() {
        assertEquals("'/data/app/base.apk'", DiagProtocol.shellQuote("/data/app/base.apk"))
        assertEquals("'a'\"'\"'b'", DiagProtocol.shellQuote("a'b"))
    }

    @Test
    fun automaticRetriesAreBounded() {
        assertTrue(DiagProtocol.shouldRetry(0, true))
        assertTrue(DiagProtocol.shouldRetry(1, true))
        assertFalse(DiagProtocol.shouldRetry(2, true))
        assertFalse(DiagProtocol.shouldRetry(0, false))
        assertFalse(DiagProtocol.shouldRetry(-1, true))
    }
}
