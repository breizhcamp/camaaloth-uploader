package org.breizhcamp.video.uploader.controller

import com.google.api.services.youtube.model.ChannelListResponse
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import okhttp3.mockwebserver.MockWebServer
import org.breizhcamp.video.uploader.enqueueObject
import org.breizhcamp.video.uploader.verifyRequest
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.http.HttpMethod
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e")
class E2ETest {

    @LocalServerPort
    lateinit var port: String

    private val ytServer = MockWebServer().apply { start(20000) }

    @Test
    fun `should upload video`() {
        ytServer.enqueueObject(ChannelListResponse())

        Playwright.create().use {
            val browser = it.firefox().launch(BrowserType.LaunchOptions().setHeadless(false).setSlowMo(1000.0))
            val page = browser.newPage()
            page.navigate("http://localhost:$port")

            val authBtn = page.locator("#yt-auth")
            authBtn.click()

            assertThat(page.locator("#yt-auth")).not().isVisible()

            ytServer.verifyRequest(
                path = "/oauth2/v4/token",
                method = HttpMethod.POST,
            )
        }
    }
}
