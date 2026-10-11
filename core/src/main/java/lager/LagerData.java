package lager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LagerData {

  private final Map<String, LagerEntry> entries = new LinkedHashMap<>();

  public void addItem(String item, int amount, String row, String chest, String position) {
    if (amount <= 0) {
      return;
    }

    LagerEntry key = new LagerEntry(item, 0, row, chest, position);
    if (key.getItem().isEmpty()) {
      return;
    }

    LagerEntry existing = this.entries.get(key.storageKey());
    if (existing == null) {
      this.entries.put(key.storageKey(), new LagerEntry(item, amount, row, chest, position));
    } else {
      existing.addAmount(amount);
    }
  }

  // Überladene Methode ohne Position für Abwärtskompatibilität
  public void addItem(String item, int amount, String row, String chest) {
    this.addItem(item, amount, row, chest, "");
  }

  public void removeItem(String item, int amount, String row, String chest, String position) {
    if (amount <= 0) {
      return;
    }

    LagerEntry key = new LagerEntry(item, 0, row, chest, position);
    LagerEntry existing = this.entries.get(key.storageKey());
    if (existing == null) {
      return;
    }

    existing.addAmount(-amount);
    if (existing.getAmount() <= 0) {
      this.entries.remove(key.storageKey());
    }
  }

  // Überladene Methode ohne Position für Abwärtskompatibilität
  public void removeItem(String item, int amount, String row, String chest) {
    this.removeItem(item, amount, row, chest, "");
  }

  public Collection<LagerEntry> getEntries() {
    return this.entries.values();
  }

  public List<LagerEntry> getEntriesSorted() {
    List<LagerEntry> list = new ArrayList<>(this.entries.values());
    list.sort(Comparator
        .comparing(LagerEntry::getItem)
        .thenComparing(LagerEntry::getRow)
        .thenComparing(LagerEntry::getChest)
        .thenComparing(LagerEntry::getPosition));
    return list;
  }

  /** Für HUD: Itemname → Gesamtmenge über alle Standorte */
  public Map<String, Integer> getAllItems() {
    Map<String, Integer> totals = new LinkedHashMap<>();
    for (LagerEntry entry : this.entries.values()) {
      if (entry.getAmount() <= 0) {
        continue;
      }
      totals.merge(entry.getItem(), entry.getAmount(), Integer::sum);
    }
    return totals;
  }

  public int getEntryCount() {
    return (int) this.entries.values().stream().filter(e -> e.getAmount() > 0).count();
  }
}
