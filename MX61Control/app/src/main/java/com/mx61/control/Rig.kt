package com.mx61.control

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

class Part(val idx: Int) {
    var name = "Part ${idx + 1}"
    var msb = 0
    var lsb = 0
    var pc = 0
    var mute = false
    var solo = false
    val cc = IntArray(128) { 64 }.also {
        it[1] = 0; it[7] = 100; it[11] = 127; it[91] = 40; it[93] = 0; it[94] = 0
        it[64] = 0; it[65] = 0; it[5] = 0
    }
}

class NoteEv(val ch: Int, val pitch: Int, val vel: Int, val t0: Long) { var t1 = 0L }

/** Estado único do app: 16 partes do MX61, log de notas e envio MIDI. */
object Rig {
    val parts = Array(16) { Part(it) }
    var selected = 0
    var engine: MidiEngine? = null
    var masterVol = 127
    val level = FloatArray(16)
    val notes = ArrayList<NoteEv>()
    var onPartChanged: ((Int) -> Unit)? = null

    /** CCs que o app controla e sincroniza com o teclado. */
    val tracked = intArrayOf(1, 5, 7, 10, 11, 64, 65, 71, 72, 73, 74, 75, 91, 93, 94)

    private var prefs: android.content.SharedPreferences? = null
    private val main = Handler(Looper.getMainLooper())
    private val saver = Runnable { save() }

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("rig", Context.MODE_PRIVATE)
        load()
    }

    // ---------- envio ----------
    private fun ch(i: Int) = i and 0x0F
    fun send(vararg b: Int) { engine?.send(*b) }

    fun noteOn(c: Int, n: Int, v: Int) { send(0x90 or ch(c), n, v); logOn(c, n, v) }
    fun noteOff(c: Int, n: Int) { send(0x80 or ch(c), n, 0); logOff(c, n) }

    fun setCC(p: Part, cc: Int, v: Int) {
        p.cc[cc] = v
        if (cc == 7) sendVol(p) else send(0xB0 or p.idx, cc, v)
        later()
    }

    fun effVol(p: Part): Int {
        val anySolo = parts.any { it.solo }
        return if (p.mute || (anySolo && !p.solo)) 0 else p.cc[7]
    }

    fun sendVol(p: Part) = send(0xB0 or p.idx, 7, effVol(p))
    fun sendAllVols() = parts.forEach { sendVol(it) }

    fun sendVoice(p: Part) {
        send(0xB0 or p.idx, 0, p.msb)
        send(0xB0 or p.idx, 32, p.lsb)
        send(0xC0 or p.idx, p.pc)
        later()
    }

    fun masterVolume(v: Int) {
        masterVol = v
        engine?.sendBytes(byteArrayOf(0xF0.toByte(), 0x7F, 0x7F, 0x04, 0x01, 0x00, v.toByte(), 0xF7.toByte()))
        later()
    }

    fun panic() {
        for (c in 0..15) { send(0xB0 or c, 123, 0); send(0xB0 or c, 120, 0) }
    }

    fun resetControllers() { for (c in 0..15) send(0xB0 or c, 121, 0) }

    fun gmOn() = engine?.sendBytes(byteArrayOf(0xF0.toByte(), 0x7E, 0x7F, 0x09, 0x01, 0xF7.toByte()))

    /** Envia todo o estado do app para o MX61. */
    fun syncAll() {
        masterVolume(masterVol)
        for (p in parts) {
            sendVoice(p)
            for (c in tracked) if (c != 7) send(0xB0 or p.idx, c, p.cc[c])
            sendVol(p)
        }
    }

    /** Hex "F0 43 ... F7" -> bytes, ou null se inválido. */
    fun parseSysex(text: String): ByteArray? {
        val toks = text.trim().split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
        if (toks.size < 3) return null
        val out = ByteArray(toks.size)
        for ((i, t) in toks.withIndex()) {
            val v = t.removePrefix("0x").removePrefix("0X").toIntOrNull(16) ?: return null
            if (v !in 0..255) return null
            out[i] = v.toByte()
        }
        val ok = (out.first().toInt() and 0xFF) == 0xF0 && (out.last().toInt() and 0xFF) == 0xF7 &&
            out.slice(1 until out.size - 1).all { it >= 0 }
        return if (ok) out else null
    }

    // ---------- entrada (do MX61) ----------
    fun incoming(status: Int, d1: Int, d2: Int) {
        val c = status and 0x0F
        val p = parts[c]
        when (status and 0xF0) {
            0x90 -> if (d2 > 0) logOn(c, d1, d2) else logOff(c, d1)
            0x80 -> logOff(c, d1)
            0xB0 -> if (d1 in tracked) { p.cc[d1] = d2; onPartChanged?.invoke(c); later() }
            0xC0 -> { p.pc = d1; onPartChanged?.invoke(c); later() }
        }
    }

    private fun logOn(c: Int, n: Int, v: Int) {
        level[c] = v / 127f
        notes.add(NoteEv(c, n, v, SystemClock.uptimeMillis()))
        if (notes.size > 2500) notes.subList(0, 500).clear()
    }

    private fun logOff(c: Int, n: Int) {
        for (i in notes.indices.reversed()) {
            val e = notes[i]
            if (e.ch == c && e.pitch == n && e.t1 == 0L) { e.t1 = SystemClock.uptimeMillis(); break }
        }
    }

    // ---------- persistência ----------
    private fun later() { main.removeCallbacks(saver); main.postDelayed(saver, 600) }

    private fun save() {
        val e = prefs?.edit() ?: return
        e.putInt("master", masterVol)
        for (p in parts) {
            val ccs = tracked.joinToString(",") { "$it:${p.cc[it]}" }
            e.putString("p${p.idx}", listOf(p.name.replace("|", " "), p.msb, p.lsb, p.pc, p.mute, p.solo, ccs).joinToString("|"))
        }
        e.apply()
    }

    private fun load() {
        val sp = prefs ?: return
        masterVol = sp.getInt("master", 127)
        for (p in parts) {
            val f = sp.getString("p${p.idx}", null)?.split("|") ?: continue
            if (f.size < 7) continue
            p.name = f[0]; p.msb = f[1].toIntOrNull() ?: 0; p.lsb = f[2].toIntOrNull() ?: 0
            p.pc = f[3].toIntOrNull() ?: 0; p.mute = f[4] == "true"; p.solo = f[5] == "true"
            f[6].split(",").forEach {
                val kv = it.split(":")
                if (kv.size == 2) p.cc[kv[0].toInt()] = kv[1].toInt()
            }
        }
    }
}
