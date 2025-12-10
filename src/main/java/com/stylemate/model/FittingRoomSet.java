package com.stylemate.model;

import lombok.Getter;
import lombok.Setter;
import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "fittingroom_sets")
@Getter @Setter
public class FittingRoomSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String name;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String topImage;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String bottomImage;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String outerImage;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String shoesImage;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String accessoryImage;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String faceImage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
