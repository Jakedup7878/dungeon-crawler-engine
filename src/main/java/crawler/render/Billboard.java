package crawler.render;

/**
 * A sprite standing on the floor at a point of the map, always facing the viewer.
 *
 * @param x      map units (cell centres are at +0.5)
 * @param y      map units
 * @param height how tall it stands, in cells (walls are 1 tall); its width follows the image's shape
 */
public record Billboard(double x, double y, Sprite sprite, double height) {
}
