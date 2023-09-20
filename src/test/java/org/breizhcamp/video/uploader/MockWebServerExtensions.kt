package org.breizhcamp.video.uploader

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.util.concurrent.TimeUnit

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

fun MockWebServer.verifyRequest(
    path: String,
    method: HttpMethod,
){
    takeRequest(1, TimeUnit.SECONDS).apply {
        assertThat(path).isEqualTo(path)
        assertThat(method).isEqualTo(method.name)
    }
}
