package com.teleprompter.rec

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.SystemClock
import android.provider.MediaStore
import android.view.Choreographer
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var editorPanel: View
    private lateinit var prompterPanel: View
    private lateinit var scriptInput: EditText
    private lateinit var fontSeek: SeekBar
    private lateinit var speedSeek: SeekBar
    private lateinit var opacitySeek: SeekBar
    private lateinit var fontLabel: TextView
    private lateinit var speedLabel: TextView
    private lateinit var opacityLabel: TextView
    private lateinit var radioFront: RadioButton
    private lateinit var radioBack: RadioButton
    private lateinit var qualitySpinner: Spinner
    private lateinit var radioLandscape: RadioButton
    private lateinit var settingsToggle: Button
    private lateinit var settingsScroll: View
    private lateinit var editorLeft: View
    private lateinit var editorRight: View
    private var settingsOpen = false
    private lateinit var prompterScroll: ScrollView
    private lateinit var prompterText: TextView
    private lateinit var timerText: TextView
    private lateinit var countdownText: TextView
    private lateinit var recButton: Button
    private lateinit var guideLine: View

    private val prefs by lazy { getSharedPreferences("teleprompter", Context.MODE_PRIVATE) }

    // Camera
    private var cameraProvider: ProcessCameraProvider? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var supportedQualities: List<Quality> = emptyList()
    private var bindingSpinner = false

    // Rolagem
    private var scrolling = false
    private var scrollPos = 0f
    private var lastFrameNanos = 0L
    private var countdown: CountDownTimer? = null
    private var recordStartMs = 0L

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (scrolling) {
                if (lastFrameNanos != 0L) {
                    val dt = (frameTimeNanos - lastFrameNanos) / 1_000_000_000f
                    scrollPos += pxPerSecond() * dt
                    val max = (prompterText.height - prompterScroll.height).coerceAtLeast(0)
                    if (scrollPos >= max) {
                        scrollPos = max.toFloat()
                        scrolling = false // chegou ao fim
                    }
                    prompterScroll.scrollTo(0, scrollPos.toInt())
                }
                lastFrameNanos = frameTimeNanos
                Choreographer.getInstance().postFrameCallback(this)
            } else {
                lastFrameNanos = 0L
            }
        }
    }

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (recording != null) {
                val s = (SystemClock.elapsedRealtime() - recordStartMs) / 1000
                timerText.text = "● %02d:%02d".format(s / 60, s % 60)
                timerText.postDelayed(this, 500)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        previewView = findViewById(R.id.previewView)
        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        editorPanel = findViewById(R.id.editorPanel)
        prompterPanel = findViewById(R.id.prompterPanel)
        scriptInput = findViewById(R.id.scriptInput)
        fontSeek = findViewById(R.id.fontSeek)
        speedSeek = findViewById(R.id.speedSeek)
        opacitySeek = findViewById(R.id.opacitySeek)
        fontLabel = findViewById(R.id.fontLabel)
        speedLabel = findViewById(R.id.speedLabel)
        opacityLabel = findViewById(R.id.opacityLabel)
        radioFront = findViewById(R.id.radioFront)
        radioBack = findViewById(R.id.radioBack)
        qualitySpinner = findViewById(R.id.qualitySpinner)
        radioLandscape = findViewById(R.id.radioLandscape)
        settingsToggle = findViewById(R.id.settingsToggle)
        settingsScroll = findViewById(R.id.settingsScroll)
        editorLeft = findViewById(R.id.editorLeft)
        editorRight = findViewById(R.id.editorRight)
        settingsToggle.setOnClickListener { settingsOpen = !settingsOpen; layoutEditor() }
        layoutEditor()
        findViewById<RadioButton>(R.id.radioPortrait).isChecked = !prefs.getBoolean("landscape", false)
        radioLandscape.isChecked = prefs.getBoolean("landscape", false)
        applyOrientation()
        findViewById<RadioGroup>(R.id.orientationGroup).setOnCheckedChangeListener { _, _ ->
            prefs.edit().putBoolean("landscape", radioLandscape.isChecked).apply()
            applyOrientation()
        }
        prompterScroll = findViewById(R.id.prompterScroll)
        prompterText = findViewById(R.id.prompterText)
        timerText = findViewById(R.id.timerText)
        countdownText = findViewById(R.id.countdownText)
        recButton = findViewById(R.id.recButton)
        guideLine = findViewById(R.id.guideLine)

        // Restaura configuracoes
        scriptInput.setText(prefs.getString("text", ""))
        fontSeek.progress = prefs.getInt("font", 20)       // 30..100 sp => progress+30
        speedSeek.progress = prefs.getInt("speed", 30)     // 1..100 => progress+1
        opacitySeek.progress = prefs.getInt("opacity", 45) // 10..100 => progress+10
        if (prefs.getBoolean("back", false)) radioBack.isChecked = true else radioFront.isChecked = true

        val labels = SimpleSeek {
            fontLabel.text = "Tamanho da letra: ${fontSeek.progress + 30} sp"
            speedLabel.text = "Velocidade de rolagem: ${speedSeek.progress + 1}"
            opacityLabel.text = "Brilho da câmera ao fundo: ${opacitySeek.progress + 10}%"
            previewView.alpha = (opacitySeek.progress + 10) / 100f
        }
        fontSeek.setOnSeekBarChangeListener(labels)
        speedSeek.setOnSeekBarChangeListener(labels)
        opacitySeek.setOnSeekBarChangeListener(labels)
        labels.update()

        findViewById<RadioGroup>(R.id.cameraGroup).setOnCheckedChangeListener { _, _ ->
            if (recording == null) bindCamera()
        }
        qualitySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (bindingSpinner) return
                prefs.edit().putInt("quality_${if (isBack()) "b" else "f"}", pos).apply()
                if (recording == null) bindCamera()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        findViewById<Button>(R.id.startButton).setOnClickListener { openPrompter() }
        findViewById<Button>(R.id.backButton).setOnClickListener { closePrompter() }
        findViewById<Button>(R.id.rewindButton).setOnClickListener { rewind() }
        findViewById<Button>(R.id.slowerButton).setOnClickListener { changeSpeed(-5) }
        findViewById<Button>(R.id.fasterButton).setOnClickListener { changeSpeed(+5) }
        findViewById<Button>(R.id.pauseButton).setOnClickListener { toggleScroll() }
        prompterText.setOnClickListener { toggleScroll() }
        recButton.setOnClickListener { onRecClicked() }

        if (hasPermissions()) bindCamera() else requestPermissions(requiredPermissions(), 10)
    }

    private fun applyOrientation() {
        requestedOrientation = if (radioLandscape.isChecked)
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    // A tela girou: reabre a camera para o video sair na rotacao certa
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        layoutEditor()
        if (recording == null) bindCamera()
    }

    // Retrato: texto em cima, ajustes recolhiveis embaixo. Paisagem: texto | ajustes lado a lado.
    private fun layoutEditor() {
        val land = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val panel = editorPanel as LinearLayout
        val h = resources.displayMetrics.heightPixels
        val m = (10 * resources.displayMetrics.density).toInt()
        val match = LinearLayout.LayoutParams.MATCH_PARENT
        val wrap = LinearLayout.LayoutParams.WRAP_CONTENT
        if (land) {
            panel.orientation = LinearLayout.HORIZONTAL
            editorLeft.layoutParams = LinearLayout.LayoutParams(0, match, 1f)
            editorRight.layoutParams = LinearLayout.LayoutParams(0, match, 1f).also { it.marginStart = m }
            (editorRight as LinearLayout).let { r ->
                settingsScroll.visibility = View.VISIBLE
                settingsScroll.layoutParams = LinearLayout.LayoutParams(match, 0, 1f)
                r.requestLayout()
            }
            settingsToggle.visibility = View.GONE
        } else {
            panel.orientation = LinearLayout.VERTICAL
            editorLeft.layoutParams = LinearLayout.LayoutParams(match, 0, 1f)
            editorRight.layoutParams = LinearLayout.LayoutParams(match, wrap)
            settingsScroll.layoutParams = LinearLayout.LayoutParams(match, (h * 0.42f).toInt())
            settingsScroll.visibility = if (settingsOpen) View.VISIBLE else View.GONE
            settingsToggle.visibility = View.VISIBLE
            settingsToggle.text = if (settingsOpen) "✕  Fechar" else "⚙  Ajustes"
        }
        (settingsScroll.layoutParams as LinearLayout.LayoutParams).topMargin = m
    }

    private fun isBack() = radioBack.isChecked

    // ---------------------------------------------------------------- permissoes
    private fun requiredPermissions(): Array<String> {
        val l = mutableListOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) l += Manifest.permission.WRITE_EXTERNAL_STORAGE
        return l.toTypedArray()
    }

    private fun hasPermissions() = requiredPermissions().all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (hasPermissions()) bindCamera()
        else Toast.makeText(this, "Preciso das permissões de câmera, microfone e armazenamento.", Toast.LENGTH_LONG).show()
    }

    // ---------------------------------------------------------------- camera
    private fun bindCamera() {
        if (!hasPermissions()) return
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            cameraProvider = provider
            val selector = if (isBack()) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
            if (!provider.hasCamera(selector)) {
                Toast.makeText(this, "Essa câmera não existe neste aparelho.", Toast.LENGTH_SHORT).show()
                return@addListener
            }
            val camInfo = provider.bindToLifecycle(this, selector).cameraInfo
            supportedQualities = QualitySelector.getSupportedQualities(camInfo)
                .sortedBy { order(it) } // melhor primeiro

            // Preenche o seletor de qualidade com o que o aparelho oferece
            bindingSpinner = true
            qualitySpinner.adapter = ArrayAdapter(
                this, R.layout.spinner_item,
                supportedQualities.mapIndexed { i, q -> name(q) + if (i == 0) "  (máxima)" else "" }
            ).also { it.setDropDownViewResource(R.layout.spinner_item) }
            val saved = prefs.getInt("quality_${if (isBack()) "b" else "f"}", 0)
            qualitySpinner.setSelection(saved.coerceIn(0, (supportedQualities.size - 1).coerceAtLeast(0)))
            bindingSpinner = false

            val chosen = supportedQualities.getOrNull(qualitySpinner.selectedItemPosition) ?: Quality.HIGHEST
            val recorder = Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.from(chosen, FallbackStrategy.lowerQualityOrHigherThan(chosen))
                )
                .build()
            val vc = VideoCapture.withOutput(recorder)
            videoCapture = vc
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }

            provider.unbindAll()
            try {
                provider.bindToLifecycle(this, selector, preview, vc)
            } catch (e: Exception) {
                Toast.makeText(this, "Erro ao abrir câmera: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun order(q: Quality) = when (q) {
        Quality.UHD -> 0; Quality.FHD -> 1; Quality.HD -> 2; else -> 3
    }

    private fun name(q: Quality) = when (q) {
        Quality.UHD -> "4K (2160p)"; Quality.FHD -> "Full HD (1080p)"
        Quality.HD -> "HD (720p)"; Quality.SD -> "SD (480p)"; else -> q.toString()
    }

    // ---------------------------------------------------------------- telas
    private fun openPrompter() {
        val text = scriptInput.text.toString().ifBlank { "(Sem texto. Volte e digite o seu roteiro.)" }
        prefs.edit()
            .putString("text", scriptInput.text.toString())
            .putInt("font", fontSeek.progress).putInt("speed", speedSeek.progress)
            .putInt("opacity", opacitySeek.progress).putBoolean("back", isBack())
            .apply()
        window.decorView.hideKeyboard()

        prompterText.text = text
        prompterText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, (fontSeek.progress + 30).toFloat())
        val h = resources.displayMetrics.heightPixels
        val topPad = if (h > resources.displayMetrics.widthPixels) 0.18f else 0.12f
        prompterText.setPadding(prompterText.paddingLeft, (h * topPad).toInt(), prompterText.paddingRight, h)
        (guideLine.layoutParams as FrameLayout.LayoutParams).topMargin = (h * topPad).toInt()
        guideLine.requestLayout()
        prompterScroll.scrollTo(0, 0)
        scrollPos = 0f
        scrolling = false
        timerText.text = "00:00"
        recButton.text = "● GRAVAR"
        editorPanel.visibility = View.GONE
        prompterPanel.visibility = View.VISIBLE
    }

    private fun closePrompter() {
        if (recording != null) stopRecording()
        countdown?.cancel(); countdownText.visibility = View.GONE
        scrolling = false
        prompterPanel.visibility = View.GONE
        editorPanel.visibility = View.VISIBLE
    }

    override fun onBackPressed() {
        if (prompterPanel.visibility == View.VISIBLE) closePrompter() else super.onBackPressed()
    }

    // ---------------------------------------------------------------- rolagem
    private fun pxPerSecond(): Float =
        (speedSeek.progress + 1) * resources.displayMetrics.density * 3f

    private fun changeSpeed(delta: Int) {
        speedSeek.progress = (speedSeek.progress + delta).coerceIn(0, 99)
        Toast.makeText(this, "Velocidade: ${speedSeek.progress + 1}", Toast.LENGTH_SHORT).show()
    }

    private fun toggleScroll() = if (scrolling) stopScroll() else startScroll()

    private fun startScroll() {
        if (scrolling) return
        scrolling = true
        lastFrameNanos = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun stopScroll() { scrolling = false }

    private fun rewind() {
        scrollPos = 0f
        prompterScroll.scrollTo(0, 0)
    }

    // ---------------------------------------------------------------- gravacao
    private fun onRecClicked() {
        if (recording != null) { stopRecording(); return }
        if (countdown != null) { countdown?.cancel(); countdown = null; countdownText.visibility = View.GONE; return }
        if (videoCapture == null) {
            Toast.makeText(this, "Câmera ainda não está pronta.", Toast.LENGTH_SHORT).show(); return
        }
        recButton.text = "✕ CANCELAR"
        countdownText.visibility = View.VISIBLE
        countdown = object : CountDownTimer(3000, 100) {
            override fun onTick(ms: Long) { countdownText.text = ((ms + 999) / 1000).toString() }
            override fun onFinish() {
                countdown = null
                countdownText.visibility = View.GONE
                startRecording()
                startScroll()
            }
        }.start()
    }

    private fun startRecording() {
        val vc = videoCapture ?: return
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "Teleprompter_$stamp")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Teleprompter")
        }
        val options = MediaStoreOutputOptions.Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(values).build()

        val pending = vc.output.prepareRecording(this, options)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
            pending.withAudioEnabled()

        recording = pending.start(ContextCompat.getMainExecutor(this)) { event ->
            if (event is VideoRecordEvent.Finalize) {
                recording = null
                recButton.text = "● GRAVAR"
                if (event.hasError()) {
                    Toast.makeText(this, "Erro na gravação (código ${event.error})", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Vídeo salvo em Filmes/Teleprompter", Toast.LENGTH_LONG).show()
                }
            }
        }
        recordStartMs = SystemClock.elapsedRealtime()
        recButton.text = "■ PARAR"
        timerText.post(timerRunnable)
    }

    private fun stopRecording() {
        recording?.stop()
        recording = null
        stopScroll()
        recButton.text = "● GRAVAR"
    }

    override fun onStop() {
        super.onStop()
        if (recording != null) stopRecording()
    }

    // ---------------------------------------------------------------- utilitarios
    private fun View.hideKeyboard() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
            .hideSoftInputFromWindow(windowToken, 0)
    }

    private inner class SimpleSeek(val update: () -> Unit) : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) = update()
        override fun onStartTrackingTouch(s: SeekBar?) {}
        override fun onStopTrackingTouch(s: SeekBar?) {}
    }
}
