package com.poshanforlife.api.service;

import com.poshanforlife.api.exception.OcrExtractionException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

/**
 * Extracts a PDF's text layer with Apache PDFBox — the same "read the text
 * layer first" approach the original app's pdfjs-based extractor used. If
 * the text layer is too sparse to be useful (a scanned/photographed InBody
 * report with no embedded text), falls back to rendering the first page as a
 * PNG so it can be sent to Claude as an image content block instead.
 */
@Service
public class PdfTextExtractionService {

    /** Below this many characters, treat the PDF as scanned/image-only and fall back to rendering. */
    private static final int MIN_USEFUL_TEXT_LENGTH = 40;

    public Extraction extract(MultipartFile file) {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            String text = new PDFTextStripper().getText(document);
            if (text != null && text.strip().length() >= MIN_USEFUL_TEXT_LENGTH) {
                return new Extraction("text", text, null);
            }

            BufferedImage image = new PDFRenderer(document).renderImageWithDPI(0, 150, ImageType.RGB);
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            ImageIO.write(image, "png", buffer);
            String base64Png = Base64.getEncoder().encodeToString(buffer.toByteArray());
            return new Extraction("image", text, base64Png);
        } catch (IOException e) {
            throw new OcrExtractionException("Could not read this PDF — it may be corrupted", e);
        }
    }

    /**
     * @param method       "text" or "image" — echoed back as extractionMethod
     * @param text         the extracted text layer (may be sparse/null when method is "image")
     * @param base64PngPage first-page render, only present when method is "image"
     */
    public record Extraction(String method, String text, String base64PngPage) {
    }
}
