package org.schabi.newpipe.cast

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AirPlayReceiver(val name: String, val host: String, val port: Int)

class AirPlaySender(private val context: Context) {

    companion object {
        private const val TAG = "AirPlaySender"
        private const val SERVICE_TYPE = "_airplay._tcp."
        private const val AIRPLAY_PORT = 7000
    }

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val discovered = mutableMapOf<String, AirPlayReceiver>()
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var onReceiversChanged: ((List<AirPlayReceiver>) -> Unit)? = null

    fun startDiscovery(onChange: (List<AirPlayReceiver>) -> Unit) {
        onReceiversChanged = onChange
        discovered.clear()

        val resolveQueue = ArrayDeque<NsdServiceInfo>()
        var resolving = false

        fun resolveNext() {
            if (resolving || resolveQueue.isEmpty()) return
            resolving = true
            val info = resolveQueue.removeFirst()
            nsdManager.resolveService(
                info,
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(si: NsdServiceInfo, code: Int) {
                        resolving = false
                        resolveNext()
                    }
                    override fun onServiceResolved(si: NsdServiceInfo) {
                        resolving = false
                        val host = si.host?.hostAddress ?: run {
                            resolveNext()
                            return
                        }
                        val port = if (si.port > 0) si.port else AIRPLAY_PORT
                        val receiver = AirPlayReceiver(si.serviceName, host, port)
                        discovered[si.serviceName] = receiver
                        onChange(discovered.values.toList())
                        resolveNext()
                    }
                }
            )
        }

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(type: String, code: Int) {
                Log.w(TAG, "Discovery start failed: $code")
            }
            override fun onStopDiscoveryFailed(type: String, code: Int) {}
            override fun onDiscoveryStarted(type: String) {}
            override fun onDiscoveryStopped(type: String) {}
            override fun onServiceFound(info: NsdServiceInfo) {
                resolveQueue.add(info)
                resolveNext()
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                discovered.remove(info.serviceName)
                onChange(discovered.values.toList())
            }
        }

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    fun stopDiscovery() {
        discoveryListener?.let {
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (_: Exception) {}
            discoveryListener = null
        }
        onReceiversChanged = null
    }

    /**
     * Sends the video URL to the selected AirPlay receiver via the AirPlay /play endpoint.
     * Returns true on success.
     */
    suspend fun play(receiver: AirPlayReceiver, videoUrl: String, startPositionSecs: Double = 0.0): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("http://${receiver.host}:${receiver.port}/play")
                val body = "Content-Location: $videoUrl\nStart-Position: $startPositionSecs\n"
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "text/parameters")
                conn.setRequestProperty("Content-Length", body.length.toString())
                conn.doOutput = true
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                OutputStreamWriter(conn.outputStream).use { it.write(body) }
                val code = conn.responseCode
                conn.disconnect()
                Log.d(TAG, "AirPlay /play → $code for ${receiver.name}")
                code in 200..299
            } catch (e: Exception) {
                Log.e(TAG, "AirPlay play failed", e)
                false
            }
        }
    }

    /** Stops playback on the receiver. */
    suspend fun stop(receiver: AirPlayReceiver) {
        withContext(Dispatchers.IO) {
            try {
                val url = URL("http://${receiver.host}:${receiver.port}/stop")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 3000
                conn.responseCode
                conn.disconnect()
            } catch (_: Exception) {}
        }
    }
}
