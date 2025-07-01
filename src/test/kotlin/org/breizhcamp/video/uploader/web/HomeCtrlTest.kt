package org.breizhcamp.video.uploader.web

import org.htmlunit.WebClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.context.web.WebAppConfiguration
import org.springframework.test.web.servlet.htmlunit.MockMvcWebClientBuilder
import org.springframework.web.context.WebApplicationContext

@ExtendWith(SpringExtension::class)
@WebAppConfiguration
class HomeCtrlTest {

    lateinit var webClient: WebClient

    @BeforeEach
    fun setup(context: WebApplicationContext) {
        webClient = MockMvcWebClientBuilder
            .webAppContextSetup(context)
            .build()
    }

    @Test
    fun `should display home page`(){
        /* ****  GIVEN  **** */


        /* ****  WHEN  **** */

        /* ****  THEN  **** */

    }

}