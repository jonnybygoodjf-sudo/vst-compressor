package com.mx61.control

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiInputPort
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Handler
import android.os.Looper
import java.io.IOException

/** Conexão MIDI USB (class-compliant) com o MX61 via android.media.midi. */
class MidiEngine(ctx: Context) {
    private val mm = ctx.getSystemService(Context.MIDI_SERVICE) as? MidiManager
    private val main = Handler(Looper.getMainLooper())
    private var device: MidiDevice? = null
    private var toDevice: MidiInputPort? = null
    private var fromDevice: MidiOutputPort? = null

    var connectedName: String? = null
        private set
    var onStateChanged: (() -> Unit)? = null
    /** status (com canal), d1, d2 — sempre na thread principal. */
    var onMessage: ((Int, Int, Int) -> Unit)? = null

    val supported get() = mm != null

    fun devices(): List<MidiDeviceInfo> =
        mm?.devices?.filter { it.inputPortCount > 0 || it.outputPortCount > 0 } ?: emptyList()

    fun nameOf(i: MidiDeviceInfo): String =
        i.properties.getString(MidiDeviceInfo.PROPERTY_NAME) ?: "Dispositivo MIDI ${i.id}"

    fun watch(cb: () -> Unit) {
        mm?.registerDeviceCallback(object : MidiManager.DeviceCallback() {
            override fun onDeviceAdded(d: MidiDeviceInfo) = cb()
            override fun onDeviceRemoved(d: MidiDeviceInfo) {
                if (device?.info?.id == d.id) close()
                cb()
            }
        }, main)
    }

    fun open(info: MidiDeviceInfo, done: (Boolean) -> Unit) {
        val m = mm ?: return done(false)
        close()
        m.openDevice(info, { dev ->
            if (dev == null) { done(false); return@openDevice }
            device = dev
            if (info.inputPortCount > 0) toDevice = dev.openInputPort(0)
            if (info.outputPortCount > 0) {
                fromDevice = dev.openOutputPort(0)
                fromDevice?.connect(object : MidiReceiver() {
                    override fun onSend(d: ByteArray, off: Int, cnt: Int, ts: Long) {
                        val copy = d.copyOfRange(off, off + cnt)
                        main.post { parse(copy) }
                    }
                })
            }
            connectedName = nameOf(info)
            onStateChanged?.invoke()
            done(true)
        }, main)
    }

    fun close() {
        try { fromDevice?.close(); toDevice?.close(); device?.close() } catch (_: IOException) {}
        fromDevice = null; toDevice = null; device = null; connectedName = null
        onStateChanged?.invoke()
    }

    val connected get() = toDevice != null

    fun send(vararg b: Int) = sendBytes(ByteArray(b.size) { b[it].toByte() })

    fun sendBytes(bytes: ByteArray) {
        try { toDevice?.send(bytes, 0, bytes.size) } catch (_: IOException) {}
    }

    var onSysex: ((ByteArray) -> Unit)? = null
    private var sxBuf: java.io.ByteArrayOutputStream? = null

    private fun parse(d: ByteArray) {
        var i = 0
        while (i < d.size) {
            val s = d[i].toInt() and 0xFF
            if (s >= 0xF8) { i++; continue }
            val buf = sxBuf
            if (buf != null) {
                if (s < 0x80) { buf.write(s); i++; continue }
                if (s == 0xF7) { buf.write(s); onSysex?.invoke(buf.toByteArray()); sxBuf = null; i++; continue }
                sxBuf = null
            }
            if (s < 0x80) { i++; continue }
            when (s and 0xF0) {
                0x80, 0x90, 0xA0, 0xB0, 0xE0 -> {
                    if (i + 2 <= d.size - 1) onMessage?.invoke(s, d[i + 1].toInt() and 0x7F, d[i + 2].toInt() and 0x7F)
                    i += 3
                }
                0xC0, 0xD0 -> {
                    if (i + 1 <= d.size - 1) onMessage?.invoke(s, d[i + 1].toInt() and 0x7F, 0)
                    i += 2
                }
                0xF0 -> {
                    if (s == 0xF0) sxBuf = java.io.ByteArrayOutputStream().also { it.write(s) }
                    i++
                }
                else -> i++
            }
        }
    }
}
