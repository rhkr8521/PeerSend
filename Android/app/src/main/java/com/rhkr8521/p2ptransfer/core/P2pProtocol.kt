package com.rhkr8521.p2ptransfer.core

import android.webkit.MimeTypeMap
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.UUID

const val BROADCAST_PORT = 37020
const val DATA_PORT = 37021
const val BUFFER_SIZE = 65_536
const val LOCAL_FILE_PORT = 50_000
const val POLL_INTERVAL_MS = 3_000L
const val DEVICE_TIMEOUT_MS = 20_000L

const val DEFAULT_TUNNEL_HOST = "rhkr8521-tunnel.kro.kr"
const val DEFAULT_TUNNEL_TOKEN = "public-p2p-token-8521"
const val DEFAULT_TUNNEL_SUB_PREFIX = "ft"
const val TUNNEL_TCP_NAME = "file_tunnel"

private val gson = Gson()

object ControlWire {
    fun writeMessage(output: OutputStream, payload: Any) {
        val bytes = PickleCompat.serialize(payload)
        val header = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(bytes.size).array()
        output.write(header)
        output.write(bytes)
        output.flush()
    }

    fun readMessage(input: InputStream): JsonObject? {
        val header = input.readExactOrNull(4) ?: return null
        val size = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN).int
        if (size <= 0) {
            throw EOFException("Invalid control frame size: $size")
        }
        val payload = input.readExactOrNull(size) ?: return null
        return decodePayload(payload)
    }

    private fun decodePayload(payload: ByteArray): JsonObject {
        runCatching {
            val text = payload.toString(Charsets.UTF_8)
            JsonParser.parseString(text).asJsonObject
        }.getOrNull()?.let { return it }

        val decoded = PickleCompat.deserialize(payload)
        return gson.toJsonTree(decoded).asJsonObject
    }
}

fun buildTunnelEndpoints(hostPort: String, useSsl: Boolean): TunnelEndpoints {
    val cleaned = hostPort
        .trim()
        .removePrefix("http://")
        .removePrefix("https://")
        .removePrefix("ws://")
        .removePrefix("wss://")
        .trim('/')
    val wsScheme = if (useSsl) "wss" else "ws"
    val httpScheme = if (useSsl) "https" else "http"
    val hostOnly = cleaned.substringBefore('/').let { value ->
        if (':' in value) value.substringBeforeLast(':') else value
    }
    return TunnelEndpoints(
        wsUrl = "$wsScheme://$cleaned/_ws",
        adminBaseUrl = "$httpScheme://$cleaned",
        tcpHost = hostOnly,
    )
}

fun humanReadableSize(sizeBytes: Long): String {
    var value = sizeBytes.toDouble().coerceAtLeast(0.0)
    val units = listOf("B", "KB", "MB", "GB", "TB")
    for (unit in units) {
        if (value < 1024.0 || unit == units.last()) {
            return if (unit == "B") "${value.toLong()} $unit" else String.format(Locale.US, "%.2f %s", value, unit)
        }
        value /= 1024.0
    }
    return "${sizeBytes} B"
}

fun formatRemaining(seconds: Double): String {
    val wholeSeconds = seconds.toInt().coerceAtLeast(0)
    val isKorean = Locale.getDefault().language.startsWith("ko")
    return when {
        wholeSeconds < 60 -> if (isKorean) "${wholeSeconds}초" else "${wholeSeconds}s"
        wholeSeconds < 3600 -> {
            val minutes = wholeSeconds / 60
            val secondsPart = wholeSeconds % 60
            if (isKorean) "${minutes}분 ${secondsPart}초" else "${minutes}m ${secondsPart}s"
        }
        else -> {
            val hours = wholeSeconds / 3600
            val minutes = (wholeSeconds % 3600) / 60
            if (isKorean) "${hours}시간 ${minutes}분" else "${hours}h ${minutes}m"
        }
    }
}

fun safeName(filename: String): String {
    val filtered = filename.filter { ch ->
        ch.isLetterOrDigit() || ch in "._- ()[]{}@+&,"
    }.trim()
    return filtered.ifBlank { "downloaded_file.dat" }
}

fun makeSubdomain(prefix: String, seed: String): String {
    val base = seed
        .lowercase(Locale.US)
        .replace(Regex("[^a-z0-9]"), "")
        .take(12)
        .ifBlank { "android" }
    return "$prefix$base${UUID.randomUUID().toString().replace("-", "").take(6)}"
}

fun mimeTypeFromName(filename: String): String {
    val ext = filename.substringAfterLast('.', "").lowercase(Locale.US)
    if (ext == "iso") {
        return "application/x-iso9660-image"
    }
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
}

fun JsonObject.string(name: String): String? = get(name)?.takeUnless { it.isJsonNull }?.asString

fun JsonObject.int(name: String): Int? = get(name)?.takeUnless { it.isJsonNull }?.asInt

fun JsonObject.long(name: String): Long? = get(name)?.takeUnless { it.isJsonNull }?.asLong

fun JsonObject.bool(name: String): Boolean? = get(name)?.takeUnless { it.isJsonNull }?.asBoolean

fun JsonObject.array(name: String): JsonArray? = getAsJsonArray(name)

fun JsonElement.asObjectOrNull(): JsonObject? = if (isJsonObject) asJsonObject else null

fun Socket.closeQuietly() {
    runCatching { close() }
}

private fun InputStream.readExactOrNull(length: Int): ByteArray? {
    val result = ByteArray(length)
    var offset = 0
    while (offset < length) {
        val read = read(result, offset, length - offset)
        if (read < 0) {
            return null
        }
        offset += read
    }
    return result
}

private object PickleCompat {
    private object Mark

    fun serialize(value: Any): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(0x80)
        out.write(0x02)
        writeValue(out, value)
        out.write('.'.code)
        return out.toByteArray()
    }

    fun deserialize(bytes: ByteArray): Any? {
        val stack = mutableListOf<Any?>()
        val memo = mutableMapOf<Int, Any?>()
        var nextMemoIndex = 0
        var index = 0

        fun readByte(): Int {
            if (index >= bytes.size) throw EOFException("Unexpected EOF while reading pickle")
            return bytes[index++].toInt() and 0xFF
        }

        fun readIntLittle(length: Int): Long {
            var result = 0L
            repeat(length) { shift ->
                result = result or ((readByte().toLong() and 0xFF) shl (shift * 8))
            }
            return result
        }

        fun popMarkIndex(): Int {
            for (i in stack.lastIndex downTo 0) {
                if (stack[i] === Mark) return i
            }
            error("Pickle MARK not found")
        }

        fun popStackValue(): Any? {
            if (stack.isEmpty()) error("Pickle stack is empty")
            return stack.removeAt(stack.lastIndex)
        }

        while (index < bytes.size) {
            when (val op = readByte()) {
                0x80 -> readByte() // PROTO
                0x95 -> {
                    index += 8 // FRAME
                }
                '('.code -> stack.add(Mark)
                ')'.code -> stack.add(emptyList<Any?>())
                ']'.code -> stack.add(mutableListOf<Any?>())
                '}'.code -> stack.add(linkedMapOf<String, Any?>())
                0x8c -> {
                    val length = readByte()
                    val value = bytes.copyOfRange(index, index + length).toString(Charsets.UTF_8)
                    index += length
                    stack.add(value)
                }
                'X'.code -> {
                    val length = readIntLittle(4).toInt()
                    val value = bytes.copyOfRange(index, index + length).toString(Charsets.UTF_8)
                    index += length
                    stack.add(value)
                }
                0x8d -> {
                    val length = readIntLittle(8).toInt()
                    val value = bytes.copyOfRange(index, index + length).toString(Charsets.UTF_8)
                    index += length
                    stack.add(value)
                }
                0x88 -> stack.add(true)
                0x89 -> stack.add(false)
                'N'.code -> stack.add(null)
                'K'.code -> stack.add(readByte())
                'M'.code -> stack.add(readIntLittle(2).toInt())
                'J'.code -> {
                    val raw = readIntLittle(4).toInt()
                    stack.add(raw)
                }
                0x8a -> {
                    val length = readByte()
                    var result = 0L
                    repeat(length) { shift ->
                        result = result or ((readByte().toLong() and 0xFF) shl (shift * 8))
                    }
                    stack.add(result)
                }
                'I'.code -> {
                    val end = bytes.indexOfFrom('\n'.code.toByte(), index)
                    val text = bytes.copyOfRange(index, end).toString(Charsets.UTF_8)
                    index = end + 1
                    stack.add(
                        when (text) {
                        "00" -> false
                        "01" -> true
                        else -> text.toLongOrNull() ?: text
                        },
                    )
                }
                'V'.code -> {
                    val end = bytes.indexOfFrom('\n'.code.toByte(), index)
                    val text = bytes.copyOfRange(index, end).toString(Charsets.UTF_8)
                    index = end + 1
                    stack.add(text)
                }
                'q'.code -> {
                    memo[readByte()] = stack.lastOrNull()
                }
                'r'.code -> {
                    memo[readIntLittle(4).toInt()] = stack.lastOrNull()
                }
                0x94 -> {
                    memo[nextMemoIndex++] = stack.lastOrNull()
                }
                'h'.code -> stack.add(memo[readByte()])
                'j'.code -> stack.add(memo[readIntLittle(4).toInt()])
                's'.code -> {
                    val value = popStackValue()
                    val key = popStackValue().toString()
                    @Suppress("UNCHECKED_CAST")
                    val map = stack.last() as MutableMap<String, Any?>
                    map[key] = value
                }
                'u'.code -> {
                    val markIndex = popMarkIndex()
                    @Suppress("UNCHECKED_CAST")
                    val map = stack[markIndex - 1] as MutableMap<String, Any?>
                    var cursor = markIndex + 1
                    while (cursor < stack.size) {
                        val key = stack[cursor++].toString()
                        val value = stack[cursor++]
                        map[key] = value
                    }
                    stack.subList(markIndex, stack.size).clear()
                }
                'a'.code -> {
                    val value = popStackValue()
                    @Suppress("UNCHECKED_CAST")
                    val list = stack.last() as MutableList<Any?>
                    list += value
                }
                'e'.code -> {
                    val markIndex = popMarkIndex()
                    @Suppress("UNCHECKED_CAST")
                    val list = stack[markIndex - 1] as MutableList<Any?>
                    list.addAll(stack.subList(markIndex + 1, stack.size))
                    stack.subList(markIndex, stack.size).clear()
                }
                'l'.code -> {
                    val markIndex = popMarkIndex()
                    val list = stack.subList(markIndex + 1, stack.size).toMutableList()
                    stack.subList(markIndex, stack.size).clear()
                    stack.add(list)
                }
                'd'.code -> {
                    val markIndex = popMarkIndex()
                    val map = linkedMapOf<String, Any?>()
                    var cursor = markIndex + 1
                    while (cursor < stack.size) {
                        val key = stack[cursor++].toString()
                        val value = stack[cursor++]
                        map[key] = value
                    }
                    stack.subList(markIndex, stack.size).clear()
                    stack.add(map)
                }
                '.'.code -> return stack.lastOrNull()
                else -> error("Unsupported pickle opcode: 0x${op.toString(16)}")
            }
        }

        return stack.lastOrNull()
    }

    private fun writeValue(out: ByteArrayOutputStream, value: Any?) {
        when (value) {
            null -> out.write('N'.code)
            is Boolean -> out.write(if (value) 0x88 else 0x89)
            is Int -> writeInt(out, value.toLong())
            is Long -> writeInt(out, value)
            is Number -> writeInt(out, value.toLong())
            is String -> writeString(out, value)
            is Map<*, *> -> {
                out.write('}'.code)
                out.write('('.code)
                value.forEach { (key, entryValue) ->
                    writeString(out, key.toString())
                    writeValue(out, entryValue)
                }
                out.write('u'.code)
            }
            is Iterable<*> -> {
                out.write(']'.code)
                out.write('('.code)
                value.forEach { item ->
                    writeValue(out, item)
                }
                out.write('e'.code)
            }
            is Array<*> -> {
                out.write(']'.code)
                out.write('('.code)
                value.forEach { item ->
                    writeValue(out, item)
                }
                out.write('e'.code)
            }
            else -> writeString(out, value.toString())
        }
    }

    private fun writeString(out: ByteArrayOutputStream, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        out.write('X'.code)
        writeLittleEndian(out, bytes.size.toLong(), 4)
        out.write(bytes)
    }

    private fun writeInt(out: ByteArrayOutputStream, value: Long) {
        when {
            value in 0..0xFF -> {
                out.write('K'.code)
                out.write(value.toInt())
            }
            value in 0..0xFFFF -> {
                out.write('M'.code)
                writeLittleEndian(out, value, 2)
            }
            value in Int.MIN_VALUE..Int.MAX_VALUE -> {
                out.write('J'.code)
                writeLittleEndian(out, value, 4)
            }
            else -> {
                val bytes = mutableListOf<Byte>()
                var current = value
                while (current != 0L) {
                    bytes += (current and 0xFF).toByte()
                    current = current ushr 8
                }
                out.write(0x8a)
                out.write(bytes.size)
                bytes.forEach { byte ->
                    out.write(byte.toInt() and 0xFF)
                }
            }
        }
    }

    private fun writeLittleEndian(out: ByteArrayOutputStream, value: Long, length: Int) {
        repeat(length) { shift ->
            out.write(((value shr (shift * 8)) and 0xFF).toInt())
        }
    }
}

private fun ByteArray.indexOfFrom(target: Byte, startIndex: Int): Int {
    for (i in startIndex until size) {
        if (this[i] == target) return i
    }
    return -1
}
