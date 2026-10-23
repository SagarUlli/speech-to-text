package com.sagarulli.transcribe.web;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sagarulli.transcribe.engine.EngineRegistry;

/** Tells the UI which speech engines are configured on this deployment. */
@RestController
@RequestMapping("/api/engines")
public class EngineController {
	private final EngineRegistry registry;

	public EngineController(EngineRegistry registry) {
		this.registry = registry;
	}

	@GetMapping
	public Map<String, Object> engines() {
		return Map.of("default", registry.defaultId(), "engines", registry.all().stream()
				.map(e -> Map.of("id", e.id(), "label", e.label(), "available", e.isAvailable())).toList());
	}
}
