package com.zaleslaw.berlinprotesttracker.classifier

import com.zaleslaw.berlinprotesttracker.domain.*
import org.springframework.stereotype.Component
import java.time.LocalTime

@Component
class ImpactClassifier {

    private val rules: ImpactRuleSet = impactRules {
        topic(DemonstrationCategory.EDUCATION_BUDGET) {
            keywords("schule", "lehrer", "lehrkraft", "bildung", "kita", "haushalt", "bildungsstreik")
            topicWeight(5)
            publicLabel("Education or budget assembly")
            reason("Education or budget-related topic")
        }
        topic(DemonstrationCategory.LABOR_SOCIAL_HOUSING) {
            keywords("arbeit", "gewerkschaft", "streik", "lohn", "sozial", "rente", "pflege", "miete", "wohnen", "gentrifizierung", "zwangsraeumung")
            topicWeight(10)
            publicLabel("Labor, social, or housing assembly")
            reason("Labor, social, or housing-related topic")
        }
        topic(DemonstrationCategory.HEALTH_CARE) {
            keywords("gesundheit", "krankenhaus", "pflege", "medizin", "arzt", "klinik")
            topicWeight(5)
            publicLabel("Health or care-related assembly")
            reason("Health or care-related topic")
        }
        topic(DemonstrationCategory.CLIMATE_ENVIRONMENT) {
            keywords("klima", "umwelt", "fridays", "extinction", "greenpeace", "co2", "energie", "fossil")
            topicWeight(10)
            publicLabel("Climate or environment assembly")
            reason("Climate or environment-related topic")
        }
        topic(DemonstrationCategory.MOBILITY_TRANSPORT) {
            keywords("verkehr", "autobahn", "fahrrad", "bahn", "oepnv", "strassenbahn", "radweg", "schulweg")
            topicWeight(5)
            publicLabel("Mobility-related assembly")
            reason("Mobility-related topic")
        }
        topic(DemonstrationCategory.LOCAL_CIVIC) {
            keywords("bezirk", "buerger", "kiez", "nachbarschaft", "wahl", "kommunal")
            topicWeight(5)
            publicLabel("Local civic assembly")
            reason("Local civic topic")
        }
        topic(DemonstrationCategory.HUMAN_RIGHTS_REPRESSIONS) {
            keywords("menschenrechte", "hinrichtung", "gefangene", "verfolgung", "freiheit", "folter", "iran", "china", "falun", "dafa", "uigur")
            topicWeight(15)
            publicLabel("Human-rights assembly")
            reason("Human-rights or repression-related topic")
        }
        topic(DemonstrationCategory.PEACE_GEOPOLITICS) {
            keywords("frieden", "krieg", "waffen", "waffenstillstand", "nato", "ukraine", "russland", "putin", "botschaft")
            topicWeight(15)
            publicLabel("Peace or foreign-policy assembly")
            reason("Peace or foreign-policy topic")
        }
        topic(DemonstrationCategory.INTERNATIONAL_CONFLICT) {
            keywords("gaza", "palaestina", "palestine", "israel", "nahost", "zionismus", "antisemitismus", "hamas", "tel aviv")
            topicWeight(15)
            publicLabel("International conflict-related assembly")
            reason("International conflict-related topic")
        }
        topic(DemonstrationCategory.DEMOCRACY_MEMORY_ANTI_EXTREMISM) {
            keywords("demokratie", "nazis", "rechtsextrem", "afd", "antisemitismus", "ns-verherrlichung", "erinnerung", "gedenken")
            topicWeight(15)
            publicLabel("Democracy, memory, or anti-extremism assembly")
            reason("Democracy, memory, or anti-extremism-related topic")
        }
        topic(DemonstrationCategory.SYSTEM_CRITIQUE_PANDEMIC) {
            keywords("corona", "rki", "who", "pandemie", "gesundheitsdiktatur", "querdenker", "agenda", "korruption")
            topicWeight(15)
            publicLabel("System-critical or pandemic-related assembly")
            reason("System-critical or pandemic-related topic")
        }
        topic(DemonstrationCategory.FAITH_IDENTITY_CULTURE) {
            keywords("kirche", "moschee", "synagoge", "religion", "islam", "christlich", "juedisch", "gebet", "identitaet")
            topicWeight(10)
            publicLabel("Faith, identity, or cultural assembly")
            reason("Faith, identity, or cultural topic")
        }
        topic(DemonstrationCategory.CULTURE_FESTIVAL) {
            keywords("kultur", "kunst", "festival", "musik", "theater", "film", "literatur")
            topicWeight(5)
            publicLabel("Cultural assembly")
            reason("Cultural topic")
        }
        topic(DemonstrationCategory.OTHER_POLITICAL) {
            keywords("politik", "partei", "bundestag", "regierung", "demokratie", "migration", "abschiebung")
            topicWeight(10)
            publicLabel("Political assembly")
            reason("General political topic")
        }

        // Crowd signals are additive by design: a 10k+ event fires BOTH "large crowd" (+20) and
        // "very large crowd" (+15) for a combined +35. The second tier is an increment on top of
        // the first, not a replacement — hence its smaller value.
        signal("large crowd") {
            whenParticipantsAtLeast(2_000)
            addImpact(20)
            reason("Large expected crowd")
        }
        signal("very large crowd") {
            whenParticipantsAtLeast(10_000)
            addImpact(15)
            reason("Very large expected crowd")
        }
        signal("moving route") {
            whenHasRoute()
            addImpact(25)
            reason("Moving route affects multiple streets")
        }
        signal("long route") {
            whenRouteLooksLong(minParts = 6)
            addImpact(15)
            reason("Route appears to pass through several locations")
        }
        signal("all-day or long-duration event") {
            whenTextContainsAny("00:00", "23:59", "ganztagig", "dauermahnwache", "camp", "hungerstreik")
            addImpact(20)
            reason("Event appears to last for a large part of the day")
        }
        signal("evening event") {
            whenStartsAfter(LocalTime.of(18, 0))
            addImpact(5)
            reason("Event takes place in the evening")
        }
        signal("possible counter-event context") {
            whenTextContainsAny(
                "gegendemo",
                "gegenkundgebung",
                "gegenprotest",
                "blockade",
                "gegen afd",
                "gegen nazis",
                "gegen rechtsextrem",
                "stopp afd"
            )
            addImpact(20)
            reason("Wording suggests possible counter-event or blockade context")
        }
    }

    fun classify(demo: NormalizedDemonstration): ImpactAssessment {
        val combined = listOfNotNull(
            demo.title,
            demo.rawText,
            demo.locationText,
            demo.routeText,
            demo.district
        ).joinToString(" ").normalizeForImpactMatching()

        val matchedTopic = rules.topicRules
            .mapNotNull { rule ->
                val matchedKeywords = rule.matchers
                    .filter { it.matches(combined) }
                    .map { it.normalized }

                if (matchedKeywords.isEmpty()) null else rule to matchedKeywords
            }
            .maxWithOrNull(
                compareBy<Pair<TopicRule, List<String>>> { it.second.size }
                    .thenBy { it.first.topicWeight }
            )

        val topic = matchedTopic?.first
        val reasons = mutableListOf<String>()
        val evidence = mutableListOf<String>()

        var totalImpact = topic?.topicWeight ?: 5

        if (topic != null) {
            reasons += topic.reason
            evidence += "topic: ${topic.publicLabel}"
            evidence += "keywords: ${matchedTopic.second.joinToString(", ")}"
        } else {
            reasons += "No specific topic matched"
            evidence += "topic: fallback"
        }

        for (signal in rules.signalRules) {
            if (signal.predicate(demo)) {
                totalImpact += signal.addImpact
                reasons += signal.reason
                evidence += "signal: ${signal.name}"
            }
        }

        val score = ImpactScore(totalImpact.coerceIn(0, 100))

        return ImpactAssessment(
            category = topic?.category ?: DemonstrationCategory.OTHER,
            publicLabel = topic?.publicLabel ?: "Civic assembly",
            level = ImpactLevel.fromScore(score.value),
            score = score,
            reasons = reasons.distinct(),
            evidence = evidence.distinct()
        )
    }
}
