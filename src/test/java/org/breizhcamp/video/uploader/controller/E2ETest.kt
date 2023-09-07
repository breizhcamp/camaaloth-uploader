package org.breizhcamp.video.uploader.controller

import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e")
class E2ETest {

    @LocalServerPort
    lateinit var port: String

    @Test
    fun `should upload video`(){
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