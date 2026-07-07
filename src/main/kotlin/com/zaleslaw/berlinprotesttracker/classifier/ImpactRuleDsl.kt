package com.zaleslaw.berlinprotesttracker.classifier

import com.zaleslaw.berlinprotesttracker.domain.DemonstrationCategory
import com.zaleslaw.berlinprotesttracker.domain.NormalizedDemonstration
import java.time.LocalTime

/**
 * A single keyword pre-normalized once, with its word-boundary regex compiled up front.
 * The rule set is built once (at classifier construction), so this moves all normalization
 * and regex compilation out of the per-event hot path in [ImpactClassifier.classify].
 */
class KeywordMatcher(val normalized: String) {
    private val regex: Regex? =
        if (normalized.isNotBlank() && normalized.none { it.isWhitespace() }) {
            Regex("(?<![\\p{L}\\p{N}])${Regex.escape(normalized)}(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
        } else {
            null // multi-word phrase → substring match
        }

    fun matches(text: String): Boolean = when {
        normalized.isBlank() -> false
        regex != null -> regex.containsMatchIn(text)
        else -> text.contains(normalized)
    }
}

data class TopicRule(
    val category: DemonstrationCategory,
    val keywords: List<String>,
    val topicWeight: Int,
    val publicLabel: String,
    val reason: String
) {
    /** Keywords normalized and compiled once, reused for every classified event. */
    val matchers: List<KeywordMatcher> = keywords.map { KeywordMatcher(it.normalizeForImpactMatching()) }
}

data class SignalRule(
    val name: String,
    val addImpact: Int,
    val reason: String,
    val predicate: (NormalizedDemonstration) -> Boolean
)

data class ImpactRuleSet(
    val topicRules: List<TopicRule>,
    val signalRules: List<SignalRule>
)

class TopicRuleBuilder(private val category: DemonstrationCategory) {
    private var keywords: List<String> = emptyList()
    private var topicWeight: Int = 10
    private var publicLabel: String = "Civic assembly"
    private var reason: String = "General civic assembly"

    fun keywords(vararg kw: String) {
        keywords = kw.toList()
    }

    fun topicWeight(value: Int) {
        topicWeight = value
    }

    fun publicLabel(text: String) {
        publicLabel = text
    }

    fun reason(text: String) {
        reason = text
    }

    fun build() = TopicRule(
        category = category,
        keywords = keywords,
        topicWeight = topicWeight,
        publicLabel = publicLabel,
        reason = reason
    )
}

class SignalRuleBuilder(private val name: String) {
    private var addImpact: Int = 0
    private var reason: String = ""
    private val predicates = mutableListOf<(NormalizedDemonstration) -> Boolean>()

    fun addImpact(value: Int) {
        addImpact = value
    }

    fun reason(text: String) {
        reason = text
    }

    fun whenParticipantsAtLeast(count: Int) {
        predicates += { it.participantCount != null && it.participantCount >= count }
    }

    fun whenStartsAfter(time: LocalTime) {
        predicates += { it.startTime != null && it.startTime.isAfter(time) }
    }

    fun whenHasRoute() {
        predicates += { !it.routeText.isNullOrBlank() }
    }

    fun whenRouteLooksLong(minParts: Int = 6) {
        predicates += {
            val route = it.routeText.orEmpty()
            route.split(" - ", " -> ", "-", "--", ",", ";")
                .map { part -> part.trim() }
                .count { part -> part.isNotBlank() } >= minParts
        }
    }

    fun whenTextContainsAny(vararg markers: String) {
        val matchers = markers.map { KeywordMatcher(it.normalizeForImpactMatching()) }
        predicates += { demo ->
            val text = listOfNotNull(
                demo.title,
                demo.rawText,
                demo.locationText,
                demo.routeText
            ).joinToString(" ").normalizeForImpactMatching()

            matchers.any { it.matches(text) }
        }
    }

    fun build() = SignalRule(
        name = name,
        addImpact = addImpact,
        reason = reason,
        predicate = { demo -> predicates.isNotEmpty() && predicates.all { it(demo) } }
    )
}

class ImpactRuleSetBuilder {
    private val topicRules = mutableListOf<TopicRule>()
    private val signalRules = mutableListOf<SignalRule>()

    fun topic(category: DemonstrationCategory, block: TopicRuleBuilder.() -> Unit) {
        topicRules += TopicRuleBuilder(category).apply(block).build()
    }

    fun signal(name: String, block: SignalRuleBuilder.() -> Unit) {
        signalRules += SignalRuleBuilder(name).apply(block).build()
    }

    fun build() = ImpactRuleSet(topicRules, signalRules)
}

fun impactRules(block: ImpactRuleSetBuilder.() -> Unit): ImpactRuleSet =
    ImpactRuleSetBuilder().apply(block).build()

fun String.normalizeForImpactMatching(): String =
    lowercase()
        .replace('ä', 'a')
        .replace('ö', 'o')
        .replace('ü', 'u')
        .replace('ß', 's')
        .replace("ae", "a")
        .replace("oe", "o")
        .replace("ue", "u")
        .replace(Regex("\\s+"), " ")
        .trim()
