package lager;

import net.labymod.api.client.component.Component;
import java.util.Locale;

public class LagerEntry {

  private String item;
  private int amount;
  private String row;
  private String chest;
  private String position;

  public LagerEntry(String item, int amount, String row, String chest, String position) {
    this.item = normalizeKey(item);
    this.amount = amount;
    this.row = row == null ? "" : row.trim();
    this.chest = chest == null ? "" : chest.trim();
    this.position = position == null ? "" : position.trim();
  }

  public static String normalizeKey(String input) {
    if (input == null) {
      return "";
    }
    return input.trim().toLowerCase(Locale.ROOT);
  }

  public String getItem() {
    return this.item;
  }

  public int getAmount() {
    return this.amount;
  }

  public void addAmount(int amount) {
    this.amount += amount;
  }

  public String getRow() {
    return this.row;
  }

  public String getChest() {
    return this.chest;
  }

  public String getPosition() {
    return this.position;
  }

  public String storageKey() {
    return this.item + "|" + this.row.toLowerCase(Locale.ROOT) + "|" + this.chest.toLowerCase(Locale.ROOT) + "|" + this.position.toLowerCase(Locale.ROOT);
  }

  public boolean matchesSearch(String query) {
    if (query == null || query.isEmpty()) {
      return true;
    }
    String q = query.toLowerCase(Locale.ROOT);
    return this.item.contains(q) || this.row.toLowerCase(Locale.ROOT).contains(q) || this.chest.toLowerCase(Locale.ROOT).contains(q);
  }

  public Component locationLabel() {
    String posKey = switch (this.position.toLowerCase(Locale.ROOT)) {
      case "top" -> "kistenlagermod.screen.position.top";
      case "middle" -> "kistenlagermod.screen.position.middle";
      case "bottom" -> "kistenlagermod.screen.position.bottom";
      default -> null;
    };

    Component posComponent = posKey != null 
        ? Component.translatable(posKey) 
        : Component.text(this.position.isEmpty() ? "-" : this.position);

    return Component.translatable("lagermod.label.location", Component.text(this.row), Component.text(this.chest), posComponent);
  }
}