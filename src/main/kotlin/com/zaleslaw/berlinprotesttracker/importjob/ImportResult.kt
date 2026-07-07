package com.zaleslaw.berlinprotesttracker.importjob

sealed interface ImportResult {
    data class Success(val eventCount: Int) : ImportResult
    data class Failure(val reason: String) : ImportResult
    data object AlreadyRunning : ImportResult
}
