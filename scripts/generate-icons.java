import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

public final class generate_icons {
    private static final int[] ICON_SIZES = {16, 32, 128, 256, 512};

    public static void main(String[] args) throws Exception {
        Path root = args.length > 0 ? Path.of(args[0]) : Path.of("assets");
        Path iconset = root.resolve("MacIsland.iconset");
        Path tray = root.resolve("tray");
        Files.createDirectories(iconset);
        Files.createDirectories(tray);

        ImageIO.write(renderAppIcon(1024), "png", root.resolve("MacIsland.png").toFile());
        for (int size : ICON_SIZES) {
            ImageIO.write(renderAppIcon(size), "png", iconset.resolve("icon_" + size + "x" + size + ".png").toFile());
            ImageIO.write(renderAppIcon(size * 2), "png", iconset.resolve("icon_" + size + "x" + size + "@2x.png").toFile());
        }
        ImageIO.write(renderTrayIcon(18), "png", tray.resolve("MacIslandTemplate.png").toFile());
        ImageIO.write(renderTrayIcon(36), "png", tray.resolve("MacIslandTemplate@2x.png").toFile());
        System.out.println("Created icon assets in " + root.toAbsolutePath());
    }

    private static BufferedImage renderAppIcon(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = graphics(image);
        float padding = size * 0.055f;
        float tile = size - (padding * 2f);
        RoundRectangle2D.Float tileShape = new RoundRectangle2D.Float(
            padding, padding, tile, tile, size * 0.23f, size * 0.23f
        );
        graphics.setPaint(new GradientPaint(
            padding, padding, new Color(58, 92, 187),
            size - padding, size - padding, new Color(15, 23, 48)
        ));
        graphics.fill(tileShape);
        graphics.setPaint(new Color(255, 255, 255, 42));
        graphics.setStroke(new BasicStroke(Math.max(1f, size * 0.007f)));
        graphics.draw(tileShape);

        float islandX = size * 0.17f;
        float islandY = size * 0.17f;
        float islandW = size * 0.66f;
        float islandH = size * 0.14f;
        RoundRectangle2D.Float island = new RoundRectangle2D.Float(
            islandX, islandY, islandW, islandH, islandH * 0.48f, islandH * 0.48f
        );
        graphics.setColor(new Color(7, 12, 27));
        graphics.fill(island);
        graphics.setColor(new Color(255, 255, 255, 72));
        graphics.setStroke(new BasicStroke(Math.max(1f, size * 0.007f)));
        graphics.draw(island);

        float cardW = size * 0.20f;
        float cardH = size * 0.28f;
        float[] xs = {size * 0.23f, size * 0.40f, size * 0.57f};
        Color[] colors = {
            new Color(255, 211, 102),
            new Color(255, 123, 116),
            new Color(116, 224, 184)
        };
        float ropeTop = islandY + islandH;
        float cardY = size * 0.43f;
        for (int i = 0; i < xs.length; i++) {
            float center = xs[i] + cardW / 2f;
            graphics.setColor(new Color(255, 255, 255, 105));
            graphics.setStroke(new BasicStroke(Math.max(1f, size * 0.009f)));
            graphics.draw(new Line2D.Float(center, ropeTop, center, cardY));

            graphics.setColor(new Color(0, 0, 0, 55));
            graphics.fill(new RoundRectangle2D.Float(
                xs[i] + size * 0.012f, cardY + size * 0.018f, cardW, cardH,
                size * 0.045f, size * 0.045f
            ));
            graphics.setColor(colors[i]);
            graphics.fill(new RoundRectangle2D.Float(
                xs[i], cardY, cardW, cardH, size * 0.045f, size * 0.045f
            ));
            graphics.setColor(new Color(255, 255, 255, 145));
            graphics.setStroke(new BasicStroke(Math.max(1f, size * 0.006f)));
            graphics.draw(new RoundRectangle2D.Float(
                xs[i], cardY, cardW, cardH, size * 0.045f, size * 0.045f
            ));

            graphics.setColor(new Color(20, 29, 49, 150));
            graphics.fill(new RoundRectangle2D.Float(
                xs[i] + cardW * 0.18f,
                cardY + cardH * 0.22f,
                cardW * 0.64f,
                cardH * 0.45f,
                size * 0.018f,
                size * 0.018f
            ));
        }

        graphics.dispose();
        return image;
    }

    private static BufferedImage renderTrayIcon(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = graphics(image);
        graphics.setColor(Color.BLACK);
        float islandX = size * 0.12f;
        float islandY = size * 0.14f;
        float islandW = size * 0.76f;
        float islandH = size * 0.19f;
        graphics.fill(new RoundRectangle2D.Float(
            islandX, islandY, islandW, islandH, islandH * 0.5f, islandH * 0.5f
        ));
        float cardW = size * 0.20f;
        float cardH = size * 0.34f;
        float cardY = size * 0.49f;
        float[] xs = {size * 0.16f, size * 0.40f, size * 0.64f};
        for (float x : xs) {
            float center = x + cardW / 2f;
            graphics.setStroke(new BasicStroke(Math.max(1f, size * 0.055f)));
            graphics.draw(new Line2D.Float(center, islandY + islandH, center, cardY));
            graphics.fill(new RoundRectangle2D.Float(
                x, cardY, cardW, cardH, size * 0.045f, size * 0.045f
            ));
        }
        graphics.dispose();
        return image;
    }

    private static Graphics2D graphics(BufferedImage image) {
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        return graphics;
    }
}
