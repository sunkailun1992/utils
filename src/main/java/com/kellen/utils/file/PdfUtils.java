package com.kellen.utils.file;

import cn.hutool.core.codec.Base64;
import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.forms.fields.PdfFormField;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.annot.PdfWidgetAnnotation;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PDF 工具类。
 *
 * <p>基于 iText 7（kernel/forms/layout）填充 AcroForm 模板、在表单域位置叠加图片并扁平化，
 * 基于 PDFBox 3（{@link Loader}）把 PDF 渲染为 PNG。</p>
 *
 * @author 孙凯伦
 */
public final class PdfUtils {

    /**
     * CJK 字体名，依赖 iText font-asian 提供 CMap，按需可替换为业务字体。
     */
    private static final String CJK_FONT = "STSong-Light";

    /**
     * CJK 字体编码，配合 font-asian 输出中文且不内嵌字体。
     */
    private static final String CJK_ENCODING = "UniGB-UCS2-H";

    private PdfUtils() {
    }

    /**
     * 按 AcroForm 模板生成 PDF，图片域接收 Base64 字符串。
     *
     * @param template 模板 PDF 的二进制流
     * @param address  输出文件地址
     * @param data     文本域数据，key 对应模板数据域名称
     * @param picture  图片域数据，value 为 Base64 编码图片
     * @throws Exception 模板读取、字体加载或文件写入异常
     */
    public static void generate(byte[] template, String address, Map<String, String> data, Map<String, String> picture) throws Exception {
        Map<String, byte[]> decoded = new HashMap<>();
        if (picture != null) {
            for (Map.Entry<String, String> entry : picture.entrySet()) {
                if (entry.getValue() != null) {
                    decoded.put(entry.getKey(), Base64.decode(entry.getValue()));
                }
            }
        }
        fillForm(template, address, data, decoded);
    }

    /**
     * 按 AcroForm 模板生成 PDF，图片域接收原始字节。
     *
     * @param template 模板 PDF 的二进制流
     * @param address  输出文件地址
     * @param data     文本域数据，key 对应模板数据域名称
     * @param picture  图片域数据，value 为图片字节
     * @throws Exception 模板读取、字体加载或文件写入异常
     */
    public static void generateByte(byte[] template, String address, Map<String, String> data, Map<String, byte[]> picture) throws Exception {
        fillForm(template, address, data, picture);
    }

    /**
     * 填充模板文本域与图片域并扁平化输出，是两个公开生成方法的统一实现。
     *
     * @param template 模板 PDF 的二进制流
     * @param address  输出文件地址
     * @param data     文本域数据
     * @param pictures 图片域数据
     * @throws IOException 模板读取、字体加载或文件写入异常
     */
    private static void fillForm(byte[] template, String address, Map<String, String> data, Map<String, byte[]> pictures) throws IOException {
        try (PdfDocument pdfDoc = new PdfDocument(new PdfReader(new ByteArrayInputStream(template)), new PdfWriter(address))) {
            PdfAcroForm form = PdfAcroForm.getAcroForm(pdfDoc, true);
            PdfFont font = PdfFontFactory.createFont(CJK_FONT, CJK_ENCODING);
            if (data != null) {
                for (Map.Entry<String, String> entry : data.entrySet()) {
                    PdfFormField field = form.getField(entry.getKey());
                    if (field != null) {
                        field.setFont(font);
                        field.setValue(entry.getValue());
                    }
                }
            }
            if (pictures != null) {
                for (Map.Entry<String, byte[]> entry : pictures.entrySet()) {
                    stampImage(pdfDoc, form, entry.getKey(), entry.getValue());
                }
            }
            form.flattenFields();
        }
    }

    /**
     * 在指定表单域所在页面的矩形区域内叠加图片。
     *
     * @param pdfDoc     PDF 文档
     * @param form       AcroForm
     * @param fieldName  图片域名称
     * @param imageBytes 图片字节
     */
    private static void stampImage(PdfDocument pdfDoc, PdfAcroForm form, String fieldName, byte[] imageBytes) {
        PdfFormField field = form.getField(fieldName);
        if (field == null || imageBytes == null) {
            return;
        }
        List<PdfWidgetAnnotation> widgets = field.getWidgets();
        if (widgets.isEmpty()) {
            return;
        }
        PdfWidgetAnnotation widget = widgets.get(0);
        Rectangle rect = widget.getRectangle().toRectangle();
        int pageNo = pdfDoc.getPageNumber(widget.getPage());
        ImageData imageData = ImageDataFactory.create(imageBytes);
        PdfCanvas canvas = new PdfCanvas(pdfDoc.getPage(pageNo));
        canvas.addImageFittedIntoRectangle(imageData, rect, false);
    }

    /**
     * 把 PDF 二进制流逐页渲染为 PNG。
     *
     * @param png  图片输出目录前缀
     * @param pdf  PDF 二进制流
     * @param name 文件名前缀
     */
    public static void png(String png, byte[] pdf, String name) {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            renderToPng(doc, png, name, 144);
        } catch (IOException e) {
            throw new IllegalStateException("PDF 转 PNG 失败：" + name, e);
        }
    }

    /**
     * 把 PDF 文件逐页渲染为 PNG。
     *
     * @param png  图片输出目录前缀
     * @param pdf  PDF 文件地址
     * @param name 文件名前缀
     */
    public static void png(String png, String pdf, String name) {
        try (PDDocument doc = Loader.loadPDF(new File(pdf))) {
            renderToPng(doc, png, name, 200);
        } catch (IOException e) {
            throw new IllegalStateException("PDF 转 PNG 失败：" + name, e);
        }
    }

    /**
     * 按指定 DPI 把文档逐页写出 PNG。
     *
     * @param doc  PDF 文档
     * @param png  图片输出目录前缀
     * @param name 文件名前缀
     * @param dpi  渲染 DPI
     * @throws IOException 渲染或写文件异常
     */
    private static void renderToPng(PDDocument doc, String png, String name, int dpi) throws IOException {
        PDFRenderer renderer = new PDFRenderer(doc);
        int pageCount = doc.getNumberOfPages();
        for (int i = 0; i < pageCount; i++) {
            BufferedImage image = renderer.renderImageWithDPI(i, dpi);
            ImageIO.write(image, "png", new File(png + name + "_" + (i + 1) + ".png"));
            image.flush();
        }
    }
}
