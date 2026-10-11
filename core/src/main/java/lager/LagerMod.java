package lager;

import net.labymod.api.Laby;
import net.labymod.api.addon.LabyAddon;
import net.labymod.api.client.gui.screen.LabyScreen;
import net.labymod.api.client.gui.screen.key.HotkeyService;
import net.labymod.api.models.addon.annotation.AddonMain;

@AddonMain
public class LagerMod extends LabyAddon<LagerConfig> {

  private LagerData lagerData;
  private long suppressEscapeUntilMs;

  @Override
  protected void enable() {
    this.lagerData = new LagerData();

    this.registerSettingCategory();

    // Registrerar kommandot /lager
    this.registerCommand(new LagerCommand(this));

    // Registrerar ChatListener för att fånga upp /lager i chatten
    this.registerListener(new LagerChatListener(this));
    this.registerListener(new LagerEscapeListener(this));

    // Widget & Container Listener
    this.labyAPI().hudWidgetRegistry().register(new LagerHudWidget(this));
    this.registerListener(new LagerContainerListener(this));

    // Hotkey: bei JEDEM Tastendruck UI öffnen/schließen (TOGGLE-State ignorieren,
    // sonst braucht man nach Escape zwei Drücke, weil der interne State noch "an" ist)
    Laby.references().hotkeyService().register(
        "lagermod-open",
        () -> this.configuration().enabled().get(),
        () -> this.configuration().openKey().get(),
        () -> HotkeyService.Type.TOGGLE,
        ignored -> {
          if (this.labyAPI().minecraft().isIngame()) {
            this.toggleLagerScreen();
          }
        }
    );

    this.logger().info("LagerMod har laddats med framgång!");
  }

  public void openLagerScreen() {
    this.labyAPI().minecraft().executeOnRenderThread(() -> {
      if (this.isLagerScreenOpen()) {
        return;
      }
      this.labyAPI().minecraft().minecraftWindow().displayScreen(new LagerScreen(this));
    });
  }

  public void toggleLagerScreen() {
    this.labyAPI().minecraft().executeOnRenderThread(() -> {
      if (this.isLagerScreenOpen()) {
        this.suppressEscapeBriefly();
        this.labyAPI().minecraft().minecraftWindow().displayScreenRaw(null);
        return;
      }
      this.labyAPI().minecraft().minecraftWindow().displayScreen(new LagerScreen(this));
    });
  }

  public boolean isLagerScreenOpen() {
    LabyScreen current = this.labyAPI().minecraft().minecraftWindow().currentLabyScreen();
    return current instanceof LagerScreen;
  }

  public LagerData getLagerData() {
    return this.lagerData;
  }

  /** Escape kurz unterdrücken, damit nach UI-Close kein Pausenmenü aufgeht. */
  public void suppressEscapeBriefly() {
    this.suppressEscapeUntilMs = System.currentTimeMillis() + 300L;
  }

  public boolean shouldSuppressEscape() {
    return System.currentTimeMillis() < this.suppressEscapeUntilMs;
  }

  @Override
  protected Class<LagerConfig> configurationClass() {
    return LagerConfig.class;
  }
}