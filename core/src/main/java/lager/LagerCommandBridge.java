package lager;

import java.util.Locale;

/**
 * Wird vom Client-Command, Chat-Listener und den Mixins genutzt,
 * damit {@code /lager} zuverlässig lokal geöffnet und nicht an den Server geschickt wird.
 */
public final class LagerCommandBridge {

  private static LagerMod instance;

  private LagerCommandBridge() {
  }

  public static void bind(LagerMod addon) {
    instance = addon;
  }

  /**
   * @param input Chatzeile oder Command, mit oder ohne führendes {@code /}
   * @return {@code true}, wenn es ein Lager-Befehl war und lokal behandelt wurde
   */
  public static boolean tryHandle(String input) {
    if (instance == null || input == null) {
      return false;
    }

    if (!instance.configuration().enabled().get()) {
      return false;
    }

    String trimmed = input.trim();
    if (trimmed.isEmpty()) {
      return false;
    }

    if (trimmed.startsWith("/")) {
      trimmed = trimmed.substring(1).trim();
    }

    if (trimmed.isEmpty()) {
      return false;
    }

    int space = trimmed.indexOf(' ');
    String command = (space < 0 ? trimmed : trimmed.substring(0, space))
        .toLowerCase(Locale.ROOT);

    if (!command.equals("lager") && !command.equals("lagermod")) {
      return false;
    }

    instance.openLagerScreen();
    instance.logger().info("/" + command + " lokal abgefangen (Mixin/Bridge)");
    return true;
  }
}
