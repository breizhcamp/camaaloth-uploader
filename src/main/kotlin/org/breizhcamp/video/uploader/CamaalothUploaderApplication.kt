package org.breizhcamp.video.uploader

import org.breizhcamp.video.uploader.thumb.ThumbGeneratorApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType

@SpringBootApplication
@EnableConfigurationProperties(CamaalothUploaderProps::class)
@ComponentScan(
    excludeFilters = [ComponentScan.Filter(
        value = [(ThumbGeneratorApplication::class)],
        type = FilterType.ASSIGNABLE_TYPE
    )]
)
class CamaalothUploaderApplication

fun main(args: Array<String>) {
    runApplication<CamaalothUploaderApplication>(*args)
}
