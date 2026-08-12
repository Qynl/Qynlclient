package com.qynl.client.agent;

import java.awt.AWTException;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

/** Captures a bounded desktop region as PNG for a vision model. */
public final class ScreenVisionAdapter {
    private final Robot robot;
    private final Rectangle region;

    public ScreenVisionAdapter() {
        try {
            this.robot = new Robot();
        } catch (AWTException e) {
            throw new IllegalStateException("Desktop screen capture is unavailable", e);
        }
        var bounds = Toolkit.getDefaultToolkit().getScreenSize();
        this.region = new Rectangle(0, 0, bounds.width, bounds.height);
    }

    public ScreenFrame capture() {
        BufferedImage image = robot.createScreenCapture(region);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return new ScreenFrame(image.getWidth(), image.getHeight(), out.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Could not encode screen frame", e);
        }
    }

    public record ScreenFrame(int width, int height, byte[] png) {}
}
