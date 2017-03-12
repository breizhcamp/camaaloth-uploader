package org.breizhcamp.video.uploader.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * JSON deserialization of an Event
 */
@Data
public class Event {

	private String id;
	private String name;
	private String description;
	private String speakers;
	private String language;
	@JsonProperty("event_start")
	private LocalDateTime eventStart;

	@JsonProperty("event_type")
	private String eventType;
	private String format;
	private String venue;

}
