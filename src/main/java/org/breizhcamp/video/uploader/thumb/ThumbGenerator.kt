package org.breizhcamp.video.uploader.thumb

import org.apache.commons.io.FileUtils
import org.breizhcamp.video.uploader.CamaalothUploaderProps
import org.breizhcamp.video.uploader.services.EventSrv
import org.breizhcamp.video.uploader.services.FileSrv
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Service
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.text.Charsets.UTF_8
import org.springframework.boot.builder.SpringApplicationBuilder

@Service
class ThumbGeneratorSrv(private val eventSrv: EventSrv, private val fileSrv: FileSrv, private val props: CamaalothUploaderProps) {

    fun generateAllThumbs() {
        val svgThumb = FileUtils.readFileToString(Paths.get(props.assetsDir, "thumb.svg").toFile(), UTF_8)
        val svgIntroVideo = FileUtils.readFileToString(Paths.get(props.assetsDir, "intro.svg").toFile(), UTF_8)

        for (event in eventSrv.list()) {
            if (event.venue == "Amphi B" || event.venue == "Amphi C" || event.venue == "Amphi D") {
                var speakers = event.speakers!!.replace("[\\\\/:*?\"<>|]".toRegex(), "-")
                if (speakers.endsWith(", ")) speakers = speakers.substring(0, speakers.length - 2)

                val destDir = fileSrv.recordingDir.resolve(fileSrv.buildDirName(event))
                makeThumb(svgThumb, event.name, speakers, destDir, "thumb.png")
                makeThumb(svgIntroVideo, event.name, speakers, destDir, "intro.png")
            }
        }
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun makeThumb(svg: String, title: String?, speakers: String, destDir: Path, targetName: String) {
        val replaced = Files.createTempFile("thumb_", ".svg")
        var content = svg.replace("TitreTalk", title!!)
        content = content.replace("SpeakersTalk", speakers)

        Files.write(replaced, content.toByteArray(UTF_8))

        if (!Files.exists(destDir)) {
            Files.createDirectory(destDir)
        }

        val cmd = arrayOf("/usr/bin/inkscape", "--export-png=$destDir/$targetName", replaced.toAbsolutePath().toString())

        println(Arrays.toString(cmd))
        val p = Runtime.getRuntime().exec(cmd)
        val exit = p.waitFor()
        println("exit: $exit")

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
    fun eventSrv() = EventSrv()

    @Bean
    fun fileSrv(eventSrv: EventSrv, props: CamaalothUploaderProps) = FileSrv(eventSrv, props)
}

fun main(args: Array<String>) {
    SpringApplicationBuilder(ThumbGeneratorApplication::class.java)
            .web(WebApplicationType.NONE)
            .run(*args)
}
