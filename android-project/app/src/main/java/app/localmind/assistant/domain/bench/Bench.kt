package app.localmind.assistant.domain.bench

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/**
 * One benchmark of a model on this device (1.52): how fast it reads a prompt and writes a reply.
 * Kept on the device only, with its settings, not in backups: the numbers describe this device.
 */
@Serializable
data class BenchRun(
    val id: String,
    val at: Long,
    /** The model's file name, and the name shown for it. */
    val model: String,
    val modelName: String,
    /** "GPU" or "CPU": where it really ran. */
    val backend: String,
    /** CPU threads (the GPU still uses a few for its own work). */
    val threads: Int,
    val promptTokens: Int,
    /** Medians over [Bench.RUNS] runs, in tokens a second. */
    val readTps: Double,
    val writeTps: Double,
    val loadSeconds: Double,
    /** The app's memory with the model loaded (proportional set size), in MB. */
    val memoryMb: Int,
    /** How far the writing speed moved between runs, as a share of its median. */
    val spreadPct: Double,
    val device: String,
    /** The GPU was asked for but the model ran on the CPU (too big for it, or no GPU support). */
    val fellBack: Boolean = false
)

/** What the engine measured in one benchmark: the raw times, before they're summed up. */
data class BenchTiming(
    val onGpu: Boolean,
    val threads: Int,
    val promptTokens: Int,
    val readTps: List<Double>,
    val writeTps: List<Double>,
    val loadMillis: Long,
    val memoryMb: Int
)

/** Where a benchmark runs. */
enum class BenchBackend(val label: String) { GPU("GPU"), CPU("CPU"), BOTH("Both") }

object Bench {
    /** Each benchmark reads the prompt and writes the reply this many times; the medians count. */
    const val RUNS = 3
    /** Tokens written per run. */
    const val WRITE_TOKENS = 128
    /** Runs kept on the device, newest first. */
    const val MAX_KEPT = 60

    /** Tokens a second, or 0 when nothing was timed. */
    fun tps(tokens: Int, millis: Long): Double = if (tokens <= 0 || millis <= 0) 0.0 else tokens * 1000.0 / millis

    fun median(xs: List<Double>): Double {
        if (xs.isEmpty()) return 0.0
        val s = xs.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    /** The range between runs as a percentage of the median: small means the numbers are steady. */
    fun spreadPct(xs: List<Double>): Double {
        val m = median(xs)
        if (xs.size < 2 || m <= 0.0) return 0.0
        return (xs.max() - xs.min()) / m * 100
    }

    /** The threads replies use: the native side's own rule (cores less two, between 2 and 4). */
    fun usualThreads(cores: Int): Int = (cores - 2).coerceIn(2, 4)

    /** The thread counts worth trying on a device with [cores] cores, the usual one first. */
    fun threadChoices(cores: Int): List<Int> =
        (listOf(usualThreads(cores)) + listOf(4, 6, 8).filter { it <= cores }).distinct().sorted()

    /** The summary of one benchmark, as kept. */
    fun run(
        id: String, at: Long, model: String, modelName: String, asked: BenchBackend,
        timing: BenchTiming, device: String
    ): BenchRun = BenchRun(
        id = id, at = at, model = model, modelName = modelName,
        backend = if (timing.onGpu) "GPU" else "CPU",
        threads = timing.threads,
        promptTokens = timing.promptTokens,
        readTps = median(timing.readTps),
        writeTps = median(timing.writeTps),
        loadSeconds = timing.loadMillis / 1000.0,
        memoryMb = timing.memoryMb,
        spreadPct = spreadPct(timing.writeTps),
        device = device,
        fellBack = asked == BenchBackend.GPU && !timing.onGpu
    )

    /** A speed for the screen: one decimal under 100, whole numbers above. */
    fun speed(tps: Double): String = when {
        tps <= 0.0 -> "–"
        tps < 100 -> "%.1f".format(java.util.Locale.ROOT, tps)
        else -> tps.roundToInt().toString()
    }

    /** "CPU · 4" or "GPU". */
    fun where(r: BenchRun): String = if (r.backend == "CPU") "CPU · ${r.threads}" else "GPU"

    /** Plain text to paste on another device or in a chat. */
    fun asText(runs: List<BenchRun>, device: String, date: (Long) -> String): String = buildString {
        append("SlothLlama benchmark · ").append(device).append('\n')
        append("Reading a ~512-token prompt and writing ").append(WRITE_TOKENS).append(" tokens, median of ").append(RUNS).append(" runs (tokens/s)\n")
        runs.forEach { r ->
            append(date(r.at)).append(" · ").append(r.modelName).append(" · ").append(where(r))
            append(" · reading ").append(speed(r.readTps)).append(" · writing ").append(speed(r.writeTps))
            append(" · load ").append("%.1f".format(java.util.Locale.ROOT, r.loadSeconds)).append(" s")
            append(" · memory ").append("%.1f".format(java.util.Locale.ROOT, r.memoryMb / 1024.0)).append(" GB")
            if (r.fellBack) append(" · asked for the GPU, ran on the CPU")
            append('\n')
        }
    }.trimEnd()

    /** Set for the model before the prompt: nothing that asks it to stop early. */
    const val SYSTEM = "You are a storyteller. Write long, flowing prose with no headings or lists."

    /**
     * The prompt (about 512 tokens with the chat template, for most models): an original passage
     * and a request to go on, so the model has a reason to write the whole reply.
     */
    val PROMPT: String = """
        Continue this story for at least four hundred words, in the same voice, without summarising it.

        The river was still grey when the ferryman untied his boat. He had crossed it every morning
        for thirty years, and in all that time he had never seen the water so quiet. On the far bank
        the market was only beginning to wake: a baker pulled shutters up, a boy carried two buckets
        of water from the well, and somewhere a radio played an old song about the sea. The ferryman
        liked this hour best. Nobody needed him yet, and the town looked the way it must have looked
        before any of the people in it were born.

        His first passenger arrived as the sun cleared the hills. She was a young woman with a
        leather case under one arm and a notebook in her hand, and she asked how long the crossing
        would take. Twenty minutes, he said, if the wind stayed down. She sat at the front of the
        boat and wrote without stopping, looking up now and then at the water, the herons standing
        in the shallows and the long line of willows that hid the old mill. When he asked what she
        was writing, she told him she was counting things: boats, birds, bridges, the number of
        windows lit in the houses along the bank. Her grandfather had made the same journey sixty
        years ago, she said, and had written down everything he saw. She wanted to know what had
        changed.

        The ferryman thought about that for a while. The mill had closed when he was a boy. The
        bridge upstream had been built and then rebuilt after the flood. The herons had come back
        only in the last few years, after the factories moved away. But the market was the same,
        and the bells of the church still rang the hour a little late, and the river still smelled
        of mud and mint in the early morning. He told her all of this, and she wrote it down,
        and for the rest of the crossing neither of them spoke.

        When they reached the other side she thanked him, paid, and stepped onto the stones. Then
        she turned back, opened the leather case and took out a small, worn notebook bound with
        string. This was his, she said. Read the page with the folded corner. The ferryman opened
        it, and on that page, in faded pencil, he found his own father's name.
    """.trimIndent()
}
