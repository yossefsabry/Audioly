package com.audioly.app.player

import com.audioly.app.network.AppHttpClient
import com.audioly.app.util.AppLogger
import com.audioly.shared.player.VttParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder

/**
 * Translates subtitle cue text using Google Translate's free API.
 *
 * Used as a **fallback** when YouTube's timedtext `&tlang=en` endpoint
 * returns 429 (rate-limited) or untranslated content.
 *
 * Flow:
 *  1. Parse source-language VTT into cues
 *  2. Batch cue texts into groups ≤ [MAX_CHARS_PER_BATCH]
 *  3. Translate each batch via Google Translate
 *  4. Reconstruct VTT with translated text
 */
object SubtitleTranslator {

    private const val TAG = "SubtitleTranslator"
    private const val TRANSLATE_URL = "https://translate.googleapis.com/translate_a/single"

    /**
     * Separator placed between cue texts within a batch so we can split the
     * translated result back into individual cues. Chosen to be unlikely to
     * appear in real subtitle text and unlikely to be modified by translation.
     */
    private const val BATCH_SEPARATOR = "\n⸻\n"
    private const val MAX_CHARS_PER_BATCH = 4000

    /**
     * Translates [sourceVtt] from [sourceLang] to [targetLang].
     * Returns translated VTT string, or null on failure.
     */
    suspend fun translateVtt(
        sourceVtt: String,
        sourceLang: String,
        targetLang: String,
    ): String? = withContext(Dispatchers.IO) {
        try {
            val cues = VttParser.parse(sourceVtt)
            if (cues.isEmpty()) {
                AppLogger.w(TAG, "Source VTT has no cues to translate")
                return@withContext null
            }

            val texts = cues.map { it.text }
            val translated = batchTranslate(texts, sourceLang, targetLang)
            if (translated == null) {
                AppLogger.w(TAG, "Batch translation returned null")
                return@withContext null
            }

            // Reconstruct VTT
            val sb = StringBuilder("WEBVTT\n\n")
            for (i in cues.indices) {
                sb.append(fmtTs(cues[i].startMs))
                sb.append(" --> ")
                sb.append(fmtTs(cues[i].endMs))
                sb.append("\n")
                sb.append(translated[i])
                sb.append("\n\n")
            }

            AppLogger.d(TAG, "Translated ${cues.size} cues from $sourceLang → $targetLang")
            sb.toString()
        } catch (e: Exception) {
            AppLogger.w(TAG, "translateVtt failed: ${e.message}")
            null
        }
    }

    // ─── Batching ────────────────────────────────────────────────────────────

    /**
     * Translates a list of strings by grouping them into batches that fit
     * within the API character limit, translating each batch, and splitting
     * the results back. Falls back to per-cue translation if the batch
     * separator is mangled by the translation engine.
     */
    private fun batchTranslate(
        texts: List<String>,
        sourceLang: String,
        targetLang: String,
    ): List<String>? {
        // Group into batches
        data class Batch(val indices: List<Int>)

        val batches = mutableListOf<Batch>()
        var currentIndices = mutableListOf<Int>()
        var currentSize = 0

        for (i in texts.indices) {
            val needed = texts[i].length + BATCH_SEPARATOR.length
            if (currentSize + needed > MAX_CHARS_PER_BATCH && currentIndices.isNotEmpty()) {
                batches.add(Batch(currentIndices))
                currentIndices = mutableListOf()
                currentSize = 0
            }
            currentIndices.add(i)
            currentSize += needed
        }
        if (currentIndices.isNotEmpty()) batches.add(Batch(currentIndices))

        // Translate each batch
        val results = arrayOfNulls<String>(texts.size)

        for (batch in batches) {
            val joined = batch.indices.joinToString(BATCH_SEPARATOR) { texts[it] }
            val translated = translateSingle(joined, sourceLang, targetLang) ?: return null

            val parts = translated.split(BATCH_SEPARATOR)
            if (parts.size == batch.indices.size) {
                // Separator survived translation intact — fast path
                for (j in batch.indices.indices) {
                    results[batch.indices[j]] = parts[j].trim()
                }
            } else {
                // Separator was modified by translator — fall back to per-cue
                AppLogger.d(TAG, "Batch separator mangled (got ${parts.size} parts, expected ${batch.indices.size}), translating individually")
                for (idx in batch.indices) {
                    val individual = translateSingle(texts[idx], sourceLang, targetLang)
                        ?: return null
                    results[idx] = individual.trim()
                }
            }
        }

        return results.map { it ?: "" }
    }

    // ─── Single request ─────────────────────────────────────────────────────

    /**
     * Sends a single translation request to Google Translate.
     * Uses POST with form body to avoid URL length limits.
     */
    private fun translateSingle(text: String, sourceLang: String, targetLang: String): String? {
        return try {
            val url = "$TRANSLATE_URL?client=gtx&sl=$sourceLang&tl=$targetLang&dt=t"
            val body = FormBody.Builder()
                .add("q", text)
                .build()
            val request = Request.Builder()
                .url(url)
                .post(body)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            val response = AppHttpClient.base.newCall(request).execute()
            if (!response.isSuccessful) {
                AppLogger.w(TAG, "Google Translate HTTP ${response.code}")
                response.close()
                return null
            }

            val json = response.body?.string() ?: return null
            parseTranslateResponse(json)
        } catch (e: Exception) {
            AppLogger.w(TAG, "Google Translate request failed: ${e.message}")
            null
        }
    }

    /**
     * Parses Google Translate's response format:
     * `[[["translated text","source text",null,null,N],...],...,null,"ko",...]`
     */
    private fun parseTranslateResponse(json: String): String? {
        return try {
            val root = JSONArray(json)
            val sentences = root.optJSONArray(0) ?: return null
            val sb = StringBuilder()
            for (i in 0 until sentences.length()) {
                val sentence = sentences.optJSONArray(i) ?: continue
                sb.append(sentence.optString(0, ""))
            }
            sb.toString().ifBlank { null }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed to parse translate response: ${e.message}")
            null
        }
    }

    // ─── Util ───────────────────────────────────────────────────────────────

    private fun fmtTs(ms: Long): String {
        val h = ms / 3_600_000
        val m = (ms % 3_600_000) / 60_000
        val s = (ms % 60_000) / 1_000
        val mil = ms % 1_000
        return "%02d:%02d:%02d.%03d".format(h, m, s, mil)
    }
}
