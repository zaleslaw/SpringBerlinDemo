package com.zaleslaw.berlinprotesttracker.query

import com.zaleslaw.berlinprotesttracker.domain.Demonstration
import com.zaleslaw.berlinprotesttracker.snapshot.DemonstrationReadModel
import org.springframework.stereotype.Service
import java.time.LocalTime

@Service
class DemonstrationQueryService(private val readModel: DemonstrationReadModel) {

    fun query(filter: DemonstrationFilter): List<Demonstration> {
        val snapshot = readModel.current() ?: return emptyList()

        return snapshot.events
            .asSequence()
            .applyFilter(filter)
            .sortedWith(
                compareBy<Demonstration> { it.date }
                    .thenBy { it.startTime ?: LocalTime.MAX }
                    .thenByDescending { it.impact.score.value }
            )
            .toList()
    }
}
