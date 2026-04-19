package com.cs335.backendservice;

import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/{guid}/notes")
public class NotesController {
    private Map<String, List<Note>> notesStorage = new HashMap<>();

    @GetMapping
    public List<Note> getNotes(@PathVariable String guid) {
        return notesStorage.getOrDefault(guid, new ArrayList<>());
    }

    @PostMapping
    public Note createNote(@PathVariable String guid, @RequestBody Note note) {
        notesStorage.computeIfAbsent(guid, k -> new ArrayList<>()).add(note);
        return note;
    }

    @PutMapping("/{id}")
    public Note updateNote(@PathVariable String guid, @PathVariable String id, @RequestBody Note updatedNote) {
        List<Note> notes = notesStorage.get(guid);
        if (notes != null) {
            for (Note note : notes) {
                if (note.getId().equals(id)) {
                    note.setContent(updatedNote.getContent());
                    return note;
                }
            }
        }
        return null;
    }

    @DeleteMapping("/{id}")
    public String deleteNote(@PathVariable String guid, @PathVariable String id) {
        List<Note> notes = notesStorage.get(guid);
        if (notes != null) {
            notes.removeIf(note -> note.getId().equals(id));
        }
        return "Deleted";
    }
}