package shop;

import java.util.Random;

public class Utils {

  public static double normal(double a, double b) {
    double x1;
    if (b <= 0.0) {
      System.err.println("SimGenerator.normal: b must be >0");
      return -1;
    }

    Random random = new Random();
    x1 = random.nextGaussian();
    return (a + x1 * b);
  }
}
