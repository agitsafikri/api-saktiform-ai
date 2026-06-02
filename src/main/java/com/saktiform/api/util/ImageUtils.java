package com.saktiform.api.util;



import com.saktiform.api.model.product.ImageType;
import org.imgscalr.Scalr;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.*;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Iterator;

public class ImageUtils {

    private static final float DEFAULT_QUALITY = 0.8f;

    private ImageUtils() {}

    public static byte[] convertToWebP(MultipartFile file, Integer maxWidth) throws IOException {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File kosong");
        }

        if ("image/webp".equalsIgnoreCase(file.getContentType())) {
            return file.getBytes();
        }

        BufferedImage originalImage = ImageIO.read(file.getInputStream());

        if (originalImage == null) {
            throw new IllegalArgumentException("File bukan gambar valid");
        }

        BufferedImage processedImage = originalImage;

        if (maxWidth != null && originalImage.getWidth() > maxWidth) {
            processedImage = Scalr.resize(originalImage, maxWidth);
        }

        return writeWebP(processedImage, DEFAULT_QUALITY);
    }

    private static byte[] writeWebP(BufferedImage image, float quality) throws IOException {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        Iterator<ImageWriter> writers =
                ImageIO.getImageWritersByMIMEType("image/webp");

        if (!writers.hasNext()) {
            throw new IllegalStateException("WebP writer tidak ditemukan");
        }

        ImageWriter writer = writers.next();
        ImageOutputStream ios = ImageIO.createImageOutputStream(baos);

        writer.setOutput(ios);

        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        String[] types = param.getCompressionTypes();
        if (types != null && types.length > 0) {
            param.setCompressionType(types[0]); // biasanya "Lossy"
        }
        param.setCompressionQuality(quality);

        writer.write(null, new IIOImage(image, null, null), param);

        ios.close();
        writer.dispose();

        return baos.toByteArray();
    }

    public static boolean isImage(MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) return false;

            BufferedImage image = ImageIO.read(file.getInputStream());
            return image != null;

        } catch (IOException e) {
            return false;
        }
    }
    public static boolean isSvg(MultipartFile file) {
        if (file == null || file.isEmpty()) return false;

        String contentType = file.getContentType();
        String filename = file.getOriginalFilename();

        return "image/svg+xml".equalsIgnoreCase(contentType) ||
                (filename != null && filename.toLowerCase().endsWith(".svg"));
    }

    public static boolean isWebP(MultipartFile file) {
        if (file == null || file.isEmpty()) return false;

        String contentType = file.getContentType();
        return "image/webp".equalsIgnoreCase(contentType);
    }

    public static boolean isRasterImage(MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) return false;

            BufferedImage image = ImageIO.read(file.getInputStream());
            return image != null;

        } catch (IOException e) {
            return false;
        }
    }

    public static ImageType detectImageType(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            return ImageType.INVALID;
        }

        if (isSvg(file)) {
            return ImageType.SVG;
        }

        if (isWebP(file)) {
            return ImageType.WEBP;
        }

        if (isRasterImage(file)) {
            return ImageType.RASTER;
        }

        return ImageType.INVALID;
    }
}
