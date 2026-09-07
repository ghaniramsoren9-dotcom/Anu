package com.ghaniram.zoya

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.StatFs
import java.io.File
import java.util.Locale

/** Fresh, local device telemetry. Returns only the information actually requested. */
object DeviceInfoProvider {
    fun snapshot(context: Context): String {
        val battery = battery(context)
        val memory = memory(context)
        val storage = storage()
        val cpu = cpu()
        val gpu = gpu(context)
        val deviceContext = context.applicationContext as android.app.Application
        val locationTime = DeviceContactLocationManager(deviceContext)
        val indiaTime = locationTime.indiaTime()
        val location = locationTime.currentLocation()

        val full = buildString {
            append("Manufacturer: ${Build.MANUFACTURER}\n")
            append("Model: ${Build.MODEL}\n")
            append("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
            append("Battery: ${battery.percent}%${if (battery.charging) " (charging)" else " (not charging)"}")
            battery.temperatureC?.let { append(", temperature ${format(it)}°C") }
            append("\n")
            append("RAM: ${memory.availableMb} MB available / ${memory.totalMb} MB total\n")
            append("App memory: ${memory.appPssMb} MB PSS\n")
            append("Storage: ${storage.freeGb} GB free / ${storage.totalGb} GB total\n")
            append("CPU: $cpu\n")
            append("GPU: ${gpu.renderer}")
            gpu.utilization?.let { append(", current utilization ${it}%") }
            gpu.headroom?.let { append(", Android 16 GPU headroom ${format(it)}% available") }
            append("\n")
            append("Display: ${context.resources.displayMetrics.widthPixels}x${context.resources.displayMetrics.heightPixels}, density ${context.resources.displayMetrics.density}\n")
            append("India time: $indiaTime\n")
            append("Location: $location")
        }

        // Text input sets this bridge. Voice input may not, so also infer the latest
        // user utterance directly from the session state before a getDeviceInfo call.
        val query = DeviceQueryContext.consume().ifBlank {
            runCatching {
                ZoyaSessionManager.state.value.chatMessages.lastOrNull { it.role == ChatRole.USER }?.text.orEmpty()
            }.getOrDefault("")
        }.lowercase(Locale.getDefault())

        if (query.isBlank()) return "Device: ${Build.MANUFACTURER} ${Build.MODEL}. Android ${Build.VERSION.RELEASE}."
        val lines = full.lines()
        fun pick(vararg prefixes: String): String = lines.filter { line -> prefixes.any { p -> line.lowercase(Locale.getDefault()).startsWith(p) } }.joinToString("\n")
        return when {
            query.contains("battery") || query.contains("ବ୍ୟାଟେରୀ") || query.contains("charge") || query.contains("charging") -> pick("battery:")
            query.contains("temperature") || query.contains("thermal") -> pick("battery:")
            query.contains("ram") || query.contains("memory") -> pick("ram:", "app memory:")
            query.contains("storage") || query.contains("disk") || query.contains("free space") -> pick("storage:")
            query.contains("cpu") || query.contains("processor") -> pick("cpu:")
            query.contains("gpu") || query.contains("graphics") -> pick("gpu:")
            query.contains("display") || query.contains("screen resolution") || query.contains("resolution") -> pick("display:")
            query.contains("time") || query.contains("କେତେ ବାଜି") || query.contains("ସମୟ") -> pick("india time:")
            query.contains("location") || query.contains("ଅବସ୍ଥାନ") || query.contains("where am i") -> pick("location:")
            query.contains("model") -> pick("model:")
            query.contains("manufacturer") || query.contains("brand") -> pick("manufacturer:")
            query.contains("android version") -> pick("android:")
            query.contains("device information") || query.contains("device info") || query.contains("phone information") || query.contains("phone info") ->
                pick("model:", "android:", "battery:")
            else -> "ମୁଁ ତୁମର ପଚରାଯାଇଥିବା device information ଅନୁସାରେ କେବଳ ଦରକାରୀ ତଥ୍ୟ ଦେବି।"
        }.ifBlank { "ଡିଭାଇସ୍ ସୂଚନା ଏବେ ମିଳିଲା ନାହିଁ।" }
    }

    private data class Battery(val percent: Int, val charging: Boolean, val temperatureC: Float?)
    private fun battery(context: Context): Battery {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        val percent = if (level >= 0 && scale > 0) kotlin.math.round((level * 100f) / scale).toInt() else -1
        return Battery(percent, status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL, if (temp != null && temp != Int.MIN_VALUE) temp / 10f else null)
    }

    private data class Memory(val totalMb: Long, val availableMb: Long, val appPssMb: Long)
    private fun memory(context: Context): Memory {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo().also(am::getMemoryInfo)
        val debugInfo = Debug.MemoryInfo()
        Debug.getMemoryInfo(debugInfo)
        return Memory(info.totalMem / MB, info.availMem / MB, debugInfo.totalPss.toLong() / 1024L)
    }

    private data class Storage(val totalGb: String, val freeGb: String)
    private fun storage(): Storage {
        val stat = StatFs(File("/data").path)
        val total = stat.totalBytes.toDouble() / GB
        val free = stat.availableBytes.toDouble() / GB
        return Storage(format(total), format(free))
    }

    private fun cpu(): String {
        val cores = Runtime.getRuntime().availableProcessors()
        val freq = runCatching { File("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq").readText().trim().toLong() / 1000L }.getOrNull()
        return if (freq != null && freq > 0) "$cores cores, current cpu0 ${freq} MHz" else "$cores available processors; frequency unavailable to this app"
    }

    private data class Gpu(val renderer: String, val utilization: Int?, val headroom: Float?)
    private fun gpu(context: Context): Gpu = Gpu(readGlRenderer() ?: buildFallbackGpuName(), readGpuUtilization(), null)

    private fun readGlRenderer(): String? = runCatching {
        var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
        var context: EGLContext = EGL14.EGL_NO_CONTEXT
        var surface: EGLSurface = EGL14.EGL_NO_SURFACE
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) return@runCatching null
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) return@runCatching null
        val configs = arrayOfNulls<EGLConfig>(1)
        val num = IntArray(1)
        val attribs = intArrayOf(EGL14.EGL_RENDERABLE_TYPE, 4, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_NONE)
        if (!EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, num, 0) || num[0] == 0) return@runCatching null
        val config = configs[0] ?: return@runCatching null
        val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
        if (context == EGL14.EGL_NO_CONTEXT) return@runCatching null
        val surfaceAttribs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
        surface = EGL14.eglCreatePbufferSurface(display, config, surfaceAttribs, 0)
        if (surface == EGL14.EGL_NO_SURFACE || !EGL14.eglMakeCurrent(display, surface, surface, context)) return@runCatching null
        GLES20.glGetString(GLES20.GL_RENDERER)?.takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun buildFallbackGpuName(): String {
        val hardware = listOfNotNull(Build.HARDWARE, Build.BOARD).joinToString(" ").lowercase(Locale.US)
        return when {
            hardware.contains("qcom") || hardware.contains("qualcomm") -> "Qualcomm GPU (exact renderer unavailable)"
            hardware.contains("mt") || hardware.contains("mediatek") -> "MediaTek GPU (exact renderer unavailable)"
            hardware.contains("exynos") -> "Samsung/Exynos GPU (exact renderer unavailable)"
            else -> "GPU renderer unavailable from standard Android APIs"
        }
    }

    private fun readGpuUtilization(): Int? = listOf(
        "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
        "/sys/class/kgsl/kgsl-3d0/gpu_busy_percent"
    ).asSequence().mapNotNull { path -> runCatching { File(path).readText().trim().removeSuffix("%").toIntOrNull() }.getOrNull() }
        .firstOrNull { it in 0..100 }

    private fun format(value: Double) = String.format(Locale.US, "%.1f", value)
    private fun format(value: Float) = String.format(Locale.US, "%.1f", value)
    private const val MB = 1024L * 1024L
    private const val GB = 1024.0 * 1024.0 * 1024.0
}
