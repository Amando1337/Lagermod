package lager;

import net.labymod.api.client.gui.hud.hudwidget.text.TextHudWidget;
import net.labymod.api.client.gui.hud.hudwidget.text.TextHudWidgetConfig;
import net.labymod.api.client.gui.hud.hudwidget.text.TextLine;
import java.util.HashMap;
import java.util.Map;

public class LagerHudWidget extends TextHudWidget<TextHudWidgetConfig> {

  private final LagerMod addon;
  private final Map<String, TextLine> activeLines = new HashMap<>();

  public LagerHudWidget(LagerMod addon) {
    super("lager_widget");
    this.addon = addon;
  }

  @Override
  public void load(TextHudWidgetConfig config) {
    super.load(config);
    // Beim Laden registrieren wir noch gar keine Zeilen (HUD bleibt komplett leer)
  }

  @Override
  public void onUpdate() {
    if (this.addon.getLagerData() == null) {
      return;
    }

    Map<String, Integer> items = this.addon.getLagerData().getAllItems();

    for (Map.Entry<String, Integer> entry : items.entrySet()) {
      String itemName = entry.getKey();
      int amount = entry.getValue();

      if (amount > 0) {
        // Falls für das Item noch keine HUD-Zeile existiert, erstelle sie jetzt dynamisch
        TextLine line = this.activeLines.get(itemName);
        if (line == null) {
          line = super.createLine(itemName, String.valueOf(amount) + "x");
          this.activeLines.put(itemName, line);
        } else {
          line.updateAndFlush(String.valueOf(amount) + "x");
          line.setState(TextLine.State.VISIBLE);
        }
      } else if (this.activeLines.containsKey(itemName)) {
        // Falls Menge 0 ist, blende die Zeile aus
        this.activeLines.get(itemName).setState(TextLine.State.HIDDEN);
      }
    }
  }
}