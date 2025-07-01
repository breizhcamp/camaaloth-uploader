package org.breizhcamp.video.uploader

import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright

fun Playwright.launchAndNavigateToHomePage(port: String, browserType: BrowserType = firefox()): Page {
    return browserType.launch(
        BrowserType.LaunchOptions()
            .setHeadless(false)
            .setSlowMo(1000.0)
    )
        .newPage().also {
            it.navigate("http://localhost:$port")
        }
}