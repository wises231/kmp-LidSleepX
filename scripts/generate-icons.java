import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

public final class generate_icons {
    private static final int[] ICON_SIZES = {16, 32, 128, 256, 512};

    public static void main(String[] args) throws Exception {
        Path root = args.length > 0 ? Path.of(args[0]) : Path.of("assets");
        Path iconset = root.resolve("LidSleepX.iconset");
        Path tray = root.resolve("tray");
        Files.createDirectories(iconset);
        Files.createDirectories(tray);

        ImageIO.write(renderAppIcon(1024), "png", root.resolve("LidSleepX.png").toFile());
        for (int size : ICON_SIZES) {
            ImageIO.write(renderAppIcon(size), "png", iconset.resolve("icon_" + size + "x" + size + ".png").toFile());
            ImageIO.write(renderAppIcon(size * 2), "png", iconset.resolve("icon_" + size + "x" + size + "@2x.png").toFile());
        }
        ImageIO.write(renderTrayIcon(18), "png", tray.resolve("LidSleepXTemplate.png").toFile());
        ImageIO.write(renderTrayIcon(36), "png", tray.resolve("LidSleepXTemplate@2x.png").toFile());
        System.out.println("Created icon assets in " + root.toAbsolutePath());
    }

    private static BufferedImage renderAppIcon(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = graphics(image);
        float padding = size * 0.08f;
        float tile = size - (padding * 2f);
        RoundRectangle2D.Float tileShape = new RoundRectangle2D.Float(
            padding, padding, tile, tile, size * 0.24f, size * 0.24f
        );
        graphics.setPaint(new GradientPaint(
            padding, padding, new Color(89, 102, 168),
            size - padding, size - padding, new Color(23, 26, 53)
        ));
        graphics.fill(tileShape);
        graphics.setPaint(new Color(255, 255, 255, 35));
        graphics.setStroke(new BasicStroke(Math.max(1f, size * 0.008f)));
        graphics.draw(tileShape);

        graphics.setColor(new Color(245, 223, 163));
        graphics.fill(crescent(
            size * 0.52f, size * 0.53f, size * 0.28f,
            size * 0.68f, size * 0.40f, size * 0.24f
        ));
        graphics.setColor(new Color(255, 242, 189));
        graphics.fill(star(size * 0.73f, size * 0.26f, size * 0.032f));
        graphics.fill(star(size * 0.82f, size * 0.39f, size * 0.022f));
        graphics.dispose();
        return image;
    }

    private static BufferedImage renderTrayIcon(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = graphics(image);
        graphics.setColor(Color.BLACK);
        graphics.fill(crescent(
            size * 0.49f, size * 0.53f, size * 0.31f,
            size * 0.66f, size * 0.40f, size * 0.25f
        ));
        graphics.fill(star(size * 0.76f, size * 0.24f, size * 0.045f));
        graphics.dispose();
        return image;
    }

    private static Graphics2D graphics(BufferedImage image) {
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        return graphics;
    }

    private static Area crescent(float x, float y, float outerRadius, float cutX, float cutY, float cutRadius) {
        Area moon = new Area(new Ellipse2D.Float(
            x - outerRadius, y - outerRadius, outerRadius * 2f, outerRadius * 2f
        ));
        moon.subtract(new Area(new Ellipse2D.Float(
            cutX - cutRadius, cutY - cutRadius, cutRadius * 2f, cutRadius * 2f
        )));
        return moon;
    }

    private static Shape star(float x, float y, float radius) {
        Path2D.Float path = new Path2D.Float();
        path.moveTo(x, y - radius);
        path.lineTo(x + radius * 0.35f, y - radius * 0.35f);
        path.lineTo(x + radius, y);
        path.lineTo(x + radius * 0.35f, y + radius * 0.35f);
        path.lineTo(x, y + radius);
        path.lineTo(x - radius * 0.35f, y + radius * 0.35f);
        path.lineTo(x - radius, y);
        path.lineTo(x - radius * 0.35f, y - radius * 0.35f);
        path.closePath();
        return path;
    }
}
