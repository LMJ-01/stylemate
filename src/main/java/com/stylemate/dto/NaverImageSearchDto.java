package com.stylemate.dto;
import lombok.Getter; import lombok.Setter;
import java.util.List;

@Getter @Setter
public class NaverImageSearchDto {
  private String lastBuildDate;
  private int total;
  private int start;
  private int display;
  private List<Item> items;

  @Getter @Setter
  public static class Item {
    private String title;
    private String link;
    private String thumbnail;
    private String sizeheight;
    private String sizewidth;
  }
}
