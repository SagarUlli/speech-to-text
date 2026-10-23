package com.sagarulli.transcribe.web;

import com.sagarulli.transcribe.domain.Transcription;
import com.sagarulli.transcribe.domain.TranscriptionRepository;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;

/** CRUD API for stored transcriptions. */
@RestController
@RequestMapping("/api/transcriptions")
public class TranscriptionController {

    private final TranscriptionRepository repo;

    public TranscriptionController(TranscriptionRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public PageResponse<TranscriptionDto> list(@RequestParam(required = false) String q,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        var result = (q == null || q.isBlank()) ? repo.findAll(pageable) : repo.search(q.trim(), pageable);
        return PageResponse.of(result.map(TranscriptionDto::from));
    }

    @GetMapping("/{id}")
    public TranscriptionDto get(@PathVariable Long id) {
        return TranscriptionDto.from(find(id));
    }

    @PostMapping
    public ResponseEntity<TranscriptionDto> create(@Valid @RequestBody TranscriptionRequest req) {
        Transcription t = new Transcription();
        apply(t, req);
        t = repo.save(t);
        return ResponseEntity.created(URI.create("/api/transcriptions/" + t.getId())).body(TranscriptionDto.from(t));
    }

    @PutMapping("/{id}")
    public TranscriptionDto update(@PathVariable Long id, @Valid @RequestBody TranscriptionRequest req) {
        Transcription t = find(id);
        apply(t, req);
        return TranscriptionDto.from(repo.save(t));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        repo.delete(find(id));
    }

    private Transcription find(Long id) {
        return repo.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Transcription " + id + " not found"));
    }

    private void apply(Transcription t, TranscriptionRequest r) {
        t.setTitle(r.title().trim());
        t.setContent(r.content());
        t.setLanguage(r.language());
        t.setEngine(r.engine());
        t.setDurationSeconds(r.durationSeconds());
    }
}
