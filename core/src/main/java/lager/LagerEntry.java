package lager;

import java.util.Locale;
import java.util.Objects;

/**
 * Ein Lagereintrag: Item + Menge + Lagerplatz (Reihe / Kiste / Position).
 */
public class LagerEntry {

  private final String item;
  private int amount;
  private final String row;
  private final String chest;
  private final String position; // NEU: Oben, Mitte, Unten etc.

  public LagerEntry(String item, int amount, String row, String chest, String position) {
    this.item = normalizeKey(item);
    this.amount = Math.max(0, amount);
    this.row = normalizePlace(row);
    this.chest = normalizePlace(chest);
    this.position = normalizePlace(position);
  }

  // Überladener Konstruktor für Abwärtskompatibilität (ohne Position)
  public LagerEntry(String item, int amount, String row, String chest) {
    this(item, amount, row, chest, "");
  }

  public String getItem() {
    return this.item;
  }

  public int getAmount() {
    return this.amount;
  }

  public void setAmount(int amount) {
    this.amount = Math.max(0, amount);
  }

  public void addAmount(int delta) {
    this.amount = Math.max(0, this.amount + delta);
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

  /** Eindeutiger Schlüssel: Item + Reihe + Kiste + Position */
  public String storageKey() {
    return this.item + "|" + this.row + "|" + this.chest + "|" + this.position;
  }

  public String locationLabel() {
    StringBuilder label = new StringBuilder();

    if (!this.row.isEmpty()) {
      label.append("Reihe ").append(this.row);
    }
    if (!this.chest.isEmpty()) {
      if (label.length() > 0) label.append(" · ");
      label.append("Kiste ").append(this.chest);
    }
    if (!this.position.isEmpty()) {
      if (label.length() > 0) label.append(" · ");
      label.append("(").append(this.position).append(")");
    }

    return label.toString();
  }

  public boolean matchesSearch(String query) {
    if (query == null || query.isEmpty()) {
      return true;
    }
    String q = query.toLowerCase(Locale.ROOT);
    return this.item.contains(q)
        || this.row.toLowerCase(Locale.ROOT).contains(q)
        || this.chest.toLowerCase(Locale.ROOT).contains(q)
        || this.position.toLowerCase(Locale.ROOT).contains(q)
        || this.locationLabel().toLowerCase(Locale.ROOT).contains(q);
  }

  public static String normalizeKey(String value) {
    return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
  }

  public static String normalizePlace(String value) {
    return value == null ? "" : value.trim();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof LagerEntry other)) {
      return false;
    }
    return Objects.equals(this.item, other.item)
        && Objects.equals(this.row, other.row)
        && Objects.equals(this.chest, other.chest)
        && Objects.equals(this.position, other.position);
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.item, this.row, this.chest, this.position);
  }
}