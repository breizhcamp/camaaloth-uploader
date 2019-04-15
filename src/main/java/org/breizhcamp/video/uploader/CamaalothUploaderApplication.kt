package org.breizhcamp.video.uploader

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(CamaalothUploaderProps::class)
class CamaalothUploaderApplication

fun main(args: Array<String>) {
    runApplication<CamaalothUploaderApplication>(*args)
}
