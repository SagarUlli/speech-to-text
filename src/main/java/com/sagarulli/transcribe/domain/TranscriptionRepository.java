package com.sagarulli.transcribe.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TranscriptionRepository extends JpaRepository<Transcription, Long> {

    @Query("""
            select t from Transcription t
            where lower(t.title) like lower(concat('%', :q, '%'))
               or lower(t.content) like lower(concat('%', :q, '%'))
            """)
    Page<Transcription> search(@Param("q") String q, Pageable pageable);
}
