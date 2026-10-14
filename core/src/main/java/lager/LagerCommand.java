package lager;

import net.labymod.api.client.chat.command.Command;

public class LagerCommand extends Command {

  private final LagerMod addon;

  public LagerCommand(LagerMod addon) {
    super("lager");
    this.addon = addon;
  }

  @Override
  public boolean execute(String prefix, String[] arguments) {
    this.addon.openLagerScreen();
    return true;
  }
}