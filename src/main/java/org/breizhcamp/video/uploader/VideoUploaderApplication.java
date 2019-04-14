package org.breizhcamp.video.uploader;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;

@SpringBootApplication
public class VideoUploaderApplication {

	public static void main(String[] args) throws IOException {
		SpringApplication.run(VideoUploaderApplication.class, args);
	}
}
