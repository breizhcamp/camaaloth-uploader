package org.breizhcamp.video.uploader.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.breizhcamp.video.uploader.dto.Event;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

@Service
public class EventSrv {

	@Autowired
	private ObjectMapper mapper;

	/**
	 * List all events in schedule
	 * @return Event list
	 */
	public List<Event> list() throws IOException {
		InputStream schedule = EventSrv.class.getResourceAsStream("/schedule.json");
		return mapper.readValue(schedule, new TypeReference<List<Event>>(){});
	}

	/**
	 * Retrieve all events group by id
	 * @return Event list
	 */
	public Map<Integer, Event> listById() throws IOException {
		return list().stream()
				.collect(toMap(Event::getId, identity()));
	}

	/**
	 * Retrieve an event with his id
	 * @param id Id of the event to retrieve
	 * @return Event
	 * @throws IOException If we cannot read schedule json file
	 * @throws FileNotFoundException If the event is not found in schedule
	 */
	public Event getFromId(Integer id) throws IOException {
		return list().stream()
				.filter(e -> e.getId().equals(id))
				.findFirst()
				.orElseThrow(() -> new FileNotFoundException("Event [" + id + "] not found in schedule"));
	}
}
