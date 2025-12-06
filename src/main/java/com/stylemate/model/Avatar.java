package com.stylemate.model;

import lombok.*;
import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "avatars",
    uniqueConstraints = {@UniqueConstraint(columnNames = {"user_id"})}
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Avatar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 한 명의 유저당 아바타 1개 */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /** 기본 속성들 (NOT NULL + 기본값) */
    @Column(nullable = false, columnDefinition = "INT DEFAULT 175")
    private Integer heightCm;       // 120~220

    @Column(nullable = false, columnDefinition = "INT DEFAULT 70")
    private Integer weightKg;       // 40~160

    @Column(nullable = false, length = 20, columnDefinition = "VARCHAR(20) DEFAULT 'regular'")
    private String bodyShape;       // slim / regular / plus

    @Column(nullable = false, columnDefinition = "DOUBLE DEFAULT 1.0")
    private Double shoulderScale;   // 0.9~1.3

    @Column(nullable = false, columnDefinition = "DOUBLE DEFAULT 1.0")
    private Double headScale;       // 0.85~1.2

    @Column(nullable = false, length = 16, columnDefinition = "VARCHAR(16) DEFAULT '#e6cbb3'")
    private String skinTone;        // HEX 색상

    @Column(nullable = false, columnDefinition = "DOUBLE DEFAULT 1.0")
    private Double toneBrightness;  // 0.85~1.15

    @Column(nullable = false, length = 10, columnDefinition = "VARCHAR(10) DEFAULT 'unisex'")
    private String gender;          // unisex / male / female

    @Column(nullable = false, length = 10, columnDefinition = "VARCHAR(10) DEFAULT 'neutral'")
    private String pose;            // neutral / slight

    /** 생성/수정 시각 */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /** 저장 전 자동 기본값 보정 */
    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;

        if (heightCm == null) heightCm = 175;
        if (weightKg == null) weightKg = 70;
        if (bodyShape == null || bodyShape.isBlank()) bodyShape = "regular";
        if (shoulderScale == null) shoulderScale = 1.0;
        if (headScale == null) headScale = 1.0;
        if (skinTone == null || skinTone.isBlank()) skinTone = "#e6cbb3";
        if (toneBrightness == null) toneBrightness = 1.0;
        if (gender == null || gender.isBlank()) gender = "unisex";
        if (pose == null || pose.isBlank()) pose = "neutral";
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
        // 보정 (업데이트 시 누락 방지)
        if (bodyShape == null || bodyShape.isBlank()) bodyShape = "regular";
        if (gender == null || gender.isBlank()) gender = "unisex";
        if (pose == null || pose.isBlank()) pose = "neutral";
    }
}
