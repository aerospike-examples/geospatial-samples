package com.aerospike.delivery.util;

import java.util.Random;

public class OurRandom {

  // Not final because sometimes we want to switch to aa specific seed.
  public static Random instance = new Random();
}
