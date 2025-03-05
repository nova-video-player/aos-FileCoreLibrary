package com.archos.filecorelibrary;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;

public class ImagePaddingUtil {
    // Function to calculate the padding based on ratio
    public static int getPaddingForRatio(int imageDimension, float ratio) {
        return (int) (imageDimension * ratio);  // Calculate padding based on ratio
    }

    // Function to check if the padding area (edges) is transparent enough
    public static boolean shouldApplyPadding(Bitmap bitmap, int padding, float transparencyThreshold) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        int transparentPixelCount = 0;
        int totalEdgePixels = 0;

        // Check top and bottom edges for transparency
        for (int x = 0; x < width; x++) {
            if (isTransparent(bitmap.getPixel(x, padding))) transparentPixelCount++; // Top edge
            if (isTransparent(bitmap.getPixel(x, height - padding - 1))) transparentPixelCount++; // Bottom edge
            totalEdgePixels += 2;
        }

        // Check left and right edges for transparency
        for (int y = 0; y < height; y++) {
            if (isTransparent(bitmap.getPixel(padding, y))) transparentPixelCount++; // Left edge
            if (isTransparent(bitmap.getPixel(width - padding - 1, y))) transparentPixelCount++; // Right edge
            totalEdgePixels += 2;
        }

        // Calculate the transparency ratio
        float transparencyRatio = (float) transparentPixelCount / totalEdgePixels;

        // Only apply padding if the transparency ratio is below the threshold
        return transparencyRatio < transparencyThreshold;
    }

    // Helper function to check if the pixel is transparent
    public static boolean isTransparent(int pixel) {
        return (pixel >>> 24) == 0; // Checks if the alpha channel is 0 (fully transparent)
    }

    // Add padding to the image
    public static Bitmap addPadding(Bitmap bitmap, int paddingSize) {
        int newWidth = bitmap.getWidth() + 2 * paddingSize;
        int newHeight = bitmap.getHeight() + 2 * paddingSize;

        Bitmap paddedBitmap = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(paddedBitmap);
        canvas.drawColor(Color.TRANSPARENT); // Fill with transparent background

        // Draw the original image in the center
        canvas.drawBitmap(bitmap, paddingSize, paddingSize, null);

        return paddedBitmap;
    }

}

