package lager;

import net.labymod.api.client.chat.command.Command;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.component.format.NamedTextColor;

public class LagerCommand extends Command {

  private final LagerMod addon;

  public LagerCommand(LagerMod addon) {
    super("lager", "lagermod");
    this.addon = addon;
  }

  @Override
  public boolean execute(String prefix, String[] arguments) {
    // Öffnet den Screen auf dem Render-Thread
    this.addon.openLagerScreen();
    
    // Gibt eine Bestätigung im Chat aus
    this.displayMessage(Component.text("Lager geöffnet", NamedTextColor.GREEN));
    return true;
  }
}