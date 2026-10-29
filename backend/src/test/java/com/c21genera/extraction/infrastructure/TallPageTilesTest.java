package com.c21genera.extraction.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 29/10: una captura de pantalla larga se reducía a ~600 px de ancho y la IA no la leía. */
class TallPageTilesTest {

  @Test
  void normalPagesAreNotTiled() {
    assertThat(OpenAiStructuredExtractionProvider.isTall(new BufferedImage(1700, 2200, BufferedImage.TYPE_INT_RGB))).isFalse();
  }

  @Test
  void longScreenshotIsSplitIntoOverlappingTilesThatCoverItAll() {
    BufferedImage page = new BufferedImage(1867, 6386, BufferedImage.TYPE_INT_RGB);

    List<BufferedImage> tiles = OpenAiStructuredExtractionProvider.tallTiles(page);

    assertThat(OpenAiStructuredExtractionProvider.isTall(page)).isTrue();
    assertThat(tiles).hasSize(3);
    assertThat(tiles).allSatisfy(t -> assertThat(t.getWidth()).isEqualTo(1867));
    assertThat(tiles.stream().mapToInt(BufferedImage::getHeight).sum()).isGreaterThan(6386);
    assertThat(OpenAiStructuredExtractionProvider.fitForModel(tiles.getFirst()).getWidth()).isGreaterThan(1400);
  }

  @Test
  void veryLongPagesNeverExceedTheTileLimit() {
    for (int height : new int[] {9000, 20000, 50000, 50001}) {
      List<BufferedImage> tiles = OpenAiStructuredExtractionProvider.tallTiles(new BufferedImage(600, height, BufferedImage.TYPE_INT_RGB));
      assertThat(tiles).hasSizeLessThanOrEqualTo(OpenAiStructuredExtractionProvider.MAX_TILES);
    }
  }
}
