package lager;

import net.labymod.api.event.Priority;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.client.input.KeyEvent;
import net.labymod.api.client.gui.screen.key.Key;

/**
 * Verhindert, dass Escape nach dem Schließen des Lager-UIs das Minecraft-Pausenmenü öffnet.
 */
public class LagerEscapeListener {

  private final LagerMod addon;

  public LagerEscapeListener(LagerMod addon) {
    this.addon = addon;
  }

  @Subscribe(Priority.FIRST)
  public void onKey(KeyEvent event) {
    if (event.key() != Key.ESCAPE) {
      return;
    }
    if (this.addon.shouldSuppressEscape()) {
      event.setCancelled(true);
    }
  }
}
