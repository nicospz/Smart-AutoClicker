package com.buzbuz.smartautoclicker.feature.throwlet

import android.content.Context
import kotlin.math.max
import kotlin.math.min

class PokemonCatalog internal constructor(private val names: List<String>) {
    fun allNames(): List<String> = names

    fun resolveExactName(name: String): Match? {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return null
        val index = names.indexOfFirst { it.equals(trimmed, ignoreCase = true) }
        if (index < 0) return null
        val catalogName = names[index]
        val key = normalize(catalogName).replace(' ', '-')
        return Match(key = key, name = catalogName, confidence = 100)
    }

    fun bestMatch(text: String): Match? {
        val normalizedText = normalize(text)
        if (normalizedText.isBlank()) return null
        return names.asSequence().map { name ->
            val n = normalize(name)
            val score = when {
                normalizedText.contains(n) -> 100
                else -> (100 - levenshteinWindowScore(normalizedText, n)).coerceIn(0, 100)
            }
            Match(key = n.replace(' ', '-'), name = name, confidence = score)
        }.maxByOrNull { it.confidence }?.takeIf { it.confidence >= MIN_MATCH_CONFIDENCE }
    }

    data class Match(val key: String, val name: String, val confidence: Int)

    fun spriteAssetPath(displayName: String): String? {
        val index = names.indexOfFirst { it.equals(displayName, ignoreCase = true) }
        if (index < 0) return null
        val slug = normalize(names[index]).replace(' ', '-')
        return "front_default/%04d-%s.png".format(index + 1, slug)
    }

    companion object {
        private const val MIN_MATCH_CONFIDENCE = 68

        @Volatile private var instance: PokemonCatalog? = null
        fun get(context: Context): PokemonCatalog = instance ?: synchronized(this) {
            instance ?: PokemonCatalog(context.assets.open("pokemon_names.txt").bufferedReader().readLines().filter { it.isNotBlank() }).also { instance = it }
        }
    }
}

private fun normalize(value: String): String = value.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim().replace(Regex("\\s+"), " ")

private fun levenshteinWindowScore(haystack: String, needle: String): Int {
    if (needle.isBlank()) return 100
    val words = haystack.split(' ').filter { it.isNotBlank() }
    val candidates = buildList {
        add(haystack)
        for (i in words.indices) for (j in i + 1..min(words.size, i + 4)) add(words.subList(i, j).joinToString(" "))
        words.forEach { word -> addCharWindows(word, needle.length) }
    }
    val best = candidates.minOfOrNull { levenshtein(it, needle) } ?: needle.length
    return (best * 100) / max(needle.length, 1)
}

private fun MutableList<String>.addCharWindows(word: String, targetLength: Int) {
    if (targetLength < 4 || word.length <= targetLength) return
    val minLength = max(1, targetLength - 2)
    val maxLength = min(word.length, targetLength + 2)
    for (length in minLength..maxLength) {
        for (start in 0..word.length - length) add(word.substring(start, start + length))
    }
}

private fun levenshtein(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    var prev = IntArray(b.length + 1) { it }
    var cur = IntArray(b.length + 1)
    for (i in 1..a.length) {
        cur[0] = i
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
        }
        val tmp = prev; prev = cur; cur = tmp
    }
    return prev[b.length]
}
