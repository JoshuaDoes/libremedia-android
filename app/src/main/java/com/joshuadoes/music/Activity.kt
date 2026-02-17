package com.joshuadoes.music

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.PersistableBundle
import android.provider.OpenableColumns
import java.io.InputStream
import Libffmpeg.Libffmpeg_
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.audiofx.HapticGenerator
import android.os.Build
import android.os.IBinder
import android.os.PerformanceHintManager
import android.os.Vibrator
import android.util.Log
import android.widget.Button
import kotlin.concurrent.thread
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import com.google.android.material.color.DynamicColors
import java.io.BufferedReader
import java.io.BufferedWriter
import java.net.URL
import java.net.URLEncoder
import java.nio.ByteBuffer

class Libremedia : AppCompatActivity() {
    var appName = "libremedia"
    var appVersion = "v0.1.0.0 preview 9"
    var appBuild = "$appName $appVersion"
    val platform = getSystemProperty("ro.board.platform", "unknown")

    private lateinit var pathLibrary: String
    private lateinit var pathFfmpeg: String
    private lateinit var pathTinymix: String
    private lateinit var pathTinymixGo: String

    private var input: InputStream? = null
    var inputName: String = ""
    private var srcFfmpeg: Libffmpeg_? = null
    private var hapFfmpeg: Libffmpeg_? = null
    private var srcAudio: AudioTrack? = null
    private var hapAudio: AudioTrack? = null
    private var haptics: HapticGenerator? = null

    private var toast: Toast? = null
    private lateinit var vibrator: Vibrator
    private lateinit var perf: PerformanceHintManager
    var hint: PerformanceHintManager.Session? = null
    private var prefs: SharedPreferences? = null

    private lateinit var txtBuild: TextView
    private lateinit var txtPtad: TextView
    private lateinit var txtStats: TextView
    private lateinit var txtError: TextView
    private lateinit var btnSelect: Button
    private lateinit var btnPlayback: Button
    private lateinit var btnStop: Button
    private lateinit var btnHaptics: Button
    private lateinit var btnPtad: Button

    private lateinit var search: SearchView
    var counter = 0
    val counterMax = 7

    var oldOrientation = 0

    var isPlaying = false
    var isPaused = false
    var isHapticify = false

    val threads = Runtime.getRuntime().availableProcessors()
    var targetFpsNanos = 1000000000L / 120

    val ptadNames = arrayOf(
        "ASP: 775/17\nJoshuaDoes",
        "ASP: 817/12\nSoft",
        "ASP: 817/13\nQuiet",
        "ASP: 817/14\nNormal",
        "ASP: 817/15\nLoud",
        "ASP: 817/16\nLouder",
        "ASP: 817/17\nLoudest",
        "DSP: 817/17\nGoogle",
        "DSP: 817/18\nSamsung",
        "DSP: 835/14\nJoshuaDoes"
    )
    val pcm = arrayOf(
        775, 817, 817, 817, 817, 817, 817, 817, 817, 835
    )
    val amp = arrayOf(
        17, 12, 13, 14, 15, 16, 17, 17, 18, 14
    )
    val ramp = arrayOf(
        "Off", "Off", "Off", "Off", "Off", "Off", "Off", "Off", ".5ms", ".5ms"
    )
    val current = arrayOf(
        "3.50A", "3.50A", "3.50A", "3.50A", "3.50A", "3.50A", "3.50A", "3.50A", "3.50A", "3.50A"
    )
    val dsp = arrayOf(
        "ASP", "ASP", "ASP", "ASP", "ASP", "ASP", "ASP", "DSP", "DSP", "DSP"
    )

    var hasHaptics = HapticGenerator.isAvailable()
    val crossover: Long = 65 //Crossover frequency when hasHaptics is true
    var ptadAvailable = false
    var ptad = 0
    val ptadMax = ptadNames.size - 1

    val bufSizeIn = 65536000
    val volSleep: Long = 4 //milliseconds

    var srcSampleRate: Long = 192000
    var srcChannels: Long = 2
    var srcHighpass: Long = 0
    var srcGain = 0.0
    var srcCodec = "pcm_f32le"
    var srcFormat = "f32le"
    var srcPrecision = "f64"
    var srcFmtChannels = AudioFormat.CHANNEL_OUT_STEREO
    var srcFmtEncoding = AudioFormat.ENCODING_PCM_FLOAT
    var srcBufSize = AudioTrack.getMinBufferSize(srcSampleRate.toInt(), srcFmtChannels, srcFmtEncoding) * 4
    var srcSampleSize: Long = 4
    var srcBufSamples = srcBufSize / srcChannels / srcSampleSize
    var srcBufSamplesStreaming = srcSampleSize * 4

    var hapSampleRate: Long = srcSampleRate
    var hapChannels: Long = srcChannels
    var hapLowpass: Long = crossover
    var hapGain = 0.0
    var hapGenDistortionGain = 1.0 //default on Pixel is 0.32, AKA 32% volume, safe up to 2.0 (200% volume) in testing but can be very fun higher if your songs don't max out their samples; consider it a hardware volume knob
    var hapCodec = srcCodec
    var hapFormat = srcFormat
    var hapPrecision = srcPrecision
    var hapFmtChannels = srcFmtChannels
    var hapFmtEncoding = srcFmtEncoding
    var hapBufSize = AudioTrack.getMinBufferSize(hapSampleRate.toInt(), hapFmtChannels, hapFmtEncoding) * 4
    var hapSampleSize: Long = srcSampleSize
    var hapBufSamples = hapBufSize / hapChannels / hapSampleSize
    var hapBufSamplesStreaming = srcBufSamplesStreaming

    @SuppressLint("WrongConstant")
    override fun onCreate(savedInstanceState: Bundle?) {
        logV("Creating activity")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.libremedia)
        DynamicColors.applyToActivitiesIfAvailable(application)
        initPrefs(applicationContext)
        initPaths(applicationContext)

        txtBuild = findViewById<TextView>(R.id.build)
        txtPtad = findViewById<TextView>(R.id.ptadTxt)
        txtStats = findViewById<TextView>(R.id.stats)
        txtError = findViewById<TextView>(R.id.error)
        search = findViewById<SearchView>(R.id.search)
        btnSelect = findViewById<Button>(R.id.select)
        btnPlayback = findViewById<Button>(R.id.playback)
        btnStop = findViewById<Button>(R.id.stop)
        btnHaptics = findViewById<Button>(R.id.haptics)
        btnPtad = findViewById<Button>(R.id.ptad)
        /*txtBuild.setOnClickListener {
            cancelToast()

            if (counter < 0) {
                setToast(applicationContext, "You already enabled the search bar!", Toast.LENGTH_SHORT)
                return@setOnClickListener
            }

            counter++
            if (counter >= counterMax) {
                counter = -1

                search.visibility = SearchView.VISIBLE
                search.queryHint = "creator, stream, album ..."
                search.setIconifiedByDefault(false)
                search.isSubmitButtonEnabled = true
                search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String?): Boolean {
                        if (query == null || query == "")
                            return false

                        val url =
                            "https://libremedia.joshuado.es/v1/stream/bestmatch:" + URLEncoder.encode(
                                query,
                                "UTF-8"
                            )
                        logV("URL: $url")

                        thread(start = true) {
                            val inputStream: InputStream

                            try {
                                inputStream = URL(url).openStream()
                                stopAudio()
                                processWithFFmpeg(inputStream, query)
                            } catch (e: Exception) {
                                e.printStackTrace()
                                logE("Error reading URL: ${e.message}")
                            }
                        }
                        return false
                    }

                    override fun onQueryTextChange(newText: String?): Boolean {
                        if (counter > -1)
                            counter = 0
                        return false
                    }
                })

                setToast(applicationContext, "You can now search for audio using libremedia!", Toast.LENGTH_SHORT)
                return@setOnClickListener
            }

            setToast(applicationContext,
                "You are ${counterMax - counter} steps away from enabling the search bar!",
                Toast.LENGTH_SHORT
            )
        }*/
        btnSelect.setOnClickListener {
            if (counter > -1)
                counter = 0
            hapticToggleOn(btnSelect)
            logV("Opening file picker for input audio")
            openAudioFilePicker()
        }
        btnPlayback.setOnClickListener {
            if (counter > -1)
                counter = 0
            if (isPlaying) {
                if (isPaused) {
                    hapticToggleOn(btnPlayback)
                    logHello()
                    thread(start = true) {
                        resumeAudio(true, true)
                    }
                } else {
                    hapticToggleOff(btnPlayback)
                    thread(start = true) {
                        pauseAudio(true)
                    }
                }
            } else {
                hapticToggleOn(btnPlayback)
                thread(start = true) {
                    //playAudio()
                    startPlayer()
                }
            }
        }
        btnStop.setOnClickListener {
            if (counter > -1)
                counter = 0
            hapticToggleOff(btnStop)
            thread(start = true) {
                stopAudio()
            }
        }
        if (hasHaptics) {
            btnHaptics.visibility = Button.VISIBLE
            btnHaptics.setOnClickListener {
                if (counter > -1)
                    counter = 0
                if (isHapticify) {
                    hapticToggleOff(btnHaptics)
                    thread(start = true) {
                        setHaptics(false)
                    }
                } else {
                    hapticToggleOn(btnHaptics)
                    thread(start = true) {
                        setHaptics(true)
                    }
                }
            }
        }
        btnPtad.setOnClickListener {
            if (counter > -1)
                counter = 0
            btnPtad.isEnabled = false
            ptadToggle()
            if (ptad > 0) {
                hapticToggleOn(btnPtad)
            } else {
                hapticToggleOff(btnPtad)
            }
            thread(start = true) {
                ptadSetAllowed(ptad)
            }
            btnPtad.isEnabled = true
        }
        txtBuild.text = appBuild

        vibrator = (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        perf = getSystemService(PERFORMANCE_HINT_SERVICE) as PerformanceHintManager

        //INIT
        if (savedInstanceState == null) {
            oldOrientation = requestedOrientation

            logV(appBuild)
            logV("Platform: $platform")

            //Detect board and raise the HapticGenerator distortion gain when on a known safe platform
            if (platform == "gs101" || platform == "gs201" || platform == "zuma" || platform == "zumapro" || platform == "laguna") hapGenDistortionGain = 2.0

            ptadSetAllowed(-1)
            if (hasHaptics) {
                //Set crossover
                srcHighpass = crossover
                hapLowpass = crossover
                setHaptics(true)
            }

            logV("Ensuring stability of player")
            createAudio()
            if (hasHaptics) {
                createHaptics(false, false)
                releaseHaptics()
            }
            releaseAudio()

            logHello()
        }
    }

    override fun onStart() {
        super.onStart()
    }

    override fun onSaveInstanceState(outState: Bundle, outPersistentState: PersistableBundle) {
        logV("Saving state")
        super.onSaveInstanceState(outState, outPersistentState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
        logV("Restoring state")
        super.onRestoreInstanceState(savedInstanceState, persistentState)
    }

    override fun onPause() {
        logV("Pausing activity")
        super.onPause()
    }

    override fun onResume() {
        logV("Resuming activity")
        super.onResume()

        setHapticsUI()
        ptadSetUI()
    }

    override fun onDestroy() {
        logV("Destroying activity")
        super.onDestroy()
    }

    fun initPrefs(context: Context) {
        if (prefs != null)
            return
        prefs = context.getSharedPreferences("libremedia", Context.MODE_PRIVATE)
    }

    fun initPaths(context: Context) {
        pathLibrary = context.applicationInfo.nativeLibraryDir
        pathFfmpeg = "$pathLibrary/ffmpeg.so"
        pathTinymix = "$pathLibrary/tinymix.so"
        pathTinymixGo = "$pathLibrary/gotinymix.so"
    }

    fun getSystemProperty(key: String, defaultValue: String? = null): String? {
        try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getDeclaredMethod("get", String::class.java, String::class.java)
            return method.invoke(null, key, defaultValue) as String?
        } catch (e: Exception) {
            Log.e("SystemProperties", "Error getting system property: $key", e)
        }
        return defaultValue
    }

    private var suProcess: Process? = null
    private var suStdin: BufferedWriter? = null
    private var suStdout: BufferedReader? = null
    private var suStderr: BufferedReader? = null
    private fun runRoot(input: String): Triple<Boolean, String, String> {
        // Lazily start root shell once
        if (suProcess == null) {
            try {
                suProcess = Runtime.getRuntime().exec("su")
                suStdin = suProcess!!.outputStream.bufferedWriter()
                suStdout = suProcess!!.inputStream.bufferedReader()
                suStderr = suProcess!!.errorStream.bufferedReader()
            } catch (e: Exception) {
                e.printStackTrace()
                return Triple(false, "", e.toString())
            }
        }

        if (input.isBlank()) {
            return Triple(false, "", "")
        }

        // Marker to detect command completion
        val marker = "__CMD_DONE_${System.nanoTime()}__"

        // Execute command
        suStdin!!.write(input)
        suStdin!!.newLine()
        suStdin!!.write("echo $marker")
        suStdin!!.newLine()
        suStdin!!.flush()

        val outBuf = StringBuilder()
        val errBuf = StringBuilder()

        // Read stdout until marker
        while (true) {
            val line = suStdout!!.readLine() ?: break
            if (line == marker) break
            outBuf.appendLine(line)
        }

        // Drain stderr without blocking
        while (suStderr!!.ready()) {
            errBuf.appendLine(suStderr!!.readLine())
        }

        return Triple(true, outBuf.toString(), errBuf.toString())
    }

    fun ptadSetAllowed(to: Int, internal: Boolean = false) {
        when (Build.DEVICE) {
            "oriole", "raven", "bluejay",               //Google Pixel 6
            "panther", "cheetah", "lynx",               //Google Pixel 7
            "felix",                                    //Google Pixel Fold
            "tangorpro",                                //Google Pixel Tablet
            "husky", "shiba", "akita",                  //Google Pixel 8
            "caiman", "komodo", "comet", "tokay"        //Google Pixel 9
                -> ptadSet(to, "google,aoc-snd-card", internal)
            "r0q", "r0s", "g0q", "g0s", "b0q", "b0s",   //Samsung Galaxy S22
            "dm1q", "dm2q", "dm3q",                     //Samsung Galaxy S23
            "r11q", "r11s",                             //Samsung Galaxy S23 FE
            "e1q", "e1s", "e2q", "e2s", "e3q",          //Samsung Galaxy S24
            "r12s"                                      //Samsung Galaxy S24 FE
                -> ptadSet(to, "", internal) //TODO: get mixer names for Samsung devices
            else -> ptadSet(to, "", internal)
        }
    }
    private fun ptadSetUI() {
        if (ptadAvailable) {
            runOnUiThread(Runnable {
                val name = ptadNames[ptad]
                btnPtad.text = name
                txtPtad.text = ptadString(ptad)
                btnPtad.visibility = Button.VISIBLE
                txtPtad.visibility = TextView.VISIBLE
            })
        }
    }
    private fun ptadSet(to: Int, mixers: String = "", internal: Boolean = false): Int {
        var set = to
        if (set == -1) set = prefs!!.getInt("PTAD", 0)
        with(prefs!!.edit()) {
            putInt("PTAD", set)
            apply()
        }
        ptad = set

        if (!internal) ptadSetUI()

        val playing = (isPlaying && !isPaused)
        if (playing) pauseAudio(volFade = true, internal = true)

        val pcm = pcm[set]
        val amp = amp[set]
        val ramp = ramp[set]
        val current = current[set]
        var dsp = dsp[set]

        //Provide extra controls under certain conditions
        var extraCtls = ""
        var extraVals = ""

        if (platform == "laguna") {
            //Support laguna having two ASP modes; we want the first
            extraCtls += ",DSP Bypass"
            if (dsp == "ASP") {
                dsp = "ASPRX1"
                extraVals += ",1"
            } else {
                extraVals += ",0"
            }

            extraCtls += ",Noise Gate Delay"
            extraVals += ",5ms"
        }

        var cmd = ""
        cmd += "$pathTinymixGo -t \"$pathTinymix\" -D 0 -m \"$mixers\" "
        cmd += "-l -r -c \""
        cmd += "Digital PCM Volume,Amp Gain,AMP PCM Gain,PCM Soft Ramp,Boost Peak Current Limit,PCM Source,PCM Stream Wait Time in MSec"
        if (extraCtls != "") cmd += extraCtls
        cmd += "\" -v \""
        cmd += "$pcm,$amp,$amp,$ramp,$current,$dsp,0"
        if (extraVals != "") cmd += extraVals
        cmd += "\""

        logV("Root: $cmd")
        val (ran1, stdout1, stderr1) = runRoot(cmd)
        if (stdout1 != "") logV("Stdout:\n$stdout1")
        if (stderr1 != "") logV("Stderr:\n$stderr1")

        ptadAvailable = ran1
        if (ran1) {
            logV("Set PTAD mode to: " + ptadNames[set])
        } else {
            logE("Disabled PTAD at runtime!")
        }

        if (!internal) ptadSetUI()

        cmd = "getprop vendor.audio.hapticgenerator.distortion.output.gain && setprop vendor.audio.hapticgenerator.distortion.output.gain $hapGenDistortionGain && getprop vendor.audio.hapticgenerator.distortion.output.gain"

        logV("Root: $cmd")
        val (ran2, stdout2, stderr2) = runRoot(cmd)
        if (stdout2 != "") logV("Stdout:\n$stdout2")
        if (stderr2 != "") logV("Stderr:\n$stderr2")
        if (ran2) {
            logV("Set HapticGenerator distortion gain to: $hapGenDistortionGain")
        } else {
            logE("Failed to set HapticGenerator distortion gain!")
        }

        if (playing) resumeAudio(volFade = true, createHaptics = true, internal = true)

        return set
    }
    private fun ptadToggle() {
        ptad++
        if (ptad > ptadMax)
            ptad = 0
    }
    fun ptadString(set: Int): String {
        val pcm = pcm[set].toString()
        val amp = amp[set].toString()
        val ramp = ramp[set]
        val current = current[set]
        val dsp = dsp[set]
        return "Pixel Tensor Audio Decompressor v3.0.1 (in-app alpha)\nPCM: $pcm/913 | AMP: $amp/20 | RAMP: $ramp/30ms\nPOWER: $current/4.50A | SOURCE: $dsp"
    }

    private fun perfOn() {
        if (perfActive())
            return
        logV("Targeting a higher performance")
        hint = perf.createHintSession(intArrayOf(android.os.Process.myTid()), targetFpsNanos)
    }
    private fun perfOff() {
        if (!perfActive())
            return
        logV("Closing the performance target")
        hint!!.close()
        hint = null
    }
    private fun perfActive(): Boolean {
        return hint != null
    }

    private fun setToast(context: Context, msg: String, duration: Int) {
        logV("Toast ($duration): $msg")
        cancelToast()
        toast = Toast.makeText(context, msg, duration)
        toast!!.show()
    }
    private fun cancelToast() {
        if (toast != null) {
            toast!!.cancel()
            toast = null
        }
    }

    private fun createAudio() {
        srcAudio = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setHapticChannelsMuted(true)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(srcSampleRate.toInt())
                .setChannelMask(srcFmtChannels)
                .setEncoding(srcFmtEncoding)
                .build(),
            srcBufSize,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        srcAudio!!.setVolume(1f)
        srcAudio!!.play()
        logV("Created source AudioTrack")

        if (hasHaptics) {
            hapAudio = AudioTrack(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setHapticChannelsMuted(false)
                    .build(),
                AudioFormat.Builder()
                    .setSampleRate(hapSampleRate.toInt())
                    .setChannelMask(hapFmtChannels)
                    .setEncoding(hapFmtEncoding)
                    .build(),
                hapBufSize,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )
            hapAudio!!.setVolume(0f)
            hapAudio!!.play()
            logV("Created haptics AudioTrack")
        }

        isPlaying = true
        isPaused = false
    }

    private fun releaseAudio() {
        if (srcAudio != null) {
            srcAudio!!.stop()
            srcAudio!!.release()
            srcAudio = null
        }
        if (hasHaptics && hapAudio != null) {
            hapAudio!!.stop()
            hapAudio!!.release()
            hapAudio = null
        }

        isPlaying = false
        isPaused = false
    }

    private fun getHaptics(): Boolean {
        val hap = prefs!!.getBoolean("Hapticify", false)
        logV("Got haptics: $hap")
        return hap
    }
    private fun setHapticsUI() {
        if (hasHaptics) {
            runOnUiThread(Runnable {
                if (getHaptics()) {
                    btnHaptics.text = "Hapticify: On"
                } else {
                    btnHaptics.text = "Hapticify: Off"
                }
                btnHaptics.visibility = Button.VISIBLE
            })
        } else {
            runOnUiThread(Runnable {
                btnHaptics.visibility = Button.GONE
            })
        }
    }
    private fun setHaptics(hap: Boolean) {
        with(prefs!!.edit()) {
            putBoolean("Hapticify", hap)
            apply()
        }
        isHapticify = hap
        logV("Set haptics: $hap")

        runOnUiThread(Runnable {
            setHapticsUI()
            btnHaptics.isEnabled = false
            if (isPlaying) {
                if (!isPaused) {
                    btnPlayback.isEnabled = false
                    btnStop.isEnabled = false
                }
            }
        })

        pauseAudio(true, true)
        resumeAudio(true, true, true)

        runOnUiThread(Runnable {
            btnHaptics.isEnabled = true
            if (isPlaying && !isPaused) {
                btnPlayback.isEnabled = true
                btnStop.isEnabled = true
            }
        })
    }

    private fun createHaptics(pause: Boolean = true, resume: Boolean = true) {
        logV(">> Request to create haptics")
        if (!hasHaptics)
            return
        logD(">> Has haptics")

        val hasAudio = (hapAudio != null)
        if (hasAudio) {
            val playing = (isPlaying && !isPaused)
            if (pause && playing)
                pauseAudio(false, true)

            releaseHaptics()

            if (isHapticify) {
                try {
                    haptics = HapticGenerator.create(hapAudio!!.audioSessionId)
                    logV(">> Created HapticGenerator")

                    if (isHapticify) {
                        haptics!!.enabled = isHapticify
                        logV(">> Enabled Hapticify")

                        Thread.sleep(500)
                    }
                } catch (e: Exception) {
                    logE(">> Failed to create HapticGenerator")
                    e.printStackTrace()
                    isHapticify = false
                    hasHaptics = false
                    haptics = null
                }
            }

            if (resume && playing)
                resumeAudio(false, false, true)
        }
    }

    private fun releaseHaptics() {
        logD("<< Request to release haptics")
        if (!hasHaptics)
            return
        logD("<< Has haptics")
        if (haptics != null) {
            logD("<< Closing haptics")
            haptics!!.close()
            haptics!!.release()
            haptics = null
        }
    }

    private fun hapticToggleOn(btn: Button) {
        if (!isPlaying && btn.isHapticFeedbackEnabled)
            btn.performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
    }

    private fun hapticToggleOff(btn: Button) {
        if (!isPlaying && btn.isHapticFeedbackEnabled)
            btn.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
    }

    private fun processWithFFmpeg(inputStream: InputStream, name: String) {
        logD("Stopping audio to change inputs")
        stopAudio()

        runOnUiThread( Runnable {
            search.visibility = SearchView.GONE
            search.setQuery("", false)
            btnSelect.visibility = Button.GONE
            btnPlayback.visibility = Button.GONE
            btnStop.visibility = Button.GONE
        })

        logD("Setting input: " + name)
        if (input != null) {
            input!!.close()
        }
        input = inputStream
        inputName = name
        logHello()

        runOnUiThread( Runnable {
            if (counter < 0)
                search.visibility = SearchView.VISIBLE
            btnSelect.text = name
            btnSelect.visibility = Button.VISIBLE
            btnPlayback.visibility = Button.VISIBLE
            btnStop.text = "Cancel"
            btnStop.visibility = Button.VISIBLE
        })
    }

    fun playAudio() {
        runOnUiThread(Runnable {
            oldOrientation = requestedOrientation
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            search.visibility = SearchView.GONE
            btnSelect.visibility = Button.GONE
            btnPlayback.visibility = Button.GONE
            btnStop.text = "Stop"
            btnStop.visibility = Button.GONE
        })

        logV("Creating source ffmpeg")
        srcFfmpeg = Libffmpeg_(srcCodec, srcFormat)
        srcFfmpeg?.setOutputChannels(srcChannels)
        srcFfmpeg?.setOutputRate(srcSampleRate)
        srcFfmpeg?.setPrecision(srcPrecision)
        srcFfmpeg?.setThreads(threads.toLong())
        srcFfmpeg?.addArgIn("-vn")
        srcFfmpeg?.addArgOut("-movflags")
        srcFfmpeg?.addArgOut("faststart")
        srcFfmpeg?.addArgOut("-vn")
        srcFfmpeg?.fFmpeg = pathFfmpeg
        srcFfmpeg?.libraryPath = pathLibrary

        if (srcHighpass > 0 || srcGain > 0) {
            logV("Adding filters to source ffmpeg")
            val srcHp: Long = srcFfmpeg?.newFilter() ?: 0
            if (srcHighpass > 0)
                srcFfmpeg?.filterAddHighpass(srcHp, srcHighpass)
            if (srcGain > 0)
                srcFfmpeg?.filterAddVolumeGain(srcHp, srcGain)
            srcFfmpeg?.filterSetOutputChannelsStereo(srcHp, 0, 1)
        }

        logV("Generated command for source ffmpeg: ${srcFfmpeg?.commandRaw()}")

        if (hasHaptics) {
            logV("Creating haptics ffmpeg")
            hapFfmpeg = Libffmpeg_(hapCodec, hapFormat)
            hapFfmpeg?.setOutputChannels(hapChannels)
            hapFfmpeg?.setOutputRate(hapSampleRate)
            hapFfmpeg?.setPrecision(hapPrecision)
            hapFfmpeg?.setThreads(threads.toLong())
            hapFfmpeg?.addArgIn("-vn")
            hapFfmpeg?.addArgOut("-movflags")
            hapFfmpeg?.addArgOut("faststart")
            hapFfmpeg?.addArgOut("-vn")
            hapFfmpeg?.fFmpeg = pathFfmpeg
            hapFfmpeg?.libraryPath = pathLibrary

            if (hapLowpass > 0 || hapGain > 0) {
                logV("Adding filters to haptics ffmpeg")
                val hapLp = hapFfmpeg?.newFilter() ?: 0
                if (hapLowpass > 0)
                    hapFfmpeg?.filterAddLowpass(hapLp, hapLowpass)
                if (hapGain > 0)
                    hapFfmpeg?.filterAddVolumeGain(hapLp, hapGain)
                hapFfmpeg?.filterSetOutputChannelsStereo(hapLp, 0, 1)
            }

            logV("Generated command for haptics ffmpeg: ${hapFfmpeg?.commandRaw()}")
        }

        thread(start = true) {
            if (isPlaying) {
                logD("Stopping previous audio")
                srcAudio!!.stop()
                if (hasHaptics)
                    hapAudio!!.stop()
            }

            //thread(start = true) {
            logI("Streaming input audio to ffmpeg")
            while (true) {
                val inputBuf = ByteArray(bufSizeIn)
                val read = input!!.read(inputBuf)
                if (read > 0) {
                    val wrote = srcFfmpeg?.write(inputBuf.copyOfRange(0, read))
                    if (wrote == null || wrote <= 0) {
                        break
                    }
                    if (hasHaptics) {
                        val wrote2 = hapFfmpeg?.write(inputBuf.copyOfRange(0, read))
                        if (wrote2 == null || wrote2 <= 0) {
                            break
                        }
                    }
                } else {
                    break
                }
            }
            srcFfmpeg?.write(null)
            if (hasHaptics)
                hapFfmpeg?.write(null)
            input!!.close()
            input = null
            //}

            logV("Starting ffmpeg")
            srcFfmpeg?.start()
            if (hasHaptics)
                hapFfmpeg?.start()

            logV("Creating new audio")
            createAudio()

            if (hasHaptics) {
                logV("Creating new haptics")
                createHaptics(false, false)
            }

            runOnUiThread(Runnable {
                btnPlayback.text = "Pause"
                if (counter < 0)
                    search.visibility = SearchView.VISIBLE
                btnSelect.visibility = Button.VISIBLE
                btnPlayback.visibility = Button.VISIBLE
                btnStop.visibility = Button.VISIBLE
            })

            logV(":: Playing audio")
            logHello()
            var warmingAudio = false
            var warmedAudio = false
            var srcBackBuf = ByteArray(0)
            var hapBackBuf = ByteArray(0)
            var loggedPause = false
            while (true) {
                if (!isPlaying) {
                    logD(":: No longer playing (1)")
                    break
                }
                if (isPaused || srcAudio == null || (hasHaptics && hapAudio == null)) {
                    if (!loggedPause) {
                        logD(":: Paused (1)")
                        loggedPause = true
                    }
                    continue
                }

                var srcBuffer: ByteArray?
                var hapBuffer: ByteArray? = ByteArray(0)

                srcBuffer = srcFfmpeg?.read(((srcSampleSize*srcChannels*srcBufSamplesStreaming) - srcBackBuf.size).toLong()) ?: null
                if (srcBuffer == null) {
                    srcBuffer = ByteArray(0)
                }
                if (srcBackBuf.isNotEmpty()) {
                    srcBuffer = srcBackBuf.plus(srcBuffer)
                    srcBackBuf = ByteArray(0)
                }

                if (hasHaptics) {
                    hapBuffer = hapFfmpeg?.read(((hapSampleSize*hapChannels*hapBufSamplesStreaming) - hapBackBuf.size))
                    if (hapBuffer == null) {
                        hapBuffer = ByteArray(0)
                    }
                    if (hapBackBuf.isNotEmpty()) {
                        hapBuffer = hapBackBuf.plus(hapBuffer)
                        hapBackBuf = ByteArray(0)
                    }
                }

                if (srcBuffer.isEmpty() || (hasHaptics && hapBuffer!!.isEmpty())) {
                    logD(":: No more audio!")
                    break
                }

                if (!warmedAudio) {
                    logV("Warming up with silence")
                    val srcSilence = ByteArray(srcBufSize / 4)
                    var hapSilence = ByteArray(0)
                    if (hasHaptics) {
                        hapSilence = ByteArray(hapBufSize / 4)
                    }
                    srcAudio!!.pause()
                    srcAudio!!.write(srcSilence, 0, srcSilence.size)
                    if (hasHaptics) {
                        hapAudio!!.pause()
                        hapAudio!!.write(hapSilence, 0, hapSilence.size)
                        logD("Warmed audio with ${srcSilence.size} bytes of silence and haptics with ${hapSilence.size} bytes of silence")
                    } else {
                        logD("Warmed audio with ${srcSilence.size} bytes of silence")
                    }
                    warmingAudio = true
                }

                if (!isPlaying) {
                    logD(":: No longer playing (2)")
                    break
                }
                if (isPaused || srcAudio == null || (hasHaptics && hapAudio == null)) {
                    if (!loggedPause) {
                        logD(":: Paused (2)")
                        loggedPause = true
                    }
                    continue
                }

                val wrote = srcAudio!!.write(ByteBuffer.wrap(srcBuffer), srcBuffer.size, AudioTrack.WRITE_BLOCKING)
                if (wrote < 0) { //Less than 0 is an error
                    logD(":: srcAudio write failed: $wrote")
                    break
                }
                if (wrote < srcBuffer.size) {
                    srcBackBuf = srcBuffer.copyOfRange(wrote, srcBuffer.size)
                }
                if (hasHaptics) {
                    //If there's a back buffer, force haptics to backbuffer too by subtracting the size of srcBackBuf
                    val wrote2 = hapAudio!!.write(ByteBuffer.wrap(hapBuffer!!), hapBuffer.size-srcBackBuf.size, AudioTrack.WRITE_BLOCKING)
                    if (wrote2 < 0) { //Less than 0 is an error
                        logD(":: hapAudio write failed: $wrote2")
                        break
                    }
                    if (wrote2 < hapBuffer.size) { //Confirms a forced backbuffer as well
                        hapBackBuf = hapBuffer.copyOfRange(wrote2, hapBuffer.size)
                    }
                }

                if (warmingAudio) {
                    logV("Warming complete, starting playback")
                    srcAudio!!.play()
                    if (hasHaptics)
                        hapAudio!!.play()
                    warmingAudio = false
                    warmedAudio = true
                }
            }

            logV(":: Stopping audio")
            stopAudio()

            logD("Releasing previous audio")
            releaseAudio()

            val err = srcFfmpeg?.error()
            val stats = srcFfmpeg?.stats
            if (err != null && err != "") {
                logE("Error: $err\nStats: $stats")
            }
            if (hasHaptics) {
                val err2 = hapFfmpeg?.error()
                val stats2 = hapFfmpeg?.stats
                if (err2 != null && err2 != "") {
                    logE("Error: $err2\nStats: $stats2")
                }
                logV("Main audio:\nError: $err\nStats: $stats\n\nHaptics audio:\nError: $err2\nStats: $stats2")
            } else {
                logV("Main audio:\nError: $err\nStats: $stats")
            }

            srcFfmpeg?.close()
            srcFfmpeg = null
            if (hasHaptics) {
                hapFfmpeg?.close()
                hapFfmpeg = null
            }
        }
    }

    fun stopAudio() {
        if (isPlaying) {
            pauseAudio(true, false)
            runOnUiThread(Runnable {
                requestedOrientation = oldOrientation
                btnSelect.text = "Select Audio"
                btnPlayback.text = "Play"
                btnPlayback.visibility = Button.GONE
                btnStop.visibility = Button.GONE
            })
            srcFfmpeg?.close()
            srcFfmpeg = null
            if (hasHaptics) {
                hapFfmpeg?.close()
                hapFfmpeg = null
            }
            isPlaying = false
            stopPlayer()
            Runtime.getRuntime().gc()
        }
    }

    private fun pauseAudio(volFade: Boolean = true, internal: Boolean = false) {
        if (isPaused || !isPlaying) {
            return
        }
        if (!internal) {
            runOnUiThread(Runnable {
                requestedOrientation = oldOrientation
                btnPlayback.text = "Resume"
            })
        }
        runOnUiThread(Runnable {
            btnSelect.isEnabled = false
            btnPlayback.isEnabled = false
            btnStop.isEnabled = false
            btnHaptics.isEnabled = false
            btnPtad.isEnabled = false
            search.isEnabled = false
        })
        if (volFade) {
            for (i in 100 downTo 0) {
                Thread.sleep(volSleep)
                srcAudio!!.setVolume(i.toFloat() / 100)
            }
        }
        if (!internal) {
            runOnUiThread(Runnable {
                btnSelect.isEnabled = true
                btnPlayback.isEnabled = true
                btnStop.isEnabled = true
                btnHaptics.isEnabled = true
                btnPtad.isEnabled = true
                search.isEnabled = true
            })
        }
        isPaused = true
        Thread.sleep(100)
    }

    private fun resumeAudio(volFade: Boolean = true, createHaptics: Boolean = true, internal: Boolean = false) {
        if (!isPaused || !isPlaying) {
            return
        }
        runOnUiThread(Runnable {
            btnSelect.isEnabled = false
            btnPlayback.isEnabled = false
            btnStop.isEnabled = false
            btnHaptics.isEnabled = false
            btnPtad.isEnabled = false
            search.isEnabled = false
        })
        if (hasHaptics && createHaptics) {
            createHaptics(false, false)
        }
        srcAudio!!.play()
        if (hasHaptics)
            hapAudio!!.play()
        isPaused = false
        Thread.sleep(100)
        if (volFade) {
            for (i in 0..100) {
                Thread.sleep(volSleep)
                srcAudio!!.setVolume(i.toFloat() / 100)
            }
        }
        if (!internal) {
            runOnUiThread(Runnable {
                oldOrientation = requestedOrientation
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                btnPlayback.text = "Pause"
            })
        }
        runOnUiThread(Runnable {
            btnSelect.isEnabled = true
            btnPlayback.isEnabled = true
            btnStop.isEnabled = true
            btnHaptics.isEnabled = true
            btnPtad.isEnabled = true
            search.isEnabled = true
        })
    }

    private fun handleAudioFile(uri: Uri) {
        try {
            val fileName = getFileName(uri)
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            if (inputStream != null) {
                logV("File: $fileName")
                processWithFFmpeg(inputStream, fileName)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            logE("Error reading file: ${e.message}")
        }
    }

    companion object {
        private const val PICK_FILE_REQUEST_CODE = 1001
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_FILE_REQUEST_CODE) {
            var uri: Uri? = null
            if (resultCode == Activity.RESULT_OK) {
                uri = data?.data
            }
            if (uri != null) {
                handleAudioFile(uri)
            } else {
                logHello()
            }
        }
    }

    private fun openAudioFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/*", "video/*"))
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(intent, PICK_FILE_REQUEST_CODE)
    }

    private fun getFileName(uri: Uri): String {
        var fileName = ""
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                fileName = it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            }
        }
        return fileName
    }

    private var player: LibremediaPlayer? = null
    private var playerBound = false
    private val playerConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName?, service: IBinder?) {
            logD("Service connected")
            val binder = service as LibremediaPlayer.LocalBinder
            player = binder.getService()
            playerBound = true
            this@Libremedia.playAudio()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            logD("Service disconnected")
            this@Libremedia.stopAudio()
            player = null
            playerBound = false
        }
    }
    fun startPlayer() {
        try {
            val intent = Intent(applicationContext, LibremediaPlayer::class.java)
            intent.putExtra("appBuild", appBuild)
            intent.putExtra("inputName", inputName)
            applicationContext.startForegroundService(intent)
            applicationContext.bindService(intent, playerConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            e.printStackTrace()
            logE("Error starting player: ${e.message}")
        }
    }
    fun stopPlayer() {
        try {
            if (playerBound) {
                applicationContext.unbindService(playerConnection)
                playerBound = false
            }
            val intent = Intent(applicationContext, LibremediaPlayer::class.java)
            applicationContext.stopService(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            logE("Error stopping player: ${e.message}")
        }
    }

    fun logI(msg: String) {
        runOnUiThread(Runnable {
            Log.i("libremedia", msg)
            txtStats.text = msg
        })
    }
    fun logW(msg: String) {
        runOnUiThread(Runnable {
            Log.w("libremedia", msg)
        })
    }
    fun logE(msg: String) {
        runOnUiThread(Runnable {
            Log.e("libremedia", msg)
            txtError.text = msg
        })
    }
    fun logD(msg: String) {
        runOnUiThread(Runnable {
            Log.d("libremedia", msg)
        })
    }
    fun logV(msg: String) {
        runOnUiThread(Runnable {
            Log.v("libremedia", msg)
        })
    }
    private fun logHello() {
        var logStr = "Audio: ${srcHighpass}Hz highpass: ${srcGain}dB ${srcPrecision}" +
                " | ${srcCodec}:${srcFormat} | ${srcChannels}ch ${srcSampleRate / 1000}KHz | ${srcBufSamples}:${srcBufSamplesStreaming} samples"
        if (hasHaptics)
            logStr += "\nHaptics: ${hapLowpass}Hz lowpass: ${hapGain}dB ${hapPrecision}" +
                    " | ${hapCodec}:${hapFormat} | ${hapChannels}ch ${hapSampleRate / 1000}KHz | ${hapBufSamples}:${hapBufSamplesStreaming} samples"
        if (ptadAvailable)
            logStr += "\nHapticGenerator: gain=${hapGenDistortionGain}"
        logI(logStr)
    }
}