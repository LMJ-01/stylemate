package com.stylemate.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AvatarUpdateDto {

    private Integer heightCm;
    private Double heightScale;

    private Integer weightKg;
    private Double weightScale;

    private String bodyShape;

    private Double shoulderScale;
    private Double headScale;

    private String skinTone;
    private Double toneBrightness;

    private String gender;
    private String pose;
}

