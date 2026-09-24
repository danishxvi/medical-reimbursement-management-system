package com.mrms.organisation.internal;

import com.mrms.organisation.OrganisationViews.DependentView;
import com.mrms.shared.domain.Relation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Family member covered under the employee's DGEHS card. Dependents are
 * deactivated, never deleted, because old claims refer to them.
 */
@Entity
@Table(name = "dependent")
class Dependent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Relation relation;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Dependent() {
    }

    Dependent(String fullName, Relation relation, LocalDate dateOfBirth, Instant now) {
        this.fullName = fullName;
        this.relation = relation;
        this.dateOfBirth = dateOfBirth;
        this.active = true;
        this.createdAt = now;
    }

    void deactivate() {
        this.active = false;
    }

    Long getId() {
        return id;
    }

    boolean isActive() {
        return active;
    }

    DependentView toView() {
        return new DependentView(id, fullName, relation, dateOfBirth, active);
    }
}
