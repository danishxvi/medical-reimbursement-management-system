package com.mrms.organisation.internal;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

import java.time.Instant;

/** Common columns of the office tables (PAO, school, dispensary). */
@MappedSuperclass
abstract class Office {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    protected Long id;

    @Column(nullable = false, unique = true)
    protected String code;

    @Column(nullable = false)
    protected String name;

    protected String address;

    @Column(name = "created_at", nullable = false)
    protected Instant createdAt;

    Long getId() {
        return id;
    }

    String getCode() {
        return code;
    }

    String getName() {
        return name;
    }

    String getAddress() {
        return address;
    }
}
