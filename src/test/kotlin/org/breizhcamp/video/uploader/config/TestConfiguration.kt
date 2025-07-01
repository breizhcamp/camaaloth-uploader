package org.breizhcamp.video.uploader.config

import com.google.api.client.googleapis.testing.auth.oauth2.MockGoogleCredential
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.test.context.ActiveProfiles

@Configuration
@ActiveProfiles("e2e")
class TestConfiguration {

    @Bean
    @Primary
    fun credentials(): MockGoogleCredential = MockGoogleCredential.Builder().build()
}