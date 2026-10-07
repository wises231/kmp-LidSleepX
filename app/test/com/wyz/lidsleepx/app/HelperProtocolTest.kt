package com.wyz.lidsleepx.app

import com.wyz.lidsleepx.app.helper.HelperProtocol
import com.wyz.lidsleepx.core.HelperRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class HelperProtocolTest {
    @Test
    fun `protocol round trips a request`() {
        val request = HelperRequest(
            version = HelperProtocol.PROTOCOL_VERSION,
            requestId = "abc",
            command = "setDisableSleep",
            payload = mapOf("disabled" to "true"),
        )
        val decoded = HelperProtocol.decodeRequest(HelperProtocol.encodeRequest(request))
        assertEquals(request, decoded)
    }

    @Test
    fun `protocol rejects commands outside the allow list`() {
        val error = HelperProtocol.validate(
            HelperRequest(HelperProtocol.PROTOCOL_VERSION, "abc", "runShell", emptyMap()),
        )
        assertNotNull(error)
    }

    @Test
    fun `protocol rejects invalid disable sleep values`() {
        val error = HelperProtocol.validate(
            HelperRequest(
                HelperProtocol.PROTOCOL_VERSION,
                "abc",
                "setDisableSleep",
                mapOf("disabled" to "yes"),
            ),
        )
        assertNotNull(error)
    }

    @Test
    fun `protocol accepts the version command`() {
        assertNull(
            HelperProtocol.validate(
                HelperRequest(HelperProtocol.PROTOCOL_VERSION, "abc", "version", emptyMap()),
            ),
        )
    }

    @Test
    fun `protocol accepts only supported hibernate modes`() {
        assertNull(
            HelperProtocol.validate(
                HelperRequest(
                    HelperProtocol.PROTOCOL_VERSION,
                    "abc",
                    "setHibernateMode",
                    mapOf("mode" to "25"),
                ),
            ),
        )
        assertNotNull(
            HelperProtocol.validate(
                HelperRequest(
                    HelperProtocol.PROTOCOL_VERSION,
                    "abc",
                    "setHibernateMode",
                    mapOf("mode" to "1"),
                ),
            ),
        )
    }
}
