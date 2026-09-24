package com.mrms.claim.internal;

import com.mrms.document.DocumentCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** A supporting document that is not the bill of a specific item. */
@Entity
@Table(name = "claim_attachment")
class ClaimAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentCategory category;

    protected ClaimAttachment() {
    }

    ClaimAttachment(UUID documentId, DocumentCategory category) {
        this.documentId = documentId;
        this.category = category;
    }

    UUID getDocumentId() {
        return documentId;
    }

    DocumentCategory getCategory() {
        return category;
    }
}
