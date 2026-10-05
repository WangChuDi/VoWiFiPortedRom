// SPDX-License-Identifier: GPL-2.0
package me.phh.sip

/** Normalize a dialable SMSC or a vendor length/TON/BCD field, without guessing a country. */
fun normalizeSmsc(raw: String?): String? {
    val input = raw?.trim() ?: return null
    // MIUI returns the AT+CSCA representation: "+447785016005",145.
    val at = Regex("^\"(\\+?[0-9]{1,15})\"\\s*,\\s*(129|145)$").matchEntire(input)
    if (at != null) {
        val number = at.groupValues[1]
        return if (at.groupValues[2] == "145" && !number.startsWith("+")) "+$number" else number
    }
    val text = input.removeSurrounding("\"")
    if (text.startsWith("+"))
        return text.takeIf { it.substring(1).matches(Regex("[0-9]{1,15}")) }
    if (text.length >= 6 && text.length % 2 == 0 && text.matches(Regex("[0-9a-fA-F]+"))) {
        val bytes = text.chunked(2).map { it.toInt(16) }
        if (bytes[0] in 2..12 && bytes[1] in listOf(0x81, 0x91)) {
            // Do not reinterpret truncated/overlong vendor fields as telephone numbers.
            if (bytes[0] != bytes.size - 1) return null
            val digits = StringBuilder()
            for (i in 2 until bytes.size) {
                val low = bytes[i] and 15
                val high = bytes[i] shr 4
                if (low > 9) return null
                digits.append(low)
                if (high <= 9) digits.append(high)
                else if (high != 15 || i != bytes.lastIndex) return null
            }
            if (digits.length !in 1..15) return null
            return (if (bytes[1] == 0x91) "+" else "") + digits
        }
    }
    return text.takeIf { it.matches(Regex("[0-9]{1,15}")) }
}

/** Only accept simple SIP service identities; reject header injection and URI parameters. */
fun normalizeSmsPsi(raw: String?): String? {
    val text = raw?.trim()?.removeSurrounding("<", ">") ?: return null
    return text.takeIf { it.matches(Regex("sip:(?:[+a-zA-Z0-9_.-]+@)?[a-zA-Z0-9.-]+")) }
}

/** TS 24.011 RP-Cause is LV: the length octet precedes the cause value. */
fun decodeRpErrorCause(body: ByteArray): Int? {
    if (body.size < 4 || body[0].toInt() != 5) return null
    val length = body[2].toInt() and 255
    if (length !in 1..2 || body.size < 3 + length) return null
    return body[3].toInt() and 127
}
