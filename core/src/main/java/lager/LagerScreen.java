package lager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.gui.screen.Parent;
import net.labymod.api.client.gui.screen.activity.AutoActivity;
import net.labymod.api.client.gui.screen.activity.Link;
import net.labymod.api.client.gui.screen.activity.types.SimpleActivity;
import net.labymod.api.client.gui.screen.key.InputType;
import net.labymod.api.client.gui.screen.key.Key;
import net.labymod.api.client.gui.screen.widget.Widget;
import net.labymod.api.client.gui.screen.widget.widgets.ComponentWidget;
import net.labymod.api.client.gui.screen.widget.widgets.DivWidget;
import net.labymod.api.client.gui.screen.widget.widgets.input.ButtonWidget;
import net.labymod.api.client.gui.screen.widget.widgets.input.TextFieldWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.FlexibleContentWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.FlexibleContentWidget.FlexibleContentOrientation;
import net.labymod.api.client.gui.screen.widget.widgets.layout.ScrollWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.list.HorizontalListWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.list.VerticalListWidget;
import net.labymod.api.client.gui.screen.widget.widgets.minecraft.ItemStackWidget;
import net.labymod.api.client.world.item.ItemStack;
import net.labymod.api.event.Priority;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.client.input.KeyEvent;

@AutoActivity
@Link("lager.lss")
public class LagerScreen extends SimpleActivity {

  private static final String[] POSITIONS = {"Oben", "Mitte", "Unten"};

  private final LagerMod addon;
  private boolean closing;

  private String searchQuery = "";
  private TextFieldWidget itemInput;
  private TextFieldWidget amountInput;
  private TextFieldWidget rowInput;
  private TextFieldWidget chestInput;

  private ButtonWidget positionDropdownButton;
  private int currentPositionIndex = 0;

  private VerticalListWidget<Widget> itemList;
  private ScrollWidget scrollWidget;
  private ComponentWidget badgeText;

  public LagerScreen(LagerMod addon) {
    this.addon = addon;
  }

  @Override
  public void initialize(Parent parent) {
    super.initialize(parent);

    int itemCount = this.addon.getLagerData().getEntryCount();

    FlexibleContentWidget root = new FlexibleContentWidget();
    root.orientation().set(FlexibleContentOrientation.VERTICAL);
    root.addId("lager-root");

    this.itemInput = new TextFieldWidget();
    this.itemInput.placeholder(Component.text("Itemname"));
    this.itemInput.addId("lager-field-item");

    this.amountInput = new TextFieldWidget();
    this.amountInput.placeholder(Component.text("Menge"));
    this.amountInput.addId("lager-field-amount");

    this.rowInput = new TextFieldWidget();
    this.rowInput.placeholder(Component.text("Reihe (z.B. F oder 3)"));
    this.rowInput.addId("lager-field-row");

    this.chestInput = new TextFieldWidget();
    this.chestInput.placeholder(Component.text("Kiste (z.B. 8)"));
    this.chestInput.addId("lager-field-chest");

    this.positionDropdownButton = ButtonWidget.text(
        this.getPositionButtonText(),
        this::cyclePosition
    );
    this.positionDropdownButton.addId("lager-btn-position");

    // ----- Header -----
    HorizontalListWidget header = new HorizontalListWidget();
    header.addId("lager-header");

    FlexibleContentWidget headerText = new FlexibleContentWidget();
    headerText.orientation().set(FlexibleContentOrientation.VERTICAL);
    headerText.addId("lager-header-text");

    ComponentWidget title = ComponentWidget.text("LAGER");
    title.addId("lager-title");

    ComponentWidget subtitle = ComponentWidget.text("Inventarverwaltung");
    subtitle.addId("lager-subtitle");

    headerText.addContent(title);
    headerText.addContent(subtitle);

    HorizontalListWidget headerActions = new HorizontalListWidget();
    headerActions.addId("lager-header-actions");

    TextFieldWidget searchField = new TextFieldWidget();
    searchField.placeholder(Component.text("Suchen..."));
    searchField.addId("lager-search");
    searchField.setText(this.searchQuery);
    searchField.updateListener(query -> {
      this.searchQuery = query == null ? "" : query;
      this.refreshItemList();
    });

    DivWidget badge = new DivWidget();
    badge.addId("lager-badge");

    this.badgeText = ComponentWidget.text(
        itemCount == 1 ? "1 Item" : itemCount + " Items"
    );
    this.badgeText.addId("lager-badge-text");
    badge.addChild(this.badgeText);

    headerActions.addEntry(searchField);
    headerActions.addEntry(badge);

    header.addEntry(headerText);
    header.addEntry(headerActions);
    root.addContent(header);

    DivWidget headerLine = new DivWidget();
    headerLine.addId("lager-header-line");
    root.addContent(headerLine);

    // ----- Formular -----
    FlexibleContentWidget form = new FlexibleContentWidget();
    form.orientation().set(FlexibleContentOrientation.VERTICAL);
    form.addId("lager-form");

    ComponentWidget formTitle = ComponentWidget.text("Neuer Eintrag");
    formTitle.addId("lager-form-title");
    form.addContent(formTitle);

    // Zeile 1: Itemname | Menge
    HorizontalListWidget formRow1 = new HorizontalListWidget();
    formRow1.addId("lager-form-row");
    formRow1.addEntry(this.formColumn(this.itemInput));
    formRow1.addEntry(this.formColumn(this.amountInput));
    form.addContent(formRow1);

    // Zeile 2: Reihe | Kiste
    HorizontalListWidget formRow2 = new HorizontalListWidget();
    formRow2.addId("lager-form-row");
    formRow2.addEntry(this.formColumn(this.rowInput));
    formRow2.addEntry(this.formColumn(this.chestInput));
    form.addContent(formRow2);

    // Zeile 3: Position (eigene Zeile, nicht volle Breite)
    HorizontalListWidget formRowPosition = new HorizontalListWidget();
    formRowPosition.addId("lager-form-row-position");
    formRowPosition.addEntry(this.positionDropdownButton);
    form.addContent(formRowPosition);

    // Zeile 4: [Hinzufügen] [Abziehen]
    HorizontalListWidget formRowActions = new HorizontalListWidget();
    formRowActions.addId("lager-form-row-actions");

    ButtonWidget addButton = ButtonWidget.text("Hinzufügen", () -> this.applyChange(true));
    addButton.addId("lager-btn-add");

    ButtonWidget removeButton = ButtonWidget.text("Abziehen", () -> this.applyChange(false));
    removeButton.addId("lager-btn-remove");

    formRowActions.addEntry(addButton);
    formRowActions.addEntry(removeButton);
    form.addContent(formRowActions);

    root.addContent(form);

    // ----- Bestand -----
    ComponentWidget listTitle = ComponentWidget.text("Bestand");
    listTitle.addId("lager-section-title");
    root.addContent(listTitle);

    this.itemList = new VerticalListWidget<>();
    this.itemList.addId("lager-list");
    this.populateItemList();

    this.scrollWidget = new ScrollWidget(this.itemList);
    this.scrollWidget.addId("lager-scroll");
    root.addFlexibleContent(this.scrollWidget);

    ComponentWidget footer = ComponentWidget.text(
        "Klicke einen Eintrag zum Auswählen  ·  /lager"
    );
    footer.addId("lager-footer");
    root.addContent(footer);

    this.document().addChild(root);
  }

  @Subscribe(Priority.FIRST)
  public void onKey(KeyEvent event) {
    if (event.key() != Key.ESCAPE) {
      return;
    }

    // Escape nicht an Minecraft durchreichen (sonst öffnet sich das Pausenmenü)
    event.setCancelled(true);

    if (event.state() == KeyEvent.State.PRESS) {
      this.closeLagerUi();
    }
  }

  @Override
  public boolean keyPressed(Key key, InputType type) {
    if (key == Key.ESCAPE) {
      this.closeLagerUi();
      return true;
    }
    return super.keyPressed(key, type);
  }

  @Override
  public boolean keyReleased(Key key, InputType type) {
    if (key == Key.ESCAPE) {
      return true;
    }
    return super.keyReleased(key, type);
  }

  private void closeLagerUi() {
    if (this.closing) {
      return;
    }
    this.closing = true;
    this.addon.suppressEscapeBriefly();
    this.displayPreviousScreen();
  }

  private void cyclePosition() {
    this.currentPositionIndex = (this.currentPositionIndex + 1) % POSITIONS.length;
    this.updatePositionButtonText();
  }

  private String getPositionButtonText() {
    return "Position: " + POSITIONS[this.currentPositionIndex] + "  ▼";
  }

  private void updatePositionButtonText() {
    this.positionDropdownButton.updateComponent(Component.text(this.getPositionButtonText()));
  }

  private void setPositionByName(String posName) {
    if (posName == null) return;
    for (int i = 0; i < POSITIONS.length; i++) {
      if (POSITIONS[i].equalsIgnoreCase(posName.trim())) {
        this.currentPositionIndex = i;
        this.updatePositionButtonText();
        break;
      }
    }
  }

  private FlexibleContentWidget formColumn(Widget child) {
    FlexibleContentWidget column = new FlexibleContentWidget();
    column.orientation().set(FlexibleContentOrientation.VERTICAL);
    column.addId("lager-form-col");
    column.addContent(child);
    return column;
  }

  private void refreshItemList() {
    if (this.itemList == null) {
      return;
    }

    List<Widget> existing = new ArrayList<>(this.itemList.getChildren());
    for (Widget child : existing) {
      this.itemList.removeChild(child);
    }

    this.populateItemList();
  }

  private void populateItemList() {
    String query = this.searchQuery.toLowerCase(Locale.ROOT).trim();

    List<LagerEntry> filtered = this.addon.getLagerData().getEntriesSorted().stream()
        .filter(entry -> entry.getAmount() > 0)
        .filter(entry -> entry.matchesSearch(query))
        .toList();

    if (filtered.isEmpty()) {
      if (this.scrollWidget != null) {
        this.scrollWidget.removeId("lager-scroll");
        this.scrollWidget.addId("lager-scroll-empty");
      }

      DivWidget emptyPanel = new DivWidget();
      emptyPanel.addId("lager-empty-panel");

      ComponentWidget empty = ComponentWidget.text(
          query.isEmpty()
              ? "Noch keine Items im Lager"
              : "Keine Treffer für \"" + this.searchQuery + "\""
      );
      empty.addId("lager-empty");
      emptyPanel.addChild(empty);

      this.itemList.addChildInitialized(emptyPanel);
      return;
    }

    if (this.scrollWidget != null) {
      this.scrollWidget.removeId("lager-scroll-empty");
      this.scrollWidget.addId("lager-scroll");
    }

    for (int index = 0; index < filtered.size(); index += 2) {
      HorizontalListWidget gridRow = new HorizontalListWidget();
      gridRow.addId("lager-grid-row");

      for (int column = 0; column < 2; column++) {
        int itemIndex = index + column;
        if (itemIndex < filtered.size()) {
          gridRow.addEntry(this.createItemRow(filtered.get(itemIndex)));
        } else {
          DivWidget spacer = new DivWidget();
          spacer.addId("lager-item-spacer");
          gridRow.addEntry(spacer);
        }
      }

      this.itemList.addChildInitialized(gridRow);
    }
  }

  private DivWidget createItemRow(LagerEntry entry) {
    DivWidget row = new DivWidget();
    row.addId("lager-item-row");

    ItemStack iconStack = LagerItemIcons.resolve(entry.getItem());
    ItemStackWidget icon = new ItemStackWidget(iconStack);
    icon.addId("lager-item-icon");

    ComponentWidget name = ComponentWidget.text(this.formatItemName(entry.getItem()));
    name.addId("lager-item-name");

    ComponentWidget location = ComponentWidget.text(entry.locationLabel());
    location.addId("lager-item-location");

    ComponentWidget amountLabel = ComponentWidget.text(entry.getAmount() + "×");
    amountLabel.addId("lager-item-amount");

    row.addChild(icon);
    row.addChild(name);
    row.addChild(location);
    row.addChild(amountLabel);

    row.setPressable(() -> {
      this.itemInput.setText(entry.getItem());
      this.amountInput.setText(String.valueOf(entry.getAmount()));
      this.rowInput.setText(entry.getRow());
      this.chestInput.setText(entry.getChest());
      this.setPositionByName(entry.getPosition());
    });

    return row;
  }

  private void applyChange(boolean add) {
    String item = this.itemInput.getText();
    String amountStr = this.amountInput.getText().trim();
    String row = this.rowInput.getText();
    String chest = this.chestInput.getText();
    String position = POSITIONS[this.currentPositionIndex];

    if (LagerEntry.normalizeKey(item).isEmpty() || amountStr.isEmpty()) {
      return;
    }

    try {
      int amount = Integer.parseInt(amountStr);
      if (amount <= 0) {
        return;
      }

      if (add) {
        this.addon.getLagerData().addItem(item, amount, row, chest, position);
      } else {
        this.addon.getLagerData().removeItem(item, amount, row, chest, position);
      }

      this.reload();
    } catch (NumberFormatException ignored) {
    }
  }

  private String formatItemName(String item) {
    if (item == null || item.isEmpty()) {
      return "";
    }

    return item.substring(0, 1).toUpperCase(Locale.ROOT) + item.substring(1);
  }
}