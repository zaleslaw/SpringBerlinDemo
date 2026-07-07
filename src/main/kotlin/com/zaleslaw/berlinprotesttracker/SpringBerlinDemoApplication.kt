package com.zaleslaw.berlinprotesttracker

import com.zaleslaw.berlinprotesttracker.config.AppProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableConfigurationProperties(AppProperties::class)
class SpringBerlinDemoApplication

fun main(args: Array<String>) {
    runApplication<SpringBerlinDemoApplication>(*args)
}
