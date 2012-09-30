package com.snobwall.transilook.osm;

public class SlippyUtil {
    
    public static int[] getTileNumber(final double mercX, final double mercY,
            final int zoom) {

        int xtile = (int) Math.floor((1 + (mercX / 180)) / 2 * (1 << zoom));
        int ytile = (int) Math.floor((1 - (mercY / 180)) / 2 * (1 << zoom));

        return new int[] { xtile, ytile };
    }

    public static BoundingBox tile2boundingBox(final int x, final int y,
            final int zoom) {
        
        final double north, south, east, west;
        
        north = tile2mercY(y, zoom);
        south = tile2mercY(y + 1, zoom);
        west = tile2mercX(x, zoom);
        east = tile2mercX(x + 1, zoom);
        
        return new BoundingBox(north, south, east, west);
    }

    public static double tileSpan(int zoom) {
        return mercUnitsPerPixel(zoom) * 256;
    }

    public static double mercUnitsPerPixel(int zoom) {
        return 360.0 / Math.pow(2.0, zoom) / 256;
    }
    
    public static double tile2mercX(int x, int z) {
        return x / Math.pow(2.0, z) * 360.0 - 180;
    }

    public static double tile2mercY(int y, int z) {
        return -(y / Math.pow(2.0, z) * 360.0 - 180);
    }
}
