package org.breizhcamp.video.uploader.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.api.services.youtube.model.Channel
import com.google.api.services.youtube.model.ChannelListResponse
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e")
class E2ETest {

    @LocalServerPort
    lateinit var port: String

    val ytServer = MockWebServer().apply { start(20000) }

    @Test
    fun `should upload video`(){
        ytServer.enqueue(
            MockResponse().apply {
                setResponseCode(200)
                addHeader("Content-Type", "application/json")
                setBody(
                    jacksonObjectMapper().writeValueAsString(ChannelListResponse())
                )
            }
        )

        Playwright.create().use {
            val browser = it.firefox().launch(BrowserType.LaunchOptions().setHeadless(false).setSlowMo(1000.0))
            val page = browser.newPage()
            page.navigate("http://localhost:$port")

            val authBtn = page.locator("#yt-auth")
            authBtn.click()

            assertThat(page.locator("#yt-auth")).not().isVisible()
        }
    }

}