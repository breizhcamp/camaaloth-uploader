package org.breizhcamp.video.uploader

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource
import java.nio.file.Paths

class CamaalothUploaderPropsTest {

    private fun bind(vararg properties: Pair<String, String>): CamaalothUploaderProps =
        Binder(MapConfigurationPropertySource(properties.toMap()))
            .bindOrCreate("camaaloth-uploader", CamaalothUploaderProps::class.java)

    @Test
    fun `should look for the assets inside the recording directory by default`() {
        val props = bind("camaaloth-uploader.recording-dir" to "/Volumes/BrzhCampZ1/2026")

        assertThat(props.assetsDir).isEqualTo(Paths.get("/Volumes/BrzhCampZ1/2026", "assets").toString())
    }

    @Test
    fun `should keep the assets directory given on the command line`() {
        val props = bind(
            "camaaloth-uploader.recording-dir" to "/Volumes/BrzhCampZ1/2026",
            "camaaloth-uploader.assets-dir" to "assets-2025",
        )

        assertThat(props.assetsDir).isEqualTo("assets-2025")
    }

    @Test
    fun `should default both directories when nothing is given`() {
        val props = bind()

        assertThat(props.recordingDir).isEqualTo("videos")
        assertThat(props.assetsDir).isEqualTo(Paths.get("videos", "assets").toString())
    }
}
