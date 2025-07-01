package org.breizhcamp.video.uploader.thumb

import org.apache.commons.io.FileUtils
import org.breizhcamp.video.uploader.CamaalothUploaderProps
import org.breizhcamp.video.uploader.event.service.EventService
import org.breizhcamp.video.uploader.file.service.FileService
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.text.Charsets.UTF_8

@Service
class ThumbGeneratorSrv(
    private val eventService: EventService,
    private val fileService: FileService,
    private val props: CamaalothUploaderProps
) {

    fun generateAllThumbs() {
        val svgThumb = FileUtils.readFileToString(Paths.get(props.assetsDir, "thumb.svg").toFile(), UTF_8)

        eventService.getEvents()
            .forEach { event ->
                if (event.speakers != null) {
                    var speakers = event.speakers.replace("[\\\\/:*?\"<>|]".toRegex(), "-")
                    if (speakers.endsWith(", ")) speakers = speakers.substring(0, speakers.length - 2)

                    makeThumb(
                        svg = svgThumb,
                        title = event.name,
                        speakers = speakers,
                        destDir = fileService.recordingDir.resolve(event.buildDirName()),
                        targetName = "thumb.png"
                    )
                } else {
                    println("WARNING: speaker is null for ${event.id} ${event.name}")
                }
            }
    }

    private fun makeThumb(svg: String, title: String?, speakers: String, destDir: Path, targetName: String) {
        val replaced = Files.createTempFile("thumb_", ".svg")
        var content = svg.replace("TitreTalk", title!!)
        content = content.replace("SpeakersTalk", speakers)

        Files.write(replaced, content.toByteArray(UTF_8))

        if (!Files.exists(destDir)) {
            Files.createDirectory(destDir)
        }
        if (!Files.exists(destDir.resolve(targetName))) {
            val cmd =
                arrayOf("/usr/bin/inkscape", "--export-png=$destDir/$targetName", replaced.toAbsolutePath().toString())

            println(Arrays.toString(cmd))
            val p = Runtime.getRuntime().exec(cmd)
            val exit = p.waitFor()
            println("exit: $exit")
        }

        Files.delete(replaced)
    }
}

@SpringBootApplication
@EnableConfigurationProperties(CamaalothUploaderProps::class)
class ThumbGeneratorApplication {

    @Bean
    fun init(generatorSrv: ThumbGeneratorSrv) = CommandLineRunner {
        generatorSrv.generateAllThumbs();
    }

    @Bean
    fun eventSrv(props: CamaalothUploaderProps) = EventService(props)

    @Bean
    fun fileSrv(eventService: EventService, props: CamaalothUploaderProps) = FileService(eventService, props)
}

fun main(args: Array<String>) {
    SpringApplicationBuilder(ThumbGeneratorApplication::class.java)
        .web(WebApplicationType.NONE)
        .run(*args)
}
