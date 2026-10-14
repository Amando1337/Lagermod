package lager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;
import net.labymod.api.Laby;
import net.labymod.api.addon.LabyAddon;
import net.labymod.api.client.gui.screen.LabyScreen;
import net.labymod.api.client.gui.screen.key.HotkeyService;
import net.labymod.api.models.addon.annotation.AddonMain;

@AddonMain
public class LagerMod extends LabyAddon<LagerConfig> {

  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
  private LagerData lagerData;
  private long suppressEscapeUntilMs;
  private File dataFile;

  @Override
  protected void enable() {
    File dir = new File("config/labymod");
    if (!dir.exists()) {
      dir.mkdirs();
    }
    this.dataFile = new File(dir, "kistenlager.json");

    this.lagerData = new LagerData();
    this.loadData();

    this.registerSettingCategory();
    this.registerCommand(new LagerCommand(this));
    this.registerListener(new LagerChatListener(this));
    this.registerListener(new LagerEscapeListener(this));

    this.labyAPI().hudWidgetRegistry().register(new LagerHudWidget(this));
    this.registerListener(new LagerContainerListener(this));

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

    this.logger().info("LagerMod erfolgreich geladen!");
  }

  public void disable() {
    this.saveData();
  }

  public void saveData() {
    try (FileWriter writer = new FileWriter(this.dataFile)) {
      GSON.toJson(this.lagerData.getRawEntries(), writer);
    } catch (IOException e) {
      this.logger().error("Fehler beim Speichern der Lagermod-Daten", e);
    }
  }

  public void loadData() {
    if (!this.dataFile.exists()) {
      return;
    }
    try (FileReader reader = new FileReader(this.dataFile)) {
      Type type = new TypeToken<Map<String, LagerEntry>>() {}.getType();
      Map<String, LagerEntry> loadedEntries = GSON.fromJson(reader, type);
      if (loadedEntries != null) {
        for (LagerEntry entry : loadedEntries.values()) {
          this.lagerData.addItem(entry.getItem(), entry.getAmount(), entry.getRow(), entry.getChest(), entry.getPosition());
        }
      }
    } catch (IOException e) {
      this.logger().error("Fehler beim Laden der Lagermod-Daten", e);
    }
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