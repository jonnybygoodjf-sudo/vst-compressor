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
        it[1] = 0; it[7] = 100; it[11] = 127; it[91] = 40; it[93] = 0; it[31] = 64
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
    val tracked = intArrayOf(1, 5, 7, 10, 11, 31, 64, 65, 71, 72, 73, 74, 75, 91, 93)

    private var prefs: android.content.SharedPreferences? = null
    private val main = Handler(Looper.getMainLooper())
    private val saver = Runnable { save() }

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("rig", Context.MODE_PRIVATE)
        load()
    }

    /** Estado SysEx por endereço (h<<16|m<<8|l -> byte). Vem do que o usuário mexeu ou do que foi lido do MX61. */
    val g = HashMap<Int, Int>()
    /** Só o que o usuário alterou no app (reenviado em "enviar tudo"): 1 byte e 2 bytes (14 bits). */
    val userG = HashMap<Int, Int>()
    val user2 = HashMap<Int, Int>()
    var insMask = 0
    /** Device number do MX61 (0 = "1", padrão de fábrica). */
    var dev = 0
    var onSysexApplied: (() -> Unit)? = null

    fun gKey(h: Int, m: Int, l: Int) = (h shl 16) or (m shl 8) or l
    fun get(h: Int, m: Int, l: Int, def: Int = -1) = g[gKey(h, m, l)] ?: def
    fun gVal(h: Int, m: Int, l: Int, def: Int) = get(h, m, l, def)
    fun get2(h: Int, m: Int, l: Int): Int {
        val a = g[gKey(h, m, l)]; val b = g[gKey(h, m, l + 1)]
        return if (a != null && b != null) a * 128 + b else -1
    }

    /** Parameter Change nativo do MX: F0 43 10 7F 17 hh mm ll dd.. F7 (device number 1). */
    fun sx(vararg b: Int) { engine?.send(0xF0, 0x43, 0x10 or dev, 0x7F, 0x17, *b, 0xF7) }

    fun set1(h: Int, m: Int, l: Int, v: Int) { val k = gKey(h, m, l); g[k] = v; userG[k] = v; sx(h, m, l, v); later() }
    fun setGlobal(h: Int, m: Int, l: Int, v: Int) = set1(h, m, l, v)
    fun setPartParam(p: Part, lo: Int, v: Int) = set1(0x37, p.idx, lo, v)

    fun set2(h: Int, m: Int, l: Int, v: Int) {
        val k = gKey(h, m, l); g[k] = v shr 7; g[k + 1] = v and 0x7F; user2[k] = v
        sx(h, m, l, v shr 7, v and 0x7F); later()
    }

    /** Tipo de efeito (2 bytes MSB/LSB) no endereço h m 00. */
    fun setType(h: Int, m: Int, msb: Int, lsb: Int) = set2(h, m, 0, msb * 128 + lsb)

    fun setInsertMask(mask: Int) {
        insMask = mask
        val b4 = mask and 0x7F; val b3 = (mask shr 7) and 0x7F; val b2 = (mask shr 14) and 0x03
        sx(0x36, 0x00, 0x4C, 0, b2, b3, b4); later()
    }

    // ---------- leitura do MX61 (Dump Request / Parameter Change recebidos) ----------
    private fun dumpRequest(h: Int, m: Int, l: Int) = engine?.send(0xF0, 0x43, 0x20 or dev, 0x7F, 0x17, h, m, l, 0xF7)

    fun readBlocks(blocks: List<Triple<Int, Int, Int>>, done: () -> Unit) {
        var t = 0L
        for ((h, m, l) in blocks) { main.postDelayed({ dumpRequest(h, m, l) }, t); t += 140 }
        main.postDelayed(done, t + 500)
    }

    fun readAllBlocks(): List<Triple<Int, Int, Int>> =
        listOf(Triple(0, 0, 0), Triple(0x36, 0, 0), Triple(0x36, 1, 0), Triple(0x36, 2, 0), Triple(0x36, 0x10, 0),
            Triple(0x36, 0x30, 0), Triple(0x40, 3, 0)) +
            (0 until 16).map { Triple(0x37, it, 0) } + (0 until 16).map { Triple(0x38, it, 0) }

    fun onSysex(b: ByteArray) {
        fun u(i: Int) = b[i].toInt() and 0xFF
        if (b.size < 9 || u(0) != 0xF0 || u(1) != 0x43 || u(3) != 0x7F || u(4) != 0x17) return
        when (u(2) shr 4) {
            0 -> { // Bulk Dump
                if (b.size < 12) return
                val count = u(5) * 128 + u(6)
                if (count <= 0 || 10 + count > b.size) return
                apply(u(7), u(8), u(9), IntArray(count) { u(10 + it) })
            }
            1 -> apply(u(5), u(6), u(7), IntArray(b.size - 9) { u(8 + it) }) // Parameter Change
        }
    }

    private fun apply(h: Int, m: Int, l: Int, data: IntArray) {
        for (i in data.indices) g[gKey(h, m, l + i)] = data[i]
        if (h == 0x37 && m < 16) fromPartBytes(m, l, data.size)
        if (h == 0x36 && m == 0 && l <= 0x4C && l + data.size > 0x4C) {
            val d = IntArray(4) { g[gKey(0x36, 0, 0x4C + it)] ?: 0 }
            insMask = d[3] or (d[2] shl 7) or ((d[1] and 3) shl 14)
        }
        if (h == 0 && m == 0 && l == 0) g[gKey(0, 0, 0)]?.let { masterVol = it }
        onSysexApplied?.invoke()
    }

    private fun fromPartBytes(pp: Int, from: Int, n: Int) {
        val p = parts[pp]
        fun v(l: Int) = if (l >= from && l < from + n) g[gKey(0x37, pp, l)] else null
        v(0x01)?.let { p.msb = it }; v(0x02)?.let { p.lsb = it }; v(0x03)?.let { p.pc = it }
        v(0x0E)?.let { p.cc[7] = it }; v(0x0F)?.let { p.cc[10] = it }
        v(0x12)?.let { p.cc[91] = it }; v(0x13)?.let { p.cc[93] = it }
        v(0x23)?.let { p.cc[73] = it }; v(0x24)?.let { p.cc[75] = it }; v(0x25)?.let { p.cc[31] = it }; v(0x26)?.let { p.cc[72] = it }
        v(0x2D)?.let { p.cc[74] = it }; v(0x2E)?.let { p.cc[71] = it }
        main.post { onPartChanged?.invoke(pp) }
    }

    fun sendPerformanceName(name: String) {
        val s = name.padEnd(10).take(10)
        for (i in 0 until 10) { val c = s[i].code.coerceIn(0x20, 0x7E); g[gKey(0x36, 0, i)] = c; userG[gKey(0x36, 0, i)] = c; sx(0x36, 0, i, c) }
        later()
    }

    fun performanceName(): String? {
        val cs = (0 until 10).map { g[gKey(0x36, 0, it)] ?: return null }
        return cs.map { it.toChar() }.joinToString("").trimEnd()
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
        for ((k, v) in user2.entries.sortedWith(compareBy({ (it.key and 0xFF) != 0 }, { it.key }))) sx(k shr 16, (k shr 8) and 0xFF, k and 0xFF, v shr 7, v and 0x7F)
        for ((k, v) in userG) sx(k shr 16, (k shr 8) and 0xFF, k and 0xFF, v)
        if (insMask != 0) setInsertMask(insMask)
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
        e.putInt("dev", dev)
        e.putString("ug", userG.entries.joinToString(",") { "${it.key}:${it.value}" })
        e.putString("u2", user2.entries.joinToString(",") { "${it.key}:${it.value}" })
        e.putInt("insmask", insMask)
        for (p in parts) {
            val ccs = tracked.joinToString(",") { "$it:${p.cc[it]}" }
            e.putString("p${p.idx}", listOf(p.name.replace("|", " "), p.msb, p.lsb, p.pc, p.mute, p.solo, ccs,
                "").joinToString("|"))
        }
        e.apply()
    }

    private fun load() {
        val sp = prefs ?: return
        masterVol = sp.getInt("master", 127)
        dev = sp.getInt("dev", 0)
        fun readMap(key: String, into: HashMap<Int, Int>) = sp.getString(key, "")?.split(",")?.forEach {
            val kv = it.split(":"); if (kv.size == 2) into[kv[0].toInt()] = kv[1].toInt()
        }
        readMap("ug", userG); readMap("u2", user2); insMask = sp.getInt("insmask", 0)
        for ((k, v) in userG) g[k] = v
        for ((k, v) in user2) { g[k] = v shr 7; g[k + 1] = v and 0x7F }
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
