package com.zaleslaw.berlinprotesttracker.snapshot

import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicReference

@Component
class DemonstrationReadModel {

    private val current = AtomicReference<DemonstrationSnapshot?>()

    fun current(): DemonstrationSnapshot? = current.get()

    fun replace(snapshot: DemonstrationSnapshot) {
        current.set(snapshot)
    }
}
