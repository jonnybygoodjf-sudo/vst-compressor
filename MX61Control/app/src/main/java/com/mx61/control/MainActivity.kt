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
        engine.onMessage = { s, d1, d2 -> Rig.incoming(s, d1, d2) }
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

    private val reverbTypes = listOf(
        "Sem efeito" to (0 to 0), "REV-X Hall" to (1 to 0x00), "R3 Hall" to (1 to 0x01), "SPX Hall" to (1 to 0x02),
        "REV-X Room" to (1 to 0x10), "R3 Room" to (1 to 0x11), "SPX Room" to (1 to 0x12), "R3 Plate" to (1 to 0x20),
        "SPX Stage" to (1 to 0x30), "Space Simulator" to (1 to 0x40),
    )
    private val chorusTypes = listOf(
        "Sem efeito" to (0 to 0),
        "Cross Delay" to (2 to 0x00), "Tempo Cross Delay" to (2 to 0x10), "Tempo Delay Mono" to (2 to 0x20),
        "Tempo Delay Stereo" to (2 to 0x28), "Control Delay" to (2 to 0x30), "Delay LR" to (2 to 0x40),
        "Delay LCR" to (2 to 0x50), "Delay LR (Stereo)" to (2 to 0x48),
        "G Chorus" to (3 to 0x00), "2 Modulator" to (3 to 0x10), "SPX Chorus" to (3 to 0x20),
        "Symphonic" to (3 to 0x30), "Ensemble Detune" to (3 to 0x40),
        "VCM Flanger" to (4 to 0x00), "Classic Flanger" to (4 to 0x10), "Tempo Flanger" to (4 to 0x20),
        "VCM Phaser Mono" to (5 to 0x00), "VCM Phaser Stereo" to (5 to 0x10), "Tempo Phaser" to (5 to 0x20),
        "Auto Pan" to (6 to 0x00), "Tremolo" to (6 to 0x10), "Rotary Speaker" to (6 to 0x20),
    )

    private fun fxCard(body: LinearLayout, title: String): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = roundRect(this@MainActivity, PANEL, 14f)
            setPadding(dpi(10f), dpi(8f), dpi(10f), dpi(8f))
        }
        card.addView(label(title, 11f, ACCENT, true))
        body.addView(card, lp(-1, -2, 0f, dpi(4f)))
        return card
    }

    private fun grid(card: LinearLayout): GridLayout {
        val g = GridLayout(this).apply { columnCount = 4 }
        card.addView(g); return g
    }

    private fun GridLayout.addKnob(k: Knob) = addView(k, GridLayout.LayoutParams().apply {
        width = dpi(76f); height = dpi(84f); setMargins(dpi(2f), dpi(2f), dpi(2f), dpi(2f))
    })

    /** Knob ligado a um parâmetro SysEx global. */
    private fun gKnob(l: String, h: Int, m: Int, lo: Int, min: Int, max: Int, def: Int, bip: Boolean = false, color: Int = ACCENT) =
        Knob(this, l, min, max, Rig.gVal(h, m, lo, def), def, color, bip) { Rig.setGlobal(h, m, lo, it) }

    private fun typeButton(card: LinearLayout, block: Int, title: String, list: List<Pair<String, Pair<Int, Int>>>) {
        val cur = Rig.types[block]
        fun nameOf(v: Int?) = list.firstOrNull { it.second.first * 128 + it.second.second == v }?.first ?: "—"
        val b = button(this, "$title: ${nameOf(cur)}  ▾") { }
        b.setOnClickListener {
            AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle(title)
                .setItems(list.map { it.first }.toTypedArray()) { _, i ->
                    val (msb, lsb) = list[i].second
                    Rig.setType(block, msb, lsb)
                    b.text = "$title: ${list[i].first}  ▾"
                }.show()
        }
        card.addView(b, lp(-1, -2, 0f, dpi(4f)))
    }

    private fun fxScreen(): View {
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val holder = FrameLayout(this)
        val seg = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dpi(8f), dpi(6f), dpi(8f), 0) }
        val segNames = listOf("PARTE", "REVERB/CHORUS", "EQ MASTER", "MASTER")
        val segViews = ArrayList<TextView>()
        fun paintSeg() = segViews.forEachIndexed { i, t ->
            t.background = roundRect(this, if (i == fxPage) ACCENT else PANEL2, 10f)
            t.setTextColor(if (i == fxPage) 0xFF101218.toInt() else TEXT)
        }
        fun build() {
            Rig.onPartChanged = null
            holder.removeAllViews()
            holder.addView(when (fxPage) { 0 -> fxPartPage(); 1 -> fxEffectsPage(); 2 -> fxEqPage(); else -> fxMasterPage() }, FrameLayout.LayoutParams(-1, -1))
            paintSeg()
        }
        segNames.forEachIndexed { i, n ->
            val t = TextView(this).apply {
                text = n; gravity = Gravity.CENTER; textSize = 11f; setTypeface(null, Typeface.BOLD)
                setPadding(0, dpi(9f), 0, dpi(9f)); setOnClickListener { fxPage = i; build() }
            }
            segViews.add(t); seg.addView(t, lp(0, -2, 1f, dpi(2f)))
        }
        page.addView(seg)
        page.addView(holder, lp(-1, 0, 1f))
        build()
        return page
    }

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
        fun sxKnob(g: GridLayout, lbl: String, lo: Int, min: Int, max: Int, def: Int, bip: Boolean = false) {
            val k = Knob(this, lbl, min, max, cur().sx[lo].let { if (it < 0) def else it }, def, CH_COLORS[Rig.selected], bip) {
                Rig.setPartParam(cur(), lo, it)
            }
            knobs.add { k.set(cur().sx[lo].let { v -> if (v < 0) def else v }, false) }; g.addKnob(k)
        }

        var c = fxCard(body, "ENVIOS DE EFEITO"); var g = grid(c)
        ccKnob(g, "REVERB", 91, 40); ccKnob(g, "CHORUS", 93, 0); sxKnob(g, "DRY", 0x15, 0, 127, 127)
        c = fxCard(body, "FILTRO (offset ±)"); g = grid(c)
        ccKnob(g, "CUTOFF", 74, 64, true); ccKnob(g, "RESSON.", 71, 64, true)
        c = fxCard(body, "ENVELOPE DE AMPLITUDE (±)"); g = grid(c)
        ccKnob(g, "ATTACK", 73, 64, true); ccKnob(g, "DECAY", 75, 64, true); ccKnob(g, "SUSTAIN", 31, 64, true); ccKnob(g, "RELEASE", 72, 64, true)
        c = fxCard(body, "ENVELOPE DE FILTRO (±) — SysEx"); g = grid(c)
        sxKnob(g, "ATTACK", 0x27, 0, 127, 64, true); sxKnob(g, "DECAY", 0x28, 0, 127, 64, true)
        sxKnob(g, "SUSTAIN", 0x29, 0, 127, 64, true); sxKnob(g, "RELEASE", 0x2A, 0, 127, 64, true)
        sxKnob(g, "DEPTH", 0x2B, 0, 127, 64, true)
        c = fxCard(body, "EXPRESSÃO"); g = grid(c)
        ccKnob(g, "VOLUME", 7, 100); ccKnob(g, "PAN", 10, 64, true); ccKnob(g, "EXPRESS.", 11, 127)
        ccKnob(g, "MOD", 1, 0); ccKnob(g, "PORTAM.", 5, 0)
        sxKnob(g, "VEL.DEPTH", 0x0C, 0, 127, 64); sxKnob(g, "VEL.OFFS", 0x0D, 0, 127, 64)
        sxKnob(g, "TRANSP.", 0x16, 0x28, 0x58, 0x40, true)
        val sw = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dpi(6f), 0, 0) }
        val port = pill(this, "Portamento", ACCENT, cur().cc[65] >= 64) { Rig.setCC(cur(), 65, if (it) 127 else 0) }
        val sus = pill(this, "Sustain", ACCENT, cur().cc[64] >= 64) { Rig.setCC(cur(), 64, if (it) 127 else 0) }
        val mono = pill(this, "Mono", ACCENT, cur().sx[0x05] == 0) { Rig.setPartParam(cur(), 0x05, if (it) 0 else 1) }
        sw.addView(port, lp(-2, -2, 0f, dpi(3f))); sw.addView(sus, lp(-2, -2, 0f, dpi(3f))); sw.addView(mono, lp(-2, -2, 0f, dpi(3f)))
        c.addView(sw)
        knobs.add { setPill(port, cur().cc[65] >= 64); setPill(sus, cur().cc[64] >= 64); setPill(mono, cur().sx[0x05] == 0) }

        fun reload() { title.text = "Parte ${cur().idx + 1} · ${cur().name}"; knobs.forEach { it() } }
        page.addView(partChips { reload() })
        page.addView(title, lp(-2, -2, 0f, dpi(10f)))
        page.addView(ScrollView(this).apply { addView(body) }, lp(-1, 0, 1f))
        reload()
        Rig.onPartChanged = { i -> if (i == Rig.selected) reload() }
        return page
    }

    private fun fxEffectsPage(): View {
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dpi(10f), dpi(6f), dpi(10f), dpi(16f)) }
        var c = fxCard(body, "REVERB (efeito de sistema)")
        typeButton(c, 0x01, "Tipo", reverbTypes)
        var g = grid(c)
        g.addKnob(gKnob("RETORNO", 0x36, 0x01, 0x24, 0, 127, 0x40)); g.addKnob(gKnob("PAN", 0x36, 0x01, 0x25, 1, 127, 0x40, true))
        c = fxCard(body, "CHORUS / DELAY / MODULAÇÃO (efeito de sistema)")
        typeButton(c, 0x02, "Tipo", chorusTypes)
        g = grid(c)
        g.addKnob(gKnob("RETORNO", 0x36, 0x02, 0x24, 0, 127, 0x60)); g.addKnob(gKnob("PAN", 0x36, 0x02, 0x25, 1, 127, 0x40, true))
        g.addKnob(gKnob("→ REVERB", 0x36, 0x02, 0x26, 0, 127, 0))
        body.addView(label("Os envios por parte (REVERB/CHORUS) ficam na página PARTE. Ao trocar o tipo, o MX61 carrega os parâmetros padrão do efeito.", 11f),
            lp(-1, -2, 0f, dpi(6f)))
        return ScrollView(this).apply { addView(body) }
    }

    private fun fxEqPage(): View {
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dpi(10f), dpi(6f), dpi(10f), dpi(16f)) }
        var c = fxCard(body, "MASTER EQ — GANHO (−12 a +12 dB)"); var g = grid(c)
        g.addKnob(gKnob("LOW", 0x36, 0x10, 0x00, 0x34, 0x4C, 0x40, true)); g.addKnob(gKnob("LOW-MID", 0x36, 0x10, 0x04, 0x34, 0x4C, 0x40, true))
        g.addKnob(gKnob("MID", 0x36, 0x10, 0x08, 0x34, 0x4C, 0x40, true)); g.addKnob(gKnob("HI-MID", 0x36, 0x10, 0x0C, 0x34, 0x4C, 0x40, true))
        g.addKnob(gKnob("HIGH", 0x36, 0x10, 0x10, 0x34, 0x4C, 0x40, true))
        c = fxCard(body, "MASTER EQ — FREQUÊNCIA (índice da tabela)"); g = grid(c)
        g.addKnob(gKnob("LOW", 0x36, 0x10, 0x01, 0x04, 0x28, 0x0C)); g.addKnob(gKnob("LOW-MID", 0x36, 0x10, 0x05, 0x0E, 0x36, 0x14))
        g.addKnob(gKnob("MID", 0x36, 0x10, 0x09, 0x0E, 0x36, 0x1C)); g.addKnob(gKnob("HI-MID", 0x36, 0x10, 0x0D, 0x0E, 0x36, 0x2C))
        g.addKnob(gKnob("HIGH", 0x36, 0x10, 0x11, 0x1C, 0x3A, 0x34))
        c = fxCard(body, "MASTER EQ — Q"); g = grid(c)
        g.addKnob(gKnob("LOW", 0x36, 0x10, 0x02, 1, 0x78, 7)); g.addKnob(gKnob("LOW-MID", 0x36, 0x10, 0x06, 1, 0x78, 7))
        g.addKnob(gKnob("MID", 0x36, 0x10, 0x0A, 1, 0x78, 7)); g.addKnob(gKnob("HI-MID", 0x36, 0x10, 0x0E, 1, 0x78, 7))
        g.addKnob(gKnob("HIGH", 0x36, 0x10, 0x12, 1, 0x78, 7))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dpi(6f), 0, 0) }
        row.addView(pill(this, "Low = peaking", ACCENT, Rig.gVal(0x36, 0x10, 0x03, 0) == 1) { Rig.setGlobal(0x36, 0x10, 0x03, if (it) 1 else 0) }, lp(-2, -2, 0f, dpi(3f)))
        row.addView(pill(this, "High = peaking", ACCENT, Rig.gVal(0x36, 0x10, 0x13, 0) == 1) { Rig.setGlobal(0x36, 0x10, 0x13, if (it) 1 else 0) }, lp(-2, -2, 0f, dpi(3f)))
        c.addView(row)
        body.addView(button(this, "Zerar EQ (0 dB)") {
            for (lo in intArrayOf(0x00, 0x04, 0x08, 0x0C, 0x10)) Rig.setGlobal(0x36, 0x10, lo, 0x40)
            fxPage = 2; show(2)
        }, lp(-1, -2, 0f, dpi(6f)))
        return ScrollView(this).apply { addView(body) }
    }

    private fun fxMasterPage(): View {
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dpi(10f), dpi(6f), dpi(10f), dpi(16f)) }
        var c = fxCard(body, "MASTER"); val g = grid(c)
        g.addKnob(Knob(this, "VOLUME", 0, 127, Rig.masterVol, 127, ACCENT) { Rig.masterVolume(it) })
        g.addKnob(gKnob("TRANSP.", 0x00, 0x00, 0x07, 0x35, 0x4B, 0x40, true))
        g.addKnob(gKnob("OITAVA", 0x00, 0x00, 0x08, 0x3D, 0x43, 0x40, true))
        g.addKnob(gKnob("NOTE SHIFT", 0x00, 0x00, 0x01, 0x28, 0x58, 0x40, true))
        c = fxCard(body, "TECLADO")
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(pill(this, "Local ON", ACCENT, Rig.gVal(0, 0, 0x09, 1) == 1) { Rig.setGlobal(0, 0, 0x09, if (it) 1 else 0) }, lp(-2, -2, 0f, dpi(3f)))
        c.addView(row)
        val curves = listOf("Normal", "Soft", "Hard", "Wide", "Fixo")
        val vb = button(this, "Curva de velocity: ${curves[Rig.gVal(0, 0, 0x11, 0).coerceIn(0, 4)]}  ▾") { }
        vb.setOnClickListener {
            AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK).setTitle("Curva de velocity")
                .setItems(curves.toTypedArray()) { _, i -> Rig.setGlobal(0, 0, 0x11, i); vb.text = "Curva de velocity: ${curves[i]}  ▾" }.show()
        }
        c.addView(vb, lp(-1, -2, 0f, dpi(6f)))
        val g2 = grid(c)
        g2.addKnob(gKnob("VEL. FIXA", 0, 0, 0x12, 1, 127, 0x40))
        body.addView(label("Endereços conforme o Data List oficial do MX49/MX61/MX88 (Model ID 7F 17, device number 1).", 11f), lp(-1, -2, 0f, dpi(6f)))
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
                Rig.sendVoice(p); done()
            }.setNegativeButton("Cancelar", null).show()
    }
}
