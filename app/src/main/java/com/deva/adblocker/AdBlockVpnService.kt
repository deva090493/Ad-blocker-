package com.deva.adblocker

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * Local "VPN" that only captures DNS lookups (nothing leaves the phone through it).
 * Ad/tracker domains get "not found", everything else is forwarded to a normal DNS server.
 */
class AdBlockVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.deva.adblocker.START"
        const val ACTION_STOP = "com.deva.adblocker.STOP"
        private const val VPN_ADDRESS = "10.111.222.1"
        private const val FAKE_DNS = "10.111.222.2"
        private val UPSTREAM = listOf("1.1.1.1", "8.8.8.8")

        @Volatile var running = false
        val blockedCount = AtomicLong(0)
    }

    private var tun: ParcelFileDescriptor? = null
    private var worker: Thread? = null
    private var pool: ExecutorService? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            stopSelf()
            return START_NOT_STICKY
        }
        startVpn()
        return START_STICKY
    }

    @Synchronized
    private fun startVpn() {
        if (running) return
        try {
            if (Blocklist.size() == 0) Blocklist.load(this)
        } catch (_: Exception) { }

        val builder = Builder()
            .setSession("Ad Blocker")
            .addAddress(VPN_ADDRESS, 32)
            .addDnsServer(FAKE_DNS)
            .addRoute(FAKE_DNS, 32)   // only DNS comes to us; normal traffic stays direct & fast
            .setBlocking(true)
        try { builder.addDisallowedApplication(packageName) } catch (_: Exception) { }

        val fd = try { builder.establish() } catch (_: Exception) { null } ?: return
        tun = fd
        pool = Executors.newFixedThreadPool(8)
        running = true
        worker = Thread({ loop(fd) }, "dns-loop").also { it.start() }
    }

    @Synchronized
    private fun stopVpn() {
        running = false
        try { tun?.close() } catch (_: Exception) { }
        tun = null
        pool?.shutdownNow()
        pool = null
        worker?.interrupt()
        worker = null
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private fun loop(fd: ParcelFileDescriptor) {
        val input = FileInputStream(fd.fileDescriptor)
        val output = FileOutputStream(fd.fileDescriptor)
        val buf = ByteArray(32767)
        while (running) {
            val n = try { input.read(buf) } catch (_: Exception) { break }
            if (n <= 0) continue
            try { handle(buf.copyOf(n), output) } catch (_: Exception) { }
        }
    }

    private fun handle(pkt: ByteArray, out: FileOutputStream) {
        if (pkt.size < 28) return
        if (((pkt[0].toInt() shr 4) and 0x0F) != 4) return   // IPv4 only
        val ihl = (pkt[0].toInt() and 0x0F) * 4
        if ((pkt[9].toInt() and 0xFF) != 17) return           // UDP only
        if (pkt.size < ihl + 8) return
        val srcIp = pkt.copyOfRange(12, 16)
        val dstIp = pkt.copyOfRange(16, 20)
        val srcPort = Packets.u16(pkt, ihl)
        val dstPort = Packets.u16(pkt, ihl + 2)
        if (dstPort != 53) return
        val dns = pkt.copyOfRange(ihl + 8, pkt.size)

        val q = Packets.parseQuestion(dns)
        if (q != null && Blocklist.isBlocked(q.name)) {
            blockedCount.incrementAndGet()
            val reply = Packets.buildUdpIpv4(dstIp, srcIp, 53, srcPort, Packets.blockedResponse(dns, q))
            write(out, reply)
            return
        }
        pool?.execute { forward(dns, srcIp, dstIp, srcPort, out) }
    }

    private fun forward(dns: ByteArray, srcIp: ByteArray, dstIp: ByteArray, srcPort: Int, out: FileOutputStream) {
        var sock: DatagramSocket? = null
        try {
            sock = DatagramSocket()
            protect(sock)
            sock.soTimeout = 4000
            val resp = ByteArray(4096)
            var got = -1
            for (server in UPSTREAM) {
                try {
                    sock.send(DatagramPacket(dns, dns.size, InetAddress.getByName(server), 53))
                    val rp = DatagramPacket(resp, resp.size)
                    sock.receive(rp)
                    got = rp.length
                    break
                } catch (_: SocketTimeoutException) { }
            }
            if (got > 0 && running) {
                write(out, Packets.buildUdpIpv4(dstIp, srcIp, 53, srcPort, resp.copyOf(got)))
            }
        } catch (_: Exception) {
        } finally {
            sock?.close()
        }
    }

    private fun write(out: FileOutputStream, data: ByteArray) {
        synchronized(out) {
            try { out.write(data) } catch (_: Exception) { }
        }
    }
}
