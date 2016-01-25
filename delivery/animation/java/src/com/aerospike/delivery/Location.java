package com.aerospike.delivery;


import com.aerospike.delivery.db.base.Database;
import com.aerospike.delivery.util.OurRandom;
import com.google.gson.Gson;

import java.awt.Point;

public class Location extends Point.Double {

  public static final double maxOffset = .5;
  public static final double maxDistance = java.lang.Double.MAX_VALUE;
  static double xStretch = Database.mapWidthPx > Database.mapHeightPx ? (double) Database.mapWidthPx / Database.mapHeightPx : 1;
  static double yStretch = Database.mapHeightPx > Database.mapWidthPx ? (double) Database.mapHeightPx / Database.mapWidthPx : 1;

  public Location(double x, double y) {
    super(x, y);
  }

  public Location(Point.Double point) { super(point.x, point.y); }

  static class GeoJSONPointDouble {
    String type = "Point";
    double[] coordinates;
  }

  public String toGeoJSONPointDouble() {
    GeoJSONPointDouble geo = new GeoJSONPointDouble();
    geo.coordinates = new double[] { x, y };
    String result = new Gson().toJson(geo);
    return result;
  }

  public static Location makeFromGeoJSONPointDouble(String geoJSON) {
    GeoJSONPointDouble geo = new Gson().fromJson(geoJSON, GeoJSONPointDouble.class);
    Location result = new Location(geo.coordinates[0], geo.coordinates[1]);
    return result;
  }

  public static Location makeRandom() {
    // BooleanRunner and y range from -0.500 to 0.500 in steps corresponding to one pixel
    double random01x;
    double random01y;
    boolean useGaussian = true;
    if (useGaussian) {
      random01x = (int) (nextGaussianScaled(xStretch) * Database.mapWidthPx) / (double) Database.mapWidthPx;
      random01y = (int) (nextGaussianScaled(yStretch) * Database.mapHeightPx) / (double) Database.mapHeightPx;
    } else {
      random01x = OurRandom.instance.nextInt(Database.mapWidthPx) / (double) Database.mapWidthPx;
      random01y = OurRandom.instance.nextInt(Database.mapHeightPx) / (double) Database.mapHeightPx;
    }
    Location result = new Location(random01x - .5, random01y - .5);
//    System.out.printf("%s %f\n", result, random01x);
    return result;
  }

  private static double nextGaussianScaled(double stretch) {
    final double factor = 4 * stretch;
    double result;
    do {
      result = OurRandom.instance.nextGaussian() / factor;
    } while (result < -.5 || .5 < result);
    return result;
  }


  @Override
  public String toString() {
    String result = String.format("[% 1.3f % 1.3f]", x, y);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof Location) {
      Location other = (Location)obj;
      return (x == other.x) && (y == other.y);
    }
    return super.equals(obj);
  }

  public double distanceTo(Location other) {
    double xSide = other.x - x;
    double ySide = other.y - y;
    double xSide2 = xSide * xSide;
    double ySide2 = ySide * ySide;
    return Math.sqrt(xSide2 + ySide2);
  }

  public static void main(String[] args) {
    Location l1 = new Location(-.5, -.5);
    Location l2 = new Location(0.5, 0.5);
    System.out.println(l1.distanceTo(l2));
    System.out.println(l2.distanceTo(l1));
  }

  /**
   * Return a location that is partway toward a destination.
   * @param partway the distance that is partway there
   * @param destination
   * @return location partway to the destination
   */
  public Location partWay(double partway, Location destination) {
    final double wholeway = distanceTo(destination);
    if (wholeway == 0) {
      return this;
    } else {
      final double portion = partway / wholeway;
      final Location result = new Location(
          this.x + portion * (destination.x - this.x),
          this.y + portion * (destination.y - this.y));
      return result;
    }
  }

}
