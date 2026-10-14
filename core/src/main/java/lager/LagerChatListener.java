package lager;

import net.labymod.api.event.Priority;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.client.chat.ChatMessageSendEvent;

public class LagerChatListener {

  private final LagerMod addon;

  public LagerChatListener(LagerMod addon) {
    this.addon = addon;
  }

  @Subscribe(Priority.FIRST)
  public void onChatSend(ChatMessageSendEvent event) {
    if (!this.addon.configuration().enabled().get()) {
      return;
    }

    String msg = event.getMessage().toLowerCase().trim();

    // Prüft, ob die Nachricht der Befehl /lager ist
    if (msg.equals("/lager") || msg.startsWith("/lager ")) {
      // Event abbrechen, damit es nicht an den Server gesendet wird
      event.setCancelled(true);
      
      // Lager-Screen öffnen
      this.addon.openLagerScreen();
    }
  }
}