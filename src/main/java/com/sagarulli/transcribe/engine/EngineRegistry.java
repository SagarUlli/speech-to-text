package com.sagarulli.transcribe.engine;

import com.sagarulli.transcribe.config.TranscribeProperties;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class EngineRegistry {
    private final List<SttEngine> engines;
    private final TranscribeProperties props;

    public EngineRegistry(List<SttEngine> engines, TranscribeProperties props) {
        this.engines = engines;
        this.props = props;
    }

    public List<SttEngine> all() { return engines; }

    public String defaultId() {
        return engines.stream().filter(e -> e.id().equals(props.defaultEngine()) && e.isAvailable())
                .findFirst()
                .or(() -> engines.stream().filter(SttEngine::isAvailable).findFirst())
                .map(SttEngine::id).orElse(props.defaultEngine());
    }

    public Optional<SttEngine> find(String id) {
        return engines.stream().filter(e -> e.id().equals(id)).findFirst();
    }
}
