package com.stylemate.repository;

import com.stylemate.model.Clothes;
import com.stylemate.model.Clothes.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClothesRepository extends JpaRepository<Clothes, Long> {

    List<Clothes> findByCategory(Category category);

    List<Clothes> findByColorIgnoreCase(String color);

    List<Clothes> findByPriceLessThanEqual(int price);

    List<Clothes> findByBrandIgnoreCase(String brand);

    // ⭐ 추가: 세부 카테고리(반팔/긴팔/후드티 등)
    List<Clothes> findBySubCategoryIgnoreCase(String subCategory);

    // ⭐ 필요하면 조합도 가능
    List<Clothes> findByCategoryAndSubCategoryIgnoreCase(Category category, String subCategory);
}
