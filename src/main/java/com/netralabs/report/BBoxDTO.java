package com.netralabs.report;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BBoxDTO {

  public float top, left, height, width;
  public BBoxDTO() {}
  public BBoxDTO(float top, float left, float height, float width) {
    this.top = top; this.left = left; this.height = height; this.width = width;
  }

}
