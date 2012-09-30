package com.snobwall.transilook.osm;

public class Mercator {

    public static double mercY(double lat) {
        return Math.toDegrees(Math.log(Math.tan(Math.PI / 4 + Math.toRadians(lat) / 2)));
    }

    public static double unmercY(double mercY) {
        return Math.toDegrees(2 * Math.atan(Math.exp(Math.toRadians(mercY))) - Math.PI / 2);
    }

    public static double mercX(double lon) {
        return lon;
    }

    public static double unmercX(double mercX) {
        return mercX;
    }
}