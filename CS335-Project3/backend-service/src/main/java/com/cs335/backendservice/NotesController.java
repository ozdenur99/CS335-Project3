package com.cs335.backendservice;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@RestController
@RequestMapping("/api/{guid}/notes")
public class NotesController {

    private static final Logger logger = LoggerFactory.getLogger(NotesController.class);

    private Map<String, List<Note>> store = new HashMap<>();

    @PostMapping
    public ResponseEntity<Note> createNote(@PathVariable String guid, @RequestBody Note note) {

        logger.info("Creating note for guid: {}", guid);

        store.putIfAbsent(guid, new ArrayList<>());
        store.get(guid).add(note);

        return ResponseEntity.status(HttpStatus.CREATED).body(note);
    }

    @GetMapping
    public ResponseEntity<List<Note>> getNotes(@PathVariable String guid) {

        logger.info("Fetching notes for guid: {}", guid);

        List<Note> notes = store.getOrDefault(guid, new ArrayList<>());

        return ResponseEntity.ok(notes);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Note> updateNote(
            @PathVariable String guid,
            @PathVariable String id,
            @RequestBody Note updatedNote) {

        logger.info("Updating note {} for guid {}", id, guid);

        List<Note> notes = store.getOrDefault(guid, new ArrayList<>());

        for (Note note : notes) {
            if (note.getId().equals(id)) {
                note.setContent(updatedNote.getContent());
                return ResponseEntity.ok(note);
            }
        }

        logger.warn("Note {} not found for guid {}", id, guid);

        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteNote(
            @PathVariable String guid,
            @PathVariable String id) {

        logger.info("Deleting note {} for guid {}", id, guid);

        List<Note> notes = store.getOrDefault(guid, new ArrayList<>());

        boolean removed = notes.removeIf(note -> note.getId().equals(id));

        if (removed) {
            return ResponseEntity.ok("Deleted");
        }

        logger.warn("Attempted delete but note {} not found", id);

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Note not found");
    }
}