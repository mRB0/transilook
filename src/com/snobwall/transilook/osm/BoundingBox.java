package com.snobwall.transilook.osm;

public class BoundingBox {
    public final double north, south, east, west;

    public BoundingBox(double north, double south, double east, double west) {
        super();
        this.north = north;
        this.south = south;
        this.east = east;
        this.west = west;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        long temp;
        temp = Double.doubleToLongBits(east);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(north);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(south);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        temp = Double.doubleToLongBits(west);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        BoundingBox other = (BoundingBox) obj;
        if (Double.doubleToLongBits(east) != Double.doubleToLongBits(other.east))
            return false;
        if (Double.doubleToLongBits(north) != Double.doubleToLongBits(other.north))
            return false;
        if (Double.doubleToLongBits(south) != Double.doubleToLongBits(other.south))
            return false;
        if (Double.doubleToLongBits(west) != Double.doubleToLongBits(other.west))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "BoundingBox [north=" + north + ", south=" + south + ", east=" + east + ", west=" + west + "]";
    }
}
