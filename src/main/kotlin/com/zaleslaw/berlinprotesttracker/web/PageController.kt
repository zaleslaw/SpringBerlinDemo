package com.zaleslaw.berlinprotesttracker.web

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

@Controller
class PageController {

    @GetMapping("/")
    fun index(): String = "index"
}
