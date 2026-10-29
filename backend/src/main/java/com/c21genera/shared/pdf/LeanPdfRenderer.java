package com.c21genera.shared.pdf;

import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.filter.DecodeOptions;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.color.PDColorSpace;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceGray;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.graphics.color.PDICCBased;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.rendering.PageDrawer;
import org.apache.pdfbox.rendering.PageDrawerParameters;

/**
 * Renderizador que decodifica las imágenes grandes sin comprimir en JPEG (Flate, como las capturas
 * de pantalla del iPhone guardadas en PDF) renglón por renglón directo a su búfer final. PDFBox las
 * descomprimía a un arreglo que crecía duplicándose, lo copiaba y luego convertía el perfil de color:
 * una captura de 1264×4326 px (16 MB de datos) pedía ~115 MB de heap y en producción, con el heap ya
 * ocupado, daba OutOfMemoryError (29/10). Aquí ocupa solo la imagen ya decodificada (~22 MB).
 *
 * <p>El perfil de color ICC de esas imágenes se ignora (se toman como RGB o gris tal cual): en un
 * documento la diferencia de tono no se nota y la conversión era lo más caro. Todo lo demás (JPEG,
 * máscaras, imágenes chicas) lo sigue dibujando PDFBox.
 */
final class LeanPdfRenderer extends PDFRenderer {

  /** Debajo de esto PDFBox las decodifica sin problema y con todo su detalle de color. */
  static final long MIN_LEAN_PIXELS = 2_000_000L;

  private static final Set<COSName> STREAMED_FILTERS = Set.of(COSName.FLATE_DECODE, COSName.LZW_DECODE);

  LeanPdfRenderer(PDDocument document) {
    super(document);
  }

  @Override
  protected PageDrawer createPageDrawer(PageDrawerParameters parameters) throws IOException {
    return new PageDrawer(parameters) {
      @Override
      public void drawImage(PDImage pdImage) throws IOException {
        int components = leanComponents(pdImage);
        super.drawImage(components > 0 ? new LeanImage((PDImageXObject) pdImage, components) : pdImage);
      }
    };
  }

  /** 3 (RGB) o 1 (gris) si la imagen va por la ruta ligera; 0 si la dibuja PDFBox. */
  static int leanComponents(PDImage image) throws IOException {
    if (!(image instanceof PDImageXObject x)
        || x.isStencil()
        || x.getBitsPerComponent() != 8
        || x.getDecode() != null
        || (long) x.getWidth() * x.getHeight() < MIN_LEAN_PIXELS
        || x.getOptionalContent() != null
        || x.getCOSObject().getDictionaryObject(COSName.SMASK) != null
        || x.getCOSObject().getDictionaryObject(COSName.MASK) != null) {
      return 0;
    }
    List<COSName> filters = x.getStream().getFilters();
    if (!STREAMED_FILTERS.containsAll(filters)) {
      return 0;
    }
    PDColorSpace colorSpace = x.getColorSpace();
    if (colorSpace instanceof PDDeviceRGB || colorSpace instanceof PDDeviceGray || colorSpace instanceof PDICCBased) {
      int n = colorSpace.getNumberOfComponents();
      return n == 3 || n == 1 ? n : 0;
    }
    return 0;
  }

  /** Lee la imagen tomando uno de cada {@code subsampling} renglones y columnas. */
  static BufferedImage decode(PDImageXObject image, int components, int subsampling) throws IOException {
    int s = Math.max(1, subsampling);
    int w = image.getWidth();
    int h = image.getHeight();
    int outW = (w + s - 1) / s;
    int outH = (h + s - 1) / s;
    // Directo en el formato del renderizado (RGB en enteros): si no, Java2D hacía otra copia convertida al dibujarla.
    BufferedImage out = new BufferedImage(outW, outH, BufferedImage.TYPE_INT_RGB);
    int[] dst = ((DataBufferInt) out.getRaster().getDataBuffer()).getData();
    byte[] row = new byte[w * components];
    try (InputStream in = image.createInputStream()) {
      for (int y = 0, outY = 0; y < h && outY < outH; y++) {
        if (in.readNBytes(row, 0, row.length) < row.length) {
          break; // Flujo truncado: lo que falta queda en negro, como en PDFBox.
        }
        if (y % s != 0) {
          continue;
        }
        int base = outY * outW;
        for (int outX = 0; outX < outW; outX++) {
          int src = outX * s * components;
          int r = row[src] & 0xFF;
          dst[base + outX] = components == 3 ? (r << 16) | ((row[src + 1] & 0xFF) << 8) | (row[src + 2] & 0xFF) : (r << 16) | (r << 8) | r;
        }
        outY++;
      }
    }
    return out;
  }

  /** La misma imagen, pero decodificada con {@link #decode}. */
  private static final class LeanImage implements PDImage {
    private final PDImageXObject image;
    private final int components;

    LeanImage(PDImageXObject image, int components) {
      this.image = image;
      this.components = components;
    }

    @Override
    public BufferedImage getImage() throws IOException {
      return decode(image, components, 1);
    }

    @Override
    public BufferedImage getImage(Rectangle region, int subsampling) throws IOException {
      BufferedImage full = decode(image, components, subsampling);
      if (region == null) {
        return full;
      }
      Rectangle r = region.intersection(new Rectangle(0, 0, full.getWidth() * subsampling, full.getHeight() * subsampling));
      int s = Math.max(1, subsampling);
      return full.getSubimage(r.x / s, r.y / s, Math.max(1, r.width / s), Math.max(1, r.height / s));
    }

    @Override
    public WritableRaster getRawRaster() throws IOException {
      return getImage().getRaster();
    }

    @Override
    public BufferedImage getRawImage() throws IOException {
      return getImage();
    }

    @Override
    public BufferedImage getStencilImage(Paint paint) throws IOException {
      return image.getStencilImage(paint);
    }

    @Override
    public InputStream createInputStream() throws IOException {
      return image.createInputStream();
    }

    @Override
    public InputStream createInputStream(List<String> stopFilters) throws IOException {
      return image.createInputStream(stopFilters);
    }

    @Override
    public InputStream createInputStream(DecodeOptions options) throws IOException {
      return image.createInputStream(options);
    }

    @Override
    public boolean isEmpty() {
      return image.isEmpty();
    }

    @Override
    public boolean isStencil() {
      return false;
    }

    @Override
    public void setStencil(boolean isStencil) {
      image.setStencil(isStencil);
    }

    @Override
    public int getBitsPerComponent() {
      return image.getBitsPerComponent();
    }

    @Override
    public void setBitsPerComponent(int bitsPerComponent) {
      image.setBitsPerComponent(bitsPerComponent);
    }

    @Override
    public PDColorSpace getColorSpace() {
      return components == 3 ? PDDeviceRGB.INSTANCE : PDDeviceGray.INSTANCE;
    }

    @Override
    public void setColorSpace(PDColorSpace colorSpace) {
      image.setColorSpace(colorSpace);
    }

    @Override
    public int getHeight() {
      return image.getHeight();
    }

    @Override
    public void setHeight(int height) {
      image.setHeight(height);
    }

    @Override
    public int getWidth() {
      return image.getWidth();
    }

    @Override
    public void setWidth(int width) {
      image.setWidth(width);
    }

    @Override
    public void setDecode(COSArray decode) {
      image.setDecode(decode);
    }

    @Override
    public COSArray getDecode() {
      return null;
    }

    @Override
    public boolean getInterpolate() {
      return image.getInterpolate();
    }

    @Override
    public void setInterpolate(boolean value) {
      image.setInterpolate(value);
    }

    @Override
    public String getSuffix() {
      return image.getSuffix();
    }

    @Override
    public COSDictionary getCOSObject() {
      return image.getCOSObject();
    }
  }
}
