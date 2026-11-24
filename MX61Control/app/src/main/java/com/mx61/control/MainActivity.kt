package com.mx61.control

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*

class MainActivity : Activity() {
    private lateinit var engine: MidiEngine
    private lateinit var content: FrameLayout
    private lateinit var status: TextView
    private val tabs = ArrayList<TextView>()
    private var tab = 0
    private var currentView: View? = null
    private val names = listOf("MIXER", "TRACKS", "EFEITOS", "TECLADO", "MIDI")

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Rig.init(this)
        engine = MidiEngine(this)
        Rig.engine = engine
        Effects.load(this)
        engine.onMessage = { s, d1, d2 -> Rig.incoming(s, d1, d2) }
        engine.onSysex = { Rig.onSysex(it) }
        engine.onStateChanged = { refreshStatus(); if (tab == 4) show(4) }
        engine.watch { if (tab == 4) show(4) }

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(BG); fitsSystemWindows = true }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dpi(14f), dpi(8f), dpi(14f), dpi(8f))
        }
        header.addView(TextView(this).apply {
            text = "MX61"; textSize = 20f; setTextColor(ACCENT); setTypeface(null, Typeface.BOLD)
        })
        header.addView(TextView(this).apply { text = "  Control"; textSize = 20f; setTextColor(TEXT) })
        header.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        status = TextView(this).apply {
            textSize = 12f; setPadding(dpi(12f), dpi(6f), dpi(12f), dpi(6f)); setTypeface(null, Typeface.BOLD)
            setOnClickListener { show(4) }
        }
        header.addView(status)
        root.addView(header)

        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))

        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(PANEL) }
        names.forEachIndexed { i, n ->
            val t = TextView(this).apply {
                text = n; gravity = Gravity.CENTER; textSize = 12f; setTypeface(null, Typeface.BOLD)
                setPadding(0, dpi(14f), 0, dpi(14f)); setOnClickListener { show(i) }
            }
            tabs.add(t); bar.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(bar)
        setContentView(root)
        refreshStatus()
        show(0)
    }

    override fun onDestroy() { engine.close(); super.onDestroy() }

    private fun refreshStatus() {
        val ok = engine.connected
        status.text = if (ok) "● ${engine.connectedName}" else "● Sem conexão"
        status.setTextColor(if (ok) 0xFF101218.toInt() else TEXT)
        status.background = roundRect(this, if (ok) ACCENT else RED and 0x00FFFFFF or 0x66000000 or (RED and 0xFFFFFF), 14f)
    }

    private fun show(i: Int) {
        tab = i
        Rig.onPartChanged = null
        tabs.forEachIndexed { k, t -> t.setTextColor(if (k == i) ACCENT else DIM); t.setBackgroundColor(if (k == i) PANEL2 else PANEL) }
        content.removeAllViews()
        val v = when (i) { 0 -> mixerScreen(); 1 -> tracksScreen(); 2 -> fxScreen(); 3 -> keysScreen(); else -> midiScreen() }
        currentView = v
        content.addView(v, FrameLayout.LayoutParams(-1, -1))
    }

    private fun lp(w: Int, h: Int, weight: Float = 0f, m: Int = 0) =
        LinearLayout.LayoutParams(w, h, weight).apply { setMargins(m, m, m, m) }

    private fun label(text: String, size: Float = 12f, color: Int = DIM, bold: Boolean = false) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color); if (bold) setTypeface(null, Typeface.BOLD)
    }

    // =============================== MIXER ===============================
    private fun mixerScreen(): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dpi(6f), dpi(4f), dpi(6f), dpi(8f)) }
        val refresh = ArrayList<() -> Unit>()
        for (p in Rig.parts) {
            val col = CH_COLORS[p.idx]
            val strip = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                background = roundRect(this@MainActivity, PANEL, 14f); setPadding(dpi(4f), dpi(6f), dpi(4f), dpi(8f))
            }
            val num = TextView(this).apply {
                text = "${p.idx + 1}"; gravity = Gravity.CENTER; textSize = 14f; setTextColor(0xFF101218.toInt())
                setTypeface(null, Typeface.BOLD); background = roundRect(this@MainActivity, col, 8f)
            }
            strip.addView(num, lp(-1, dpi(26f), 0f, dpi(2f)))
            val voice = TextView(this).apply {
                textSize = 10f; setTextColor(TEXT); gravity = Gravity.CENTER; maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(0, dpi(4f), 0, dpi(4f))
                setOnClickListener { voiceDialog(p) { refresh.forEach { f -> f() } } }
            }
            strip.addView(voice, lp(-1, dpi(38f)))
            val pan = Knob(this, "PAN", 0, 127, p.cc[10], 64, col, true) { Rig.setCC(p, 10, it) }
            strip.addView(pan, lp(dpi(60f), dpi(68f)))
            val sends = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val rev = Knob(this, "REV", 0, 127, p.cc[91], 40, col) { Rig.setCC(p, 91, it) }
            val cho = Knob(this, "CHO", 0, 127, p.cc[93], 0, col) { Rig.setCC(p, 93, it) }
            sends.addView(rev, lp(dpi(38f), dpi(52f))); sends.addView(cho, lp(dpi(38f), dpi(52f)))
            strip.addView(sends)
            val fader = Fader(this, col, p.cc[7], p.idx) { Rig.setCC(p, 7, it) }
            strip.addView(fader, lp(dpi(76f), 0, 1f))
            val vol = label("", 11f, TEXT, true).apply { gravity = Gravity.CENTER }
            strip.addView(vol, lp(-1, -2))
            val ms = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            val m = pill(this, "M", RED, p.mute) { p.mute = it; Rig.sendAllVols() }
            val s = pill(this, "S", AMBER, p.solo) { p.solo = it; Rig.sendAllVols() }
            ms.addView(m, lp(dpi(34f), dpi(32f), 0f, dpi(2f))); ms.addView(s, lp(dpi(34f), dpi(32f), 0f, dpi(2f)))
            strip.addView(ms, lp(-2, -2, 0f, dpi(2f)))
            fader.setOnTouchListener { v, e -> val r = v.onTouchEvent(e); vol.text = "${fader.value}"; r }
            val upd: () -> Unit = {
                voice.text = p.name; vol.text = "${p.cc[7]}"
                pan.set(p.cc[10], false); rev.set(p.cc[91], false); cho.set(p.cc[93], false); fader.set(p.cc[7], false)
                setPill(m, p.mute); setPill(s, p.solo)
            }
            upd(); refresh.add(upd)
            row.addView(strip, lp(dpi(90f), -1, 0f, dpi(3f)))
        }
        Rig.onPartChanged = { i -> refresh[i]() }
        return HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row, ViewGroup.LayoutParams(-2, -1)) }
    }

    // =============================== TRACKS ===============================
    private fun tracksScreen(): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dpi(8f), dpi(4f), dpi(8f), dpi(8f)) }
        col.addView(label("Cada linha é uma parte (track) do MX61. As notas que você toca aparecem rolando; toque na linha para selecionar a parte.", 11f),
            lp(-1, -2, 0f, dpi(4f)))
        val refresh = ArrayList<() -> Unit>()
        for (p in Rig.parts) {
            val c = CH_COLORS[p.idx]
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val head = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                background = roundRect(this@MainActivity, PANEL, 8f); setPadding(dpi(6f), 0, dpi(4f), 0)
            }
            head.addView(TextView(this).apply {
                text = "${p.idx + 1}"; gravity = Gravity.CENTER; setTextColor(0xFF101218.toInt()); textSize = 13f
                setTypeface(null, Typeface.BOLD); background = roundRect(this@MainActivity, c, 6f)
            }, lp(dpi(24f), dpi(24f)))
            val nm = TextView(this).apply {
                textSize = 11f; setTextColor(TEXT); maxLines = 2; setPadding(dpi(6f), 0, dpi(4f), 0)
                ellipsize = android.text.TextUtils.TruncateAt.END
                setOnClickListener { voiceDialog(p) { refresh.forEach { f -> f() } } }
            }
            head.addView(nm, lp(0, -2, 1f))
            val m = pill(this, "M", RED, p.mute, 11f) { p.mute = it; Rig.sendAllVols() }
            val s = pill(this, "S", AMBER, p.solo, 11f) { p.solo = it; Rig.sendAllVols() }
            m.setPadding(dpi(7f), dpi(4f), dpi(7f), dpi(4f)); s.setPadding(dpi(7f), dpi(4f), dpi(7f), dpi(4f))
            head.addView(m, lp(-2, -2, 0f, dpi(1f))); head.addView(s, lp(-2, -2, 0f, dpi(1f)))
            val lane = LaneView(this, p.idx)
            lane.setOnClickListener { Rig.selected = p.idx }
            r.addView(head, lp(dpi(168f), dpi(46f), 0f, dpi(2f)))
            r.addView(lane, lp(0, dpi(46f), 1f, dpi(2f)))
            col.addView(r, lp(-1, -2))
            val upd: () -> Unit = { nm.text = p.name; setPill(m, p.mute); setPill(s, p.solo) }
            upd(); refresh.add(upd)
        }
        Rig.onPartChanged = { i -> refresh[i]() }
        return ScrollView(this).apply { addView(col) }
    }

    // =============================== EFEITOS ===============================
    private fun partChips(onSel: (Int) -> Unit): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dpi(8f), dpi(6f), dpi(8f), dpi(6f)) }
        val chips = ArrayList<TextView>()
        fun paint() = chips.forEachIndexed { i, t ->
            val sel = i == Rig.selected
            t.background = roundRect(this, if (sel) CH_COLORS[i] else PANEL2, 18f)
            t.setTextColor(if (sel) 0xFF101218.toInt() else TEXT)
        }
        for (i in 0 until 16) {
            val t = TextView(this).apply {
                text = "${i + 1}"; gravity = Gravity.CENTER; textSize = 13f; setTypeface(null, Typeface.BOLD)
                setOnClickListener { Rig.selected = i; paint(); onSel(i) }
            }
            chips.add(t); row.addView(t, lp(dpi(38f), dpi(38f), 0f, dpi(3f)))
        }
        paint()
        return HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row) }
    }

    private var fxPage = 0
    private var fxRebuild: (() -> Unit)? = null

    private fun noteName(n: Int) = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")[n % 12] + (n / 12 - 2)
    private fun tabFmt(v: Int): String { val t = Effects.tables[3]?.getOrNull(v) ?: return "$v"; return if (t.contains("THRU")) t else t + "Hz" }
    private fun signed(v: Int) = (v - 64).let { if (it > 0) "+$it" else "$it" }

    private fun fxCard(body: LinearLayout, title: String): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = roundRect(this@MainActivity, PANEL, 14f)
            setPadding(dpi(10f), dpi(8f), dpi(10f), dpi(8f))
        }
        card.addView(label(title, 11f, ACCENT, true))
        body.addView(card, lp(-1, -2, 0f, dpi(4f)))
        return card
    }

    private fun grid(card: LinearLayout): GridLayout { val g = GridLayout(this).apply { columnCount = 4 }; card.addView(g); return g }

    private fun GridLayout.addKnob(k: Knob) = addView(k, GridLayout.LayoutParams().apply {
        width = dpi(76f); height = dpi(84f); setMargins(dpi(2f), dpi(2f), dpi(2f), dpi(2f))
    })

    private fun gKnob(l: String, h: Int, m: Int, lo: Int, min: Int, max: Int, def: Int, bip: Boolean = false,
                      color: Int = ACCENT, fmt: ((Int) -> String)? = null) =
        Knob(this, l, min, max, Rig.gVal(h, m, lo, def), def, color, bip, fmt) { Rig.setGlobal(h, m, lo, it) }

    /** Linha com rótulo, slider fino, botões −/+ e valor formatado. value < 0 = ainda não lido do MX61. */
    private fun sliderRow(name: String, min: Int, max: Int, value: Int, fmt: (Int) -> String, color: Int, onChange: (Int) -> Unit): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dpi(2f), 0, dpi(2f)) }
        val lbl = label(name, 12f, TEXT).apply { maxLines = 2 }
        row.addView(lbl, lp(dpi(112f), -2))
        var cur = value
        val valTxt = label(if (value < 0) "—" else fmt(value), 12f, ACCENT, true).apply { gravity = Gravity.END }
        val sb = SeekBar(this).apply {
            this.max = max - min; progress = if (value < 0) 0 else value - min
            progressTintList = android.content.res.ColorStateList.valueOf(color)
            thumbTintList = android.content.res.ColorStateList.valueOf(color)
        }
        fun apply(v: Int) { cur = v.coerceIn(min, max); valTxt.text = fmt(cur); onChange(cur) }
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) apply(min + p) }
            override fun onStartTrackingTouch(s: SeekBar?) { sb.parent?.requestDisallowInterceptTouchEvent(true) }
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        fun step(d: Int) { apply((if (cur < 0) min else cur) + d); sb.progress = cur - min }
        row.addView(button(this, "−", PANEL2) { step(-1) }.apply { setPadding(dpi(10f), dpi(2f), dpi(10f), dpi(2f)) }, lp(-2, -2))
        row.addView(sb, lp(0, -2, 1f))
        row.addView(button(this, "+", PANEL2) { step(1) }.apply { setPadding(dpi(10f), dpi(2f), dpi(10f), dpi(2f)) }, lp(-2, -2))
        row.addView(valTxt, lp(dpi(78f), -2))
        return row
    }

    private fun switchRow(name: String, on: Boolean, color: Int, onChange: (Boolean) -> Unit): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dpi(3f), 0, dpi(3f)) }
        row.addView(label(name, 12f, TEXT), lp(0, -2, 1f))
        row.addView(pill(this, if (on) "ON" else "OFF", color, on) { onChange(it) }.also { t ->
            t.setOnClickListener {
                val nv = t.text != "ON"; t.text = if (nv) "ON" else "OFF"; setPill(t, nv); onChange(nv)
            }
        })
        return row
    }

    /** Parâmetro SysEx genérico (1 ou 2 bytes). */
    private class AP(val label: String, val h: Int, val m: () -> Int, val l: Int, val two: Boolean, val min: Int, val max: Int,
                     val fmt: (Int) -> String = { "$it" })

    private fun apRow(a: AP, color: Int): View {
        val v = if (a.two) Rig.get2(a.h, a.m(), a.l) else Rig.get(a.h, a.m(), a.l)
        if (a.min == 0 && a.max == 1) return switchRow(a.label, v == 1, color) { Rig.set1(a.h, a.m(), a.l, if (it) 1 else 0) }
        return sliderRow(a.label, a.min, a.max, v, a.fmt, color) { if (a.two) Rig.set2(a.h, a.m(), a.l, it) else Rig.set1(a.h, a.m(), a.l, it) }
    }

    private fun readButton(blocks: List<Triple<Int, Int, Int>>, text: String = "Ler valores do MX61"): TextView =
        button(this, text, PANEL2, ACCENT) {
            if (!Rig.engine!!.connected) Toast.makeText(this, "Conecte o MX61 na aba MIDI", Toast.LENGTH_SHORT).show()
            else { Toast.makeText(this, "Lendo…", Toast.LENGTH_SHORT).show(); Rig.readBlocks(blocks) { fxRebuild?.invoke() } }
        }

    private fun fxScreen(): View {
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val holder = FrameLayout(this)
        val seg = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dpi(8f), dpi(6f), dpi(8f), 0) }
        val segNames = listOf("PARTE", "REVERB", "CHORUS", "INSERT", "EQ", "ARP", "MASTER")
        val segViews = ArrayList<TextView>()
        fun paintSeg() = segViews.forEachIndexed { i, t ->
            t.background = roundRect(this, if (i == fxPage) ACCENT else PANEL2, 10f)
            t.setTextColor(if (i == fxPage) 0xFF101218.toInt() else TEXT)
        }
        fun build() {
            Rig.onPartChanged = null; Rig.onSysexApplied = null
            holder.removeAllViews()
            holder.addView(when (fxPage) {
                0 -> fxPartPage(); 1 -> effectPage("REVERB (efeito de sistema)", 0x36, 0x01, "rev") { b ->
                    val g = grid(fxCard(b, "NÍVEIS"))
                    g.addKnob(gKnob("RETORNO", 0x36, 0x01, 0x24, 0, 127, 0x40)); g.addKnob(gKnob("PAN", 0x36, 0x01, 0x25, 1, 127, 0x40, true))
                }
                2 -> effectPage("CHORUS / DELAY (efeito de sistema)", 0x36, 0x02, "cho") { b ->
                    val g = grid(fxCard(b, "NÍVEIS"))
                    g.addKnob(gKnob("RETORNO", 0x36, 0x02, 0x24, 0, 127, 0x60)); g.addKnob(gKnob("PAN", 0x36, 0x02, 0x25, 1, 127, 0x40, true))
                    g.addKnob(gKnob("→ REVERB", 0x36, 0x02, 0x26, 0, 127, 0))
                }
                3 -> fxInsertPage(); 4 -> fxEqPage(); 5 -> fxArpPage(); else -> fxMasterPage()
            }, FrameLayout.LayoutParams(-1, -1))
            paintSeg()
        }
        fxRebuild = { if (tab == 2) build() }
        segNames.forEachIndexed { i, n ->
            val t = TextView(this).apply {
                text = n; gravity = Gravity.CENTER; textSize = 11f; setTypeface(null, Typeface.BOLD)
                setPadding(dpi(14f), dpi(9f), dpi(14f), dpi(9f)); setOnClickListener { fxPage = i; build() }
            }
            segViews.add(t); seg.addView(t, lp(-2, -2, 0f, dpi(2f)))
        }
        page.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(seg) })
        page.addView(holder, lp(-1, 0, 1f))
        build()
        return page
    }

    // ----- PARTE -----
    private fun fxPartPage(): View {
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dpi(10f), 0, dpi(10f), dpi(16f)) }
        val knobs = ArrayList<() -> Unit>()
        val title = label("", 16f, TEXT, true)
        fun cur() = Rig.parts[Rig.selected]

        fun ccKnob(g: GridLayout, lbl: String, cc: Int, def: Int, bip: Boolean = false) {
            val k = Knob(this, lbl, 0, 127, cur().cc[cc], def, CH_COLORS[Rig.selected], bip) { Rig.setCC(cur(), cc, it) }
            knobs.add { k.set(cur().cc[cc], false) }; g.addKnob(k)
        }
        fun sxKnob(g: GridLayout, lbl: String, lo: Int, min: Int, max: Int, def: Int, bip: Boolean = false, fmt: ((Int) -> String)? = null) {
            fun v() = Rig.get(0x37, cur().idx, lo, def)
            val k = Knob(this, lbl, min, max, v(), def, CH_COLORS[Rig.selected], bip, fmt) { Rig.setPartParam(cur(), lo, it) }
            knobs.add { k.set(v(), false) }; g.addKnob(k)
        }

        var c = fxCard(body, "ENVIOS DE EFEITO"); var g = grid(c)
        ccKnob(g, "REVERB", 91, 40); ccKnob(g, "CHORUS", 93, 0); sxKnob(g, "DRY", 0x15, 0, 127, 127)
        c = fxCard(body, "FILTRO (offset ±)"); g = grid(c)
        ccKnob(g, "CUTOFF", 74, 64, true); ccKnob(g, "RESSON.", 71, 64, true)
        c = fxCard(body, "ENVELOPE DE AMPLITUDE (±)"); g = grid(c)
        ccKnob(g, "ATTACK", 73, 64, true); ccKnob(g, "DECAY", 75, 64, true); ccKnob(g, "SUSTAIN", 31, 64, true); ccKnob(g, "RELEASE", 72, 64, true)
        c = fxCard(body, "ENVELOPE DE FILTRO (±)"); g = grid(c)
        sxKnob(g, "ATTACK", 0x27, 0, 127, 64, true); sxKnob(g, "DECAY", 0x28, 0, 127, 64, true)
        sxKnob(g, "SUSTAIN", 0x29, 0, 127, 64, true); sxKnob(g, "RELEASE", 0x2A, 0, 127, 64, true)
        sxKnob(g, "DEPTH", 0x2B, 0, 127, 64, true)
        c = fxCard(body, "EXPRESSÃO"); g = grid(c)
        ccKnob(g, "VOLUME", 7, 100); ccKnob(g, "PAN", 10, 64, true); ccKnob(g, "EXPRESS.", 11, 127)
        ccKnob(g, "MOD", 1, 0); ccKnob(g, "PORTAM.", 5, 0)
        sxKnob(g, "VEL.DEPTH", 0x0C, 0, 127, 64); sxKnob(g, "VEL.OFFS", 0x0D, 0, 127, 64)
        sxKnob(g, "TRANSP.", 0x16, 0x28, 0x58, 0x40, true) { signed(it) }
        val sw = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dpi(6f), 0, 0) }
        val port = pill(this, "Portamento", ACCENT, cur().cc[65] >= 64) { Rig.setCC(cur(), 65, if (it) 127 else 0) }
        val sus = pill(this, "Sustain", ACCENT, cur().cc[64] >= 64) { Rig.setCC(cur(), 64, if (it) 127 else 0) }
        val mono = pill(this, "Mono", ACCENT, Rig.get(0x37, cur().idx, 0x05, 1) == 0) { Rig.setPartParam(cur(), 0x05, if (it) 0 else 1) }
        sw.addView(port, lp(-2, -2, 0f, dpi(3f))); sw.addView(sus, lp(-2, -2, 0f, dpi(3f))); sw.addView(mono, lp(-2, -2, 0f, dpi(3f)))
        c.addView(sw)
        knobs.add { setPill(port, cur().cc[65] >= 64); setPill(sus, cur().cc[64] >= 64); setPill(mono, Rig.get(0x37, cur().idx, 0x05, 1) == 0) }
        c = fxCard(body, "FAIXA DE NOTAS, VELOCITY E PITCH BEND"); g = grid(c)
        sxKnob(g, "NOTA MÍN", 0x08, 0, 127, 0, false) { noteName(it) }; sxKnob(g, "NOTA MÁX", 0x09, 0, 127, 127, false) { noteName(it) }
        sxKnob(g, "VEL MÍN", 0x06, 1, 127, 1); sxKnob(g, "VEL MÁX", 0x07, 1, 127, 127)
        sxKnob(g, "PB ↑", 0x0A, 0x10, 0x4C, 0x42, false) { signed(it) }; sxKnob(g, "PB ↓", 0x0B, 0x10, 0x4C, 0x3E, false) { signed(it) }

        fun reload() { title.text = "Parte ${cur().idx + 1} · ${cur().name}"; knobs.forEach { it() } }
        page.addView(partChips { reload() })
        page.addView(title, lp(-2, -2, 0f, dpi(10f)))
        page.addView(ScrollView(this).apply { addView(body) }, lp(-1, 0, 1f))
        reload()
        Rig.onPartChanged = { i -> if (i == Rig.selected) reload() }
        return page
    }

    // ----- REVERB / CHORUS / INSERT (catálogo oficial de efeitos) -----
    private fun effectPage(title: String, h: Int, m: Int, block: String, extra: (LinearLayout) -> Unit): View {
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dpi(10f), dpi(6f), dpi(10f), dpi(16f)) }
        val card = fxCard(body, title)
        val typeBtn = button(this, "", PANEL2) { }
        card.addView(typeBtn, lp(-1, -2, 0f, dpi(4f)))
        card.addView(readButton(listOf(Triple(h, m, 0)), "Ler tipo e parâmetros do MX61"), lp(-1, -2, 0f, dpi(2f)))
        extra(body)
        val pc = fxCard(body, "PARÂMETROS DO EFEITO")
        val key = if (block == "rev") "rev" else "cho"
        fun rebuild() {
            while (pc.childCount > 1) pc.removeViewAt(1)
            val fx = Effects.find(Rig.get2(h, m, 0), block)
            typeBtn.text = "Tipo: ${fx?.name ?: "—"}  ▾"
            if (fx == null) { pc.addView(label("Tipo ainda não lido. Escolha um tipo acima ou toque em \"Ler\".", 12f)); return }
            val ps = fx.params[key]?.filter { !(block == "cho" && it.dryWet) } ?: emptyList()
            if (ps.isEmpty()) pc.addView(label("Este tipo não tem parâmetros.", 12f))
            for (p in ps) pc.addView(sliderRow(p.name, p.min, p.max, Rig.get2(h, m, p.offset), { p.fmt(it) }, ACCENT) { Rig.set2(h, m, p.offset, it) })
        }
        typeBtn.setOnClickListener {
            val list = Effects.forBlock(block)
            AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle("Tipo")
                .setItems(list.map { it.name }.toTypedArray()) { _, i ->
                    val fx = list[i]
                    for (l in 4 until 0x24) { Rig.g.remove(Rig.gKey(h, m, l)); Rig.user2.remove(Rig.gKey(h, m, l)) }
                    Rig.setType(h, m, fx.msb, fx.lsb); rebuild()
                    if (Rig.engine?.connected == true) Rig.readBlocks(listOf(Triple(h, m, 0))) { rebuild() }
                }.show()
        }
        rebuild()
        return ScrollView(this).apply { addView(body) }
    }

    private fun fxInsertPage(): View {
        val page = effectPage("INSERT A (voz em edição — modo Voice)", 0x40, 0x03, "ins") { b ->
            val c = fxCard(b, "INSERT POR PARTE (modo Performance, até 4 partes)")
            val grid = GridLayout(this).apply { columnCount = 8 }
            for (i in 0 until 16) {
                val on = (Rig.insMask shr i) and 1 == 1
                grid.addView(pill(this, "${i + 1}", CH_COLORS[i], on) { v ->
                    val nm = if (v) Rig.insMask or (1 shl i) else Rig.insMask and (1 shl i).inv()
                    if (Integer.bitCount(nm) > 4) { Toast.makeText(this, "Máximo de 4 partes", Toast.LENGTH_SHORT).show(); show(2) }
                    else Rig.setInsertMask(nm)
                }, GridLayout.LayoutParams().apply { setMargins(dpi(2f), dpi(2f), dpi(2f), dpi(2f)) })
            }
            c.addView(grid)
            c.addView(label("O efeito de insert em si pertence à voz: os parâmetros abaixo editam a voz atual do MX61 (modo Voice).", 11f))
        }
        return page
    }

    // ----- EQ MASTER -----
    private fun fxEqPage(): View {
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dpi(10f), dpi(6f), dpi(10f), dpi(16f)) }
        body.addView(readButton(listOf(Triple(0x36, 0x10, 0))), lp(-1, -2, 0f, dpi(4f)))
        val bands = listOf(Triple("LOW", 0x00, listOf(0x04, 0x28)), Triple("LOW-MID", 0x04, listOf(0x0E, 0x36)),
            Triple("MID", 0x08, listOf(0x0E, 0x36)), Triple("HI-MID", 0x0C, listOf(0x0E, 0x36)), Triple("HIGH", 0x10, listOf(0x1C, 0x3A)))
        val defF = intArrayOf(0x0C, 0x14, 0x1C, 0x2C, 0x34)
        var c = fxCard(body, "MASTER EQ — GANHO"); var g = grid(c)
        for ((n, base, _) in bands) g.addKnob(gKnob(n, 0x36, 0x10, base, 0x34, 0x4C, 0x40, true, fmt = { signed(it) + "dB" }))
        c = fxCard(body, "MASTER EQ — FREQUÊNCIA"); g = grid(c)
        bands.forEachIndexed { i, (n, base, r) -> g.addKnob(gKnob(n, 0x36, 0x10, base + 1, r[0], r[1], defF[i], fmt = { tabFmt(it) })) }
        c = fxCard(body, "MASTER EQ — Q"); g = grid(c)
        for ((n, base, _) in bands) g.addKnob(gKnob(n, 0x36, 0x10, base + 2, 1, 0x78, 7, fmt = { String.format(java.util.Locale.US, "%.1f", it / 10.0) }))
        c = fxCard(body, "FORMATO DOS EXTREMOS")
        c.addView(switchRow("Low = peaking (OFF = shelving)", Rig.gVal(0x36, 0x10, 0x03, 0) == 1, ACCENT) { Rig.setGlobal(0x36, 0x10, 0x03, if (it) 1 else 0) })
        c.addView(switchRow("High = peaking (OFF = shelving)", Rig.gVal(0x36, 0x10, 0x13, 0) == 1, ACCENT) { Rig.setGlobal(0x36, 0x10, 0x13, if (it) 1 else 0) })
        body.addView(button(this, "Zerar ganhos (0 dB)") {
            for ((_, base, _) in bands) Rig.setGlobal(0x36, 0x10, base, 0x40)
            fxRebuild?.invoke()
        }, lp(-1, -2, 0f, dpi(6f)))
        return ScrollView(this).apply { addView(body) }
    }

    // ----- ARPEJADOR -----
    private fun fxArpPage(): View {
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dpi(10f), 0, dpi(10f), dpi(16f)) }
        val title = label("", 16f, TEXT, true)
        val partCard = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val quant = listOf("off", "32th", "16th triplet", "16th", "8th triplet", "8th", "4th triplet", "4th")
        val common = listOf(
            AP("Arp ligado (global)", 0x36, { 0x30 }, 0x04, false, 0, 1),
            AP("Tempo", 0x36, { 0x30 }, 0x02, true, 5, 300) { "$it BPM" },
            AP("Seleção SF", 0x36, { 0x30 }, 0x00, false, 0, 5) { "SF${it + 1}" },
            AP("Sync quantize", 0x36, { 0x30 }, 0x05, false, 0, 7) { quant[it] },
        )
        var c = fxCard(body, "ARPEJADOR — GLOBAL")
        for (a in common) c.addView(apRow(a, ACCENT))
        val pp = { Rig.selected }
        val part = listOf(
            AP("Arp ligado", 0x38, pp, 0x00, false, 0, 1), AP("Saída MIDI", 0x38, pp, 0x01, false, 0, 1),
            AP("Canal de saída", 0x38, pp, 0x02, false, 0, 15) { "${it + 1}" },
            AP("Tocar só o arp", 0x38, pp, 0x03, false, 0, 1), AP("Loop", 0x38, pp, 0x06, false, 0, 1),
            AP("Hold", 0x38, pp, 0x07, false, 0, 2) { listOf("sync-off", "off", "on")[it] },
            AP("Multiplicador de unidade", 0x38, pp, 0x08, false, 0, 6) { listOf("50%", "66%", "75%", "100%", "133%", "150%", "200%")[it] },
            AP("Nota mín", 0x38, pp, 0x09, false, 0, 127) { noteName(it) }, AP("Nota máx", 0x38, pp, 0x0A, false, 0, 127) { noteName(it) },
            AP("Velocity mín", 0x38, pp, 0x0B, false, 1, 127), AP("Velocity máx", 0x38, pp, 0x0C, false, 1, 127),
            AP("Modo de tecla", 0x38, pp, 0x0D, false, 0, 4) { listOf("sort", "thru", "direct", "sort+direct", "thru+direct")[it] },
            AP("Modo de velocity", 0x38, pp, 0x0E, false, 0, 1) { listOf("original", "thru")[it] },
            AP("Troca de tempo", 0x38, pp, 0x0F, false, 0, 1) { listOf("realtime", "compasso")[it] },
            AP("Quantize", 0x38, pp, 0x10, false, 1, 7) { quant[it] },
            AP("Força do quantize", 0x38, pp, 0x11, false, 0, 100) { "$it%" },
            AP("Velocity rate", 0x38, pp, 0x12, true, 0, 200) { "$it%" },
            AP("Gate time rate", 0x38, pp, 0x14, true, 0, 200) { "$it%" },
            AP("Swing", 0x38, pp, 0x1A, true, 8, 248) { (it - 128).let { v -> if (v > 0) "+$v" else "$v" } },
            AP("Faixa de oitavas", 0x38, pp, 0x1E, false, 0x3D, 0x43) { signed(it) },
            AP("Oitava de saída", 0x38, pp, 0x1F, false, 0x36, 0x4A) { signed(it) },
            AP("Trigger", 0x38, pp, 0x20, false, 0, 1) { listOf("gate", "toggle")[it] },
        )
        c = fxCard(body, "ARPEJADOR — PARTE SELECIONADA")
        fun reload() {
            title.text = "Parte ${Rig.selected + 1} · ${Rig.parts[Rig.selected].name}"
            while (c.childCount > 1) c.removeViewAt(1)
            c.addView(readButton(listOf(Triple(0x38, Rig.selected, 0), Triple(0x36, 0x30, 0)), "Ler arpejador do MX61"))
            for (a in part) c.addView(apRow(a, CH_COLORS[Rig.selected]))
        }
        page.addView(partChips { reload() })
        page.addView(title, lp(-2, -2, 0f, dpi(10f)))
        page.addView(ScrollView(this).apply { addView(body) }, lp(-1, 0, 1f))
        reload()
        return page
    }

    // ----- MASTER / PERFORMANCE -----
    private fun fxMasterPage(): View {
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dpi(10f), dpi(6f), dpi(10f), dpi(16f)) }
        body.addView(readButton(listOf(Triple(0, 0, 0), Triple(0x36, 0, 0)), "Ler master e performance do MX61"), lp(-1, -2, 0f, dpi(4f)))
        var c = fxCard(body, "MASTER"); val g = grid(c)
        g.addKnob(Knob(this, "VOLUME", 0, 127, Rig.masterVol, 127, ACCENT) { Rig.masterVolume(it) })
        g.addKnob(gKnob("TRANSP.", 0x00, 0x00, 0x07, 0x35, 0x4B, 0x40, true, fmt = { signed(it) }))
        g.addKnob(gKnob("OITAVA", 0x00, 0x00, 0x08, 0x3D, 0x43, 0x40, true, fmt = { signed(it) }))
        g.addKnob(gKnob("NOTE SHIFT", 0x00, 0x00, 0x01, 0x28, 0x58, 0x40, true, fmt = { signed(it) }))
        c = fxCard(body, "PERFORMANCE")
        val nameRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val et = EditText(this).apply {
            setText(Rig.performanceName() ?: ""); hint = "Nome (10 letras)"; setTextColor(TEXT); setHintTextColor(DIM); setSingleLine()
            filters = arrayOf(android.text.InputFilter.LengthFilter(10))
        }
        nameRow.addView(et, lp(0, -2, 1f)); nameRow.addView(button(this, "Enviar nome") { Rig.sendPerformanceName(et.text.toString()) })
        c.addView(nameRow)
        val perf = listOf(
            AP("Modo do teclado", 0x36, { 0 }, 0x41, false, 0, 2) { listOf("single", "layer", "split")[it] },
            AP("Ponto de split", 0x36, { 0 }, 0x42, false, 0, 127) { noteName(it) },
            AP("Link partes 1-2", 0x36, { 0 }, 0x44, false, 0, 1),
        )
        for (a in perf) c.addView(apRow(a, ACCENT))
        c = fxCard(body, "TECLADO E SISTEMA")
        val curves = listOf("Normal", "Soft", "Hard", "Wide", "Fixo")
        val sys = listOf(
            AP("Local ON", 0, { 0 }, 0x09, false, 0, 1), AP("Curva de velocity", 0, { 0 }, 0x11, false, 0, 4) { curves[it] },
            AP("Velocity fixa", 0, { 0 }, 0x12, false, 1, 127), AP("Enviar/receber Bank Select", 0, { 0 }, 0x14, false, 0, 1),
            AP("Enviar/receber Program Change", 0, { 0 }, 0x15, false, 0, 1), AP("Volume WAV (USB)", 0, { 0 }, 0x1D, false, 0, 127),
            AP("Direct monitor", 0, { 0 }, 0x10, false, 0, 1),
        )
        for (a in sys) c.addView(apRow(a, ACCENT))
        body.addView(label("Endereços do Data List oficial do MX49/MX61/MX88 (Model ID 7F 17).", 11f), lp(-1, -2, 0f, dpi(6f)))
        return ScrollView(this).apply { addView(body) }
    }

    // =============================== TECLADO ===============================
    private fun keysScreen(): View {
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val keys = KeysView(this, { Rig.selected }, 48)
        page.addView(partChips { })
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dpi(8f), 0, dpi(8f), dpi(6f)) }
        val oct = label("Oitava: C2", 13f, TEXT, true)
        fun upd() { oct.text = "Oitava: C${keys.baseNote / 12 - 1}" }
        bar.addView(button(this, "−") { if (keys.baseNote > 12) { keys.releaseAll(); keys.baseNote -= 12; upd(); keys.invalidate() } }, lp(dpi(52f), -2, 0f, dpi(3f)))
        bar.addView(oct, lp(-2, -2, 0f, dpi(8f)))
        bar.addView(button(this, "+") { if (keys.baseNote < 84) { keys.releaseAll(); keys.baseNote += 12; upd(); keys.invalidate() } }, lp(dpi(52f), -2, 0f, dpi(3f)))
        bar.addView(View(this), lp(0, 1, 1f))
        bar.addView(button(this, "PÂNICO", RED, 0xFF101218.toInt()) { Rig.panic() })
        page.addView(bar)
        page.addView(label("  Deslize entre as teclas; mais embaixo = mais forte (velocity).", 11f))
        page.addView(keys, lp(-1, 0, 1f, dpi(8f)))
        return page
    }

    // =============================== MIDI / CONEXÃO ===============================
    private fun midiScreen(): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dpi(14f), dpi(6f), dpi(14f), dpi(20f)) }
        fun card(title: String): LinearLayout {
            val c = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; background = roundRect(this@MainActivity, PANEL, 14f)
                setPadding(dpi(12f), dpi(10f), dpi(12f), dpi(12f))
            }
            c.addView(label(title, 11f, ACCENT, true), lp(-1, -2, 0f, dpi(4f)))
            col.addView(c, lp(-1, -2, 0f, dpi(5f)))
            return c
        }

        val conn = card("CONEXÃO")
        conn.addView(label("Ligue o MX61 ao celular com cabo USB (porta \"TO HOST\" do MX61 → adaptador OTG). Ele aparece abaixo.", 12f))
        if (!engine.supported) conn.addView(label("Este aparelho não suporta MIDI.", 13f, RED))
        val list = engine.devices()
        if (list.isEmpty()) conn.addView(label("Nenhum dispositivo MIDI encontrado.", 13f, AMBER), lp(-1, -2, 0f, dpi(6f)))
        for (d in list) {
            val isCur = engine.connectedName == engine.nameOf(d)
            conn.addView(button(this, (if (isCur) "✔ " else "") + engine.nameOf(d), if (isCur) ACCENT else PANEL2,
                if (isCur) 0xFF101218.toInt() else TEXT) {
                if (isCur) engine.close() else engine.open(d) { ok ->
                    Toast.makeText(this, if (ok) "Conectado" else "Falha ao abrir", Toast.LENGTH_SHORT).show()
                    show(4)
                }
            }, lp(-1, -2, 0f, dpi(4f)))
        }
        conn.addView(button(this, "Atualizar lista") { show(4) }, lp(-1, -2, 0f, dpi(4f)))

        val act = card("AÇÕES")
        act.addView(button(this, "Enviar todo o estado do app → MX61", ACCENT, 0xFF101218.toInt()) {
            Rig.syncAll(); Toast.makeText(this, "Enviado", Toast.LENGTH_SHORT).show()
        }, lp(-1, -2, 0f, dpi(4f)))
        act.addView(button(this, "Ler tudo do MX61 (efeitos, EQ, partes, arpejo)", PANEL2, ACCENT) {
            if (!engine.connected) Toast.makeText(this, "Conecte o MX61 primeiro", Toast.LENGTH_SHORT).show()
            else {
                Toast.makeText(this, "Lendo… (cerca de 6 s)", Toast.LENGTH_LONG).show()
                Rig.readBlocks(Rig.readAllBlocks()) { Toast.makeText(this, "Leitura concluída", Toast.LENGTH_SHORT).show() }
            }
        }, lp(-1, -2, 0f, dpi(4f)))
        act.addView(button(this, "Device number do MX61: ${Rig.dev + 1}  ▾") { }.also { b ->
            b.setOnClickListener {
                AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle("Device number (padrão: 1)")
                    .setItems(Array(16) { "${it + 1}" }) { _, i -> Rig.dev = i; b.text = "Device number do MX61: ${i + 1}  ▾" }.show()
            }
        }, lp(-1, -2, 0f, dpi(4f)))
        act.addView(button(this, "Parar todos os sons (All Notes/Sound Off)") { Rig.panic() }, lp(-1, -2, 0f, dpi(4f)))
        act.addView(button(this, "Resetar controladores (CC121)") { Rig.resetControllers() }, lp(-1, -2, 0f, dpi(4f)))
        act.addView(button(this, "GM System On (cuidado: reinicia o modo)") {
            AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK).setMessage("Enviar GM System On? O MX61 pode trocar de modo.")
                .setPositiveButton("Enviar") { _, _ -> Rig.gmOn() }.setNegativeButton("Cancelar", null).show()
        }, lp(-1, -2, 0f, dpi(4f)))

        val sx = card("SYSEX MANUAL")
        sx.addView(label("Formato do MX61: F0 43 10 7F 17 <3 bytes de endereço> <dado> F7. Ex.: F0 43 10 7F 17 36 01 00 01 00 F7 = reverb REV-X Hall. Também aceita qualquer outro SysEx.", 12f))
        val et = EditText(this).apply {
            setTextColor(TEXT); setHintTextColor(DIM); hint = "F0 .. F7"; textSize = 13f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            background = roundRect(this@MainActivity, PANEL2, 10f); setPadding(dpi(10f), dpi(8f), dpi(10f), dpi(8f))
        }
        sx.addView(et, lp(-1, -2, 0f, dpi(4f)))
        sx.addView(button(this, "Enviar SysEx") {
            val b = Rig.parseSysex(et.text.toString())
            if (b == null) Toast.makeText(this, "SysEx inválido (precisa começar com F0 e terminar com F7)", Toast.LENGTH_LONG).show()
            else { engine.sendBytes(b); Toast.makeText(this, "Enviado (${b.size} bytes)", Toast.LENGTH_SHORT).show() }
        })
        return ScrollView(this).apply { addView(col) }
    }

    // =============================== VOZ ===============================
    private val banks = listOf(
        "Preset 1" to (63 to 0), "Preset 2" to (63 to 1), "Preset 3" to (63 to 2), "Preset 4" to (63 to 3),
        "Preset 5" to (63 to 4), "Preset 6" to (63 to 5), "Preset 7" to (63 to 6), "Preset 8" to (63 to 7),
        "User" to (63 to 8), "GM" to (0 to 0), "GM Drum" to (127 to 0), "Drum Preset" to (63 to 32), "Drum User" to (63 to 40),
        "Performance User" to (63 to 80),
    )

    private fun bankName(msb: Int, lsb: Int) = banks.firstOrNull { it.second == (msb to lsb) }?.first ?: "MSB $msb · LSB $lsb"

    private fun voiceDialog(p: Part, done: () -> Unit) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dpi(20f), dpi(10f), dpi(20f), 0) }
        val name = EditText(this).apply { setText(p.name); setSingleLine(); hint = "Nome da track" }
        box.addView(name)
        var msb = p.msb; var lsb = p.lsb
        val bankBtn = button(this, "Banco: ${bankName(msb, lsb)}  ▾") { }
        box.addView(bankBtn, lp(-1, -2, 0f, dpi(6f)))
        val pickers = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        c.addView(label("Número da voz", 11f).apply { gravity = Gravity.CENTER })
        val pc = NumberPicker(this).apply { minValue = 1; maxValue = 128; value = p.pc + 1; wrapSelectorWheel = true }
        c.addView(pc); pickers.addView(c, lp(0, -2, 1f))
        val rxBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        rxBox.addView(label("Canal RX", 11f).apply { gravity = Gravity.CENTER })
        val rx = NumberPicker(this).apply {
            minValue = 1; maxValue = 17; displayedValues = Array(17) { if (it == 16) "OFF" else "${it + 1}" }
            val cur = Rig.get(0x37, p.idx, 0x04, p.idx); value = if (cur == 0x7F) 17 else (cur + 1).coerceIn(1, 16)
        }
        rxBox.addView(rx); pickers.addView(rxBox, lp(0, -2, 1f))
        bankBtn.setOnClickListener {
            AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle("Banco")
                .setItems(banks.map { it.first }.toTypedArray()) { _, i ->
                    msb = banks[i].second.first; lsb = banks[i].second.second
                    bankBtn.text = "Banco: ${banks[i].first}  ▾"
                }.show()
        }
        box.addView(pickers, lp(-1, -2, 0f, dpi(8f)))
        AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("Parte ${p.idx + 1} — voz").setView(box)
            .setPositiveButton("Aplicar") { _, _ ->
                p.name = name.text.toString().ifBlank { "Part ${p.idx + 1}" }
                p.msb = msb; p.lsb = lsb; p.pc = pc.value - 1
                Rig.sendVoice(p)
                Rig.set1(0x37, p.idx, 0x04, if (rx.value == 17) 0x7F else rx.value - 1)
                done()
            }.setNegativeButton("Cancelar", null).show()
    }
}
