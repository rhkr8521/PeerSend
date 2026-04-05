package com.rhkr8521.p2ptransfer.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ControlWireTest {

    @Test
    fun `reads python pickle framed message`() {
        val payloadHex =
            "80049514000000000000007d948c0474797065948c0641434345505494732e"
        val payload = payloadHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val framed = ByteBuffer.allocate(4 + payload.size)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(payload.size)
            .put(payload)
            .array()

        val message = ControlWire.readMessage(ByteArrayInputStream(framed))

        assertNotNull(message)
        assertEquals("ACCEPT", message.string("type"))
    }

    @Test
    fun `writes framed message that round trips`() {
        val output = ByteArrayOutputStream()

        ControlWire.writeMessage(
            output,
            mapOf(
                "type" to "REQUEST_SEND",
                "name" to "Android",
                "size" to 123,
                "is_zip" to false,
                "file_count" to 1,
            ),
        )

        val message = ControlWire.readMessage(ByteArrayInputStream(output.toByteArray()))

        assertNotNull(message)
        assertEquals("REQUEST_SEND", message.string("type"))
        assertEquals("Android", message.string("name"))
        assertEquals(123, message.int("size"))
        assertEquals(false, message.bool("is_zip"))
        assertEquals(1, message.int("file_count"))
    }
}
