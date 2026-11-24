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

    private fun fxScreen(): View {
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dpi(10f), 0, dpi(10f), dpi(16f)) }
        val knobs = ArrayList<Pair<Int, Knob>>()
        val title = label("", 16f, TEXT, true)

        fun section(name: String, items: List<Triple<String, Int, Int>>) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; background = roundRect(this@MainActivity, PANEL, 14f)
                setPadding(dpi(10f), dpi(8f), dpi(10f), dpi(8f))
            }
            card.addView(label(name, 11f, ACCENT, true))
            val grid = GridLayout(this).apply { columnCount = 4 }
            for ((lbl, cc, def) in items) {
                val k = Knob(this, lbl, 0, 127, Rig.parts[Rig.selected].cc[cc], def, CH_COLORS[Rig.selected]) {
                    Rig.setCC(Rig.parts[Rig.selected], cc, it)
                }
                knobs.add(cc to k)
                grid.addView(k, GridLayout.LayoutParams().apply {
                    width = dpi(76f); height = dpi(84f); setMargins(dpi(2f), dpi(2f), dpi(2f), dpi(2f))
                })
            }
            card.addView(grid)
            body.addView(card, lp(-1, -2, 0f, dpi(4f)))
        }

        // master
        val master = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = roundRect(this@MainActivity, PANEL, 14f); setPadding(dpi(10f), dpi(8f), dpi(10f), dpi(8f))
        }
        master.addView(label("MASTER", 11f, ACCENT, true), lp(-2, -2, 0f, dpi(4f)))
        master.addView(Knob(this, "VOLUME", 0, 127, Rig.masterVol, 127, ACCENT) { Rig.masterVolume(it) }, lp(dpi(76f), dpi(84f)))
        master.addView(View(this), lp(0, 1, 1f))
        master.addView(button(this, "Sustain") { }.also { b ->
            b.setOnClickListener {
                val on = b.tag != true; b.tag = on
                val p = Rig.parts[Rig.selected]; Rig.setCC(p, 64, if (on) 127 else 0)
                b.background = roundRect(this, if (on) ACCENT else PANEL2, 10f); b.setTextColor(if (on) 0xFF101218.toInt() else TEXT)
            }
        })
        body.addView(master, lp(-1, -2, 0f, dpi(4f)))

        section("EFEITOS — ENVIOS DA PARTE", listOf(Triple("REVERB", 91, 40), Triple("CHORUS", 93, 0), Triple("VARIAÇÃO", 94, 0)))
        section("FILTRO", listOf(Triple("CUTOFF", 74, 64), Triple("RESSON.", 71, 64)))
        section("ENVELOPE", listOf(Triple("ATTACK", 73, 64), Triple("DECAY", 75, 64), Triple("RELEASE", 72, 64)))
        section("EXPRESSÃO", listOf(Triple("VOLUME", 7, 100), Triple("PAN", 10, 64), Triple("EXPRESS.", 11, 127),
            Triple("MOD", 1, 0), Triple("PORTAM.", 5, 0)))

        fun reload() {
            val p = Rig.parts[Rig.selected]
            title.text = "Parte ${p.idx + 1} · ${p.name}"
            for ((cc, k) in knobs) k.set(p.cc[cc], false)
        }
        page.addView(partChips { reload() })
        page.addView(title, lp(-2, -2, 0f, dpi(10f)))
        page.addView(ScrollView(this).apply { addView(body) }, lp(-1, 0, 1f))
        reload()
        Rig.onPartChanged = { i -> if (i == Rig.selected) reload() }
        return page
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
        sx.addView(label("Cole um SysEx em hexadecimal (ex.: F0 43 10 ... F7) do Data List do MX61 para parâmetros que não têm CC, como tipo de efeito.", 12f))
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
    private fun voiceDialog(p: Part, done: () -> Unit) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dpi(20f), dpi(10f), dpi(20f), 0) }
        val name = EditText(this).apply { setText(p.name); setSingleLine(); hint = "Nome da track" }
        box.addView(name)
        val pickers = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        fun picker(lbl: String, lo: Int, hi: Int, v: Int): NumberPicker {
            val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
            c.addView(label(lbl, 11f).apply { gravity = Gravity.CENTER })
            val np = NumberPicker(this).apply { minValue = lo; maxValue = hi; value = v; wrapSelectorWheel = true }
            c.addView(np); pickers.addView(c, lp(0, -2, 1f)); return np
        }
        val msb = picker("Bank MSB", 0, 127, p.msb)
        val lsb = picker("Bank LSB", 0, 127, p.lsb)
        val pc = picker("Program", 1, 128, p.pc + 1)
        box.addView(pickers, lp(-1, -2, 0f, dpi(8f)))
        AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("Parte ${p.idx + 1} — voz").setView(box)
            .setPositiveButton("Aplicar") { _, _ ->
                p.name = name.text.toString().ifBlank { "Part ${p.idx + 1}" }
                p.msb = msb.value; p.lsb = lsb.value; p.pc = pc.value - 1
                Rig.sendVoice(p); done()
            }.setNegativeButton("Cancelar", null).show()
    }
}
