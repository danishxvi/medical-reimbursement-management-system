package com.mrms.document.internal;

import com.mrms.document.DocumentCategory;
import com.mrms.document.DocumentMeta;
import com.mrms.document.DocumentStore;
import com.mrms.shared.security.CurrentUser;
import com.mrms.shared.web.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * Upload and owner only download. Reviewers (HoS, dispensary, PAO) read
 * documents through the claim and e-NAC endpoints, which check that the
 * document belongs to a record in their queue.
 */
@RestController
@RequestMapping("/api/documents")
class DocumentController {

    private final DocumentStore store;

    DocumentController(DocumentStore store) {
        this.store = store;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('EMPLOYEE')")
    @ResponseStatus(HttpStatus.CREATED)
    DocumentMeta upload(@RequestParam("file") MultipartFile file,
                        @RequestParam("category") DocumentCategory category) throws IOException {
        return store.store(CurrentUser.id(), category, file.getOriginalFilename(), file.getBytes());
    }

    @GetMapping("/{id}")
    DocumentMeta meta(@PathVariable UUID id) {
        return ownMeta(id);
    }

    @GetMapping("/{id}/content")
    ResponseEntity<byte[]> content(@PathVariable UUID id) {
        ownMeta(id);
        return store.read(id).toResponse();
    }

    private DocumentMeta ownMeta(UUID id) {
        return store.meta(id)
                .filter(m -> m.ownerUserId().equals(CurrentUser.id()))
                .orElseThrow(() -> new NotFoundException("Document"));
    }
}
