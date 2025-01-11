package de.thecoolcraft11.util;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.awt.Graphics2D;

public class ImageCompressor {
    public static void compressImage(File inputFile, File outputFile) throws IOException {
        BufferedImage image = ImageIO.read(inputFile);
        BufferedImage compressedImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);

        Graphics2D g2d = compressedImage.createGraphics();
        g2d.drawImage(image, 0, 0, null);
        g2d.dispose();

        ImageIO.write(compressedImage, "jpg", outputFile);
    }
}

