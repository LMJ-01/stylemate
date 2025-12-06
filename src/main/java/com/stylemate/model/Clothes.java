package com.stylemate.model;

import javax.persistence.*;   // Boot 2.x는 javax
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "clothes")
@Getter
@Setter
public class Clothes {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "brand", length = 50)
    private String brand;

    /** 
     * 상위 카테고리 (상의/하의/아우터/신발/악세서리)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private Category category;

    /**
     * 세부 카테고리 (반팔, 긴팔, 후드티, 셔츠, 바람막이, 패딩 등)
     */
    @Column(name = "sub_category", length = 50)
    private String subCategory;

    @Column(name = "color", length = 30)
    private String color;

    @Column(name = "price", nullable = false)
    private Integer price;

    @Column(name = "image_url", length = 255)
    private String imageUrl;

    @Column(name = "gender", length = 10)
    private String gender;

    @Column(name = "created_at", updatable = false, insertable = false)
    private java.sql.Timestamp createdAt;

    public enum Category {
        top, bottom, outer, shoes, accessory
    }
}
