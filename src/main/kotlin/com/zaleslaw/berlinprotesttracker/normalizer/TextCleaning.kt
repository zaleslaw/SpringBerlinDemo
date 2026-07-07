package com.zaleslaw.berlinprotesttracker.normalizer

import com.zaleslaw.berlinprotesttracker.domain.SourceHash
import java.security.MessageDigest

object TextCleaning {

    fun clean(text: String?): String? {
        if (text == null) return null
        return text
            .replace('\u00a0', ' ')   // non-breaking space
            .replace('\u200b', ' ')   // zero-width space
            .replace("  +".toRegex(), " ")
            .trim()
            .takeIf { it.isNotEmpty() }
    }

    fun cleanRequired(text: String): String =
        clean(text) ?: ""

    fun toLowerCaseGerman(text: String): String =
        text.lowercase()
            .replace('ä', 'a').replace("ae", "ae")
            .replace('ö', 'o').replace("oe", "oe")
            .replace('ü', 'u').replace("ue", "ue")
            .replace('ß', 's')

    fun sourceHash(vararg parts: String?): SourceHash {
        val combined = parts.filterNotNull().joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(combined.toByteArray(Charsets.UTF_8))
        val hex = bytes.joinToString("") { "%02x".format(it) }
        return SourceHash(hex.take(16))
    }
}
