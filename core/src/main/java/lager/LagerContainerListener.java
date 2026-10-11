package lager;

public class LagerContainerListener {

  private final LagerMod addon;

  public LagerContainerListener(LagerMod addon) {
    this.addon = addon;
  }

  public LagerMod getAddon() {
    return this.addon;
  }
}