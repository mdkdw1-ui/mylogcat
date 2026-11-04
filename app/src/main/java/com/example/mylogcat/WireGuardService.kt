package com.example.mylogcat

import android.content.Intent
import android.net.VpnService
import android.os.IBinder
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import java.io.File

class WireGuardService : android.app.Service() {
    private var backend: Backend? = null
    private var tunnel: Tunnel? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "start" -> {
                val confPath = intent.getStringExtra("confPath") ?: return START_NOT_STICKY
                if (VpnService.prepare(this) != null) { stopSelf(); return START_NOT_STICKY }
                startWg(confPath)
            }
            "stop" -> stopWg()
        }
        return START_NOT_STICKY
    }

    private fun startWg(confPath: String) {
        try {
            val cfg = File(confPath).inputStream().use { Config.parse(it) }
            backend = GoBackend(this)
            tunnel = object : Tunnel {
                override fun getName() = "mylogcat-wg"
                override fun onStateChange(newState: Tunnel.State) {
                    running = (newState == Tunnel.State.UP)
                }
            }
            backend?.setState(tunnel, Tunnel.State.UP, cfg)
            running = true
        } catch (e: Exception) {
            running = false
            stopSelf()
        }
    }

    private fun stopWg() {
        try { if (tunnel != null) backend?.setState(tunnel, Tunnel.State.DOWN, null) } catch (_: Exception) {}
        running = false
        stopSelf()
    }

    override fun onDestroy() { stopWg(); super.onDestroy() }

    companion object { @Volatile var running = false }
}
