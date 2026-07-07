package com.zaleslaw.berlinprotesttracker.web

import com.zaleslaw.berlinprotesttracker.importjob.DemonstrationImportService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class ImportTriggerResponse(val status: String, val message: String)

/**
 * Internal maintenance endpoints. Authentication is handled by [InternalTokenFilter] for the whole
 * internal path prefix, so handlers assume the caller is already authorized. Imports are kicked off
 * asynchronously and return 202 Accepted immediately — progress is polled via the status endpoints.
 */
@RestController
@RequestMapping("/internal")
class InternalImportController(
    private val importService: DemonstrationImportService
) {

    @PostMapping("/import/run")
    fun runImport(): ResponseEntity<ImportTriggerResponse> =
        if (importService.tryStartImportAsync()) {
            ResponseEntity.accepted()
                .body(ImportTriggerResponse("accepted", "Import started; poll /api/snapshot/status for progress"))
        } else {
            ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ImportTriggerResponse("already_running", "An import is already in progress"))
        }
}
