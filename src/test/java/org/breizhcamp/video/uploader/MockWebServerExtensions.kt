package org.breizhcamp.video.uploader

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.springframework.http.HttpStatus

fun MockWebServer.enqueueObject(obj: Any, status: HttpStatus = HttpStatus.OK) {
    enqueue(
        MockResponse().apply {
            setResponseCode(status.value())
            addHeader("Content-Type", "application/json")
            setBody(
                jacksonObjectMapper().writeValueAsString(obj)
            )
        }
    )
}