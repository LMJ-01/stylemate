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
    private String topImage;
    private String bottomImage;
    private String outerImage;
    private String shoesImage;
    private String accessoryImage;

    private LocalDateTime createdAt = LocalDateTime.now();
}
