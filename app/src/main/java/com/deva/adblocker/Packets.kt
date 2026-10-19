package com.deva.adblocker

/** Small helpers to read DNS questions and build IPv4/UDP reply packets. */
object Packets {

    class Question(val name: String, val end: Int)

    fun u16(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 8) or (b[off + 1].toInt() and 0xFF)

    fun parseQuestion(dns: ByteArray): Question? {
        if (dns.size < 12) return null
        if (u16(dns, 4) < 1) return null
        var pos = 12
        val sb = StringBuilder()
        while (true) {
            if (pos >= dns.size) return null
            val len = dns[pos].toInt() and 0xFF
            if (len == 0) { pos++; break }
            if ((len and 0xC0) != 0) return null
            pos++
            if (pos + len > dns.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            for (i in 0 until len) sb.append((dns[pos + i].toInt() and 0xFF).toChar())
            pos += len
        }
        val end = pos + 4 // QTYPE + QCLASS
        if (end > dns.size) return null
        return Question(sb.toString().lowercase(), end)
    }

    /** "No such domain" answer for a blocked name. */
    fun blockedResponse(dns: ByteArray, q: Question): ByteArray {
        val r = dns.copyOf(q.end)
        r[2] = ((dns[2].toInt() and 0x79) or 0x80).toByte() // QR=1, keep opcode + RD
        r[3] = 0x83.toByte()                                // RA=1, RCODE=NXDOMAIN
        r[4] = 0; r[5] = 1                                  // 1 question
        for (i in 6..11) r[i] = 0                           // no answers/authority/additional
        return r
    }

    fun buildUdpIpv4(src: ByteArray, dst: ByteArray, srcPort: Int, dstPort: Int, payload: ByteArray): ByteArray {
        val total = 28 + payload.size
        val p = ByteArray(total)
        p[0] = 0x45
        p[2] = (total shr 8).toByte(); p[3] = total.toByte()
        p[8] = 64   // TTL
        p[9] = 17   // UDP
        System.arraycopy(src, 0, p, 12, 4)
        System.arraycopy(dst, 0, p, 16, 4)
        val cs = checksum(p, 0, 20)
        p[10] = (cs shr 8).toByte(); p[11] = cs.toByte()
        val udpLen = 8 + payload.size
        p[20] = (srcPort shr 8).toByte(); p[21] = srcPort.toByte()
        p[22] = (dstPort shr 8).toByte(); p[23] = dstPort.toByte()
        p[24] = (udpLen shr 8).toByte(); p[25] = udpLen.toByte()
        // UDP checksum 0 = not used (allowed for IPv4)
        System.arraycopy(payload, 0, p, 28, payload.size)
        return p
    }

    private fun checksum(b: ByteArray, off: Int, len: Int): Int {
        var sum = 0
        var i = off
        while (i < off + len) {
            sum += u16(b, i)
            i += 2
        }
        while ((sum shr 16) != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv() and 0xFFFF
    }
}
