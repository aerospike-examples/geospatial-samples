package com.aerospike.delivery.util;

import java.util.ArrayList;
import java.util.Collection;


public class Util {

  public static <T> Collection<T> makeCollection(Iterable<T> iterable) {
    Collection<T> result = new ArrayList<>();
    for (T obj : iterable) {
      result.add(obj);
    }
    return result;
  }

}
