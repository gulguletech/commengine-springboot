package com.commengine.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String phone;

    @Column(nullable = false)
    private Integer age;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    @Column(length = 300)
    private String bio;

    private String avatarUrl;

    @Column(nullable = false)
    private boolean isVerified = false;

    @Column(nullable = false)
    private boolean isOnline = false;

    @Column(nullable = false)
    private Instant lastSeen = Instant.now();

    /** Set from the admin panel. A banned user cannot log in, call the API, or connect to sockets. */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean banned = false;

    private Instant bannedAt;

    @Column(length = 200)
    private String banReason;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
