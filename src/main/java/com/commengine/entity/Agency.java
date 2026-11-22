package com.commengine.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** A host agency. Created from the admin panel with one owner (a normal app user). */
@Entity
@Table(name = "agencies")
@Getter
@Setter
@NoArgsConstructor
public class Agency {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /** Short public ID shown in the app, e.g. MMA159648. */
    @Column(name = "code", nullable = false, unique = true, length = 16)
    private String code;

    @Column(nullable = false, length = 80)
    private String name;

    /** The "Welcome to join my agency" line shown on the agency card. */
    @Column(name = "welcome_message", length = 200)
    private String welcomeMessage;

    @Column(name = "owner_id", nullable = false)
    private String ownerId;

    /** A disabled agency keeps its data but the owner loses the dashboard and can't invite. */
    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
