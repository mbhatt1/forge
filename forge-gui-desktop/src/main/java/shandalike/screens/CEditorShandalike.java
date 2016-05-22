/*
 * Forge: Play Magic: the Gathering.
 * Copyright (C) 2011  Forge Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package shandalike.screens;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.google.common.base.Function;
import com.google.common.base.Supplier;

import forge.UiCommand;
import forge.deck.CardPool;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.gui.framework.DragCell;
import forge.gui.framework.FScreen;
import forge.item.InventoryItem;
import forge.item.PaperCard;
import forge.itemmanager.CardManager;
import forge.itemmanager.ColumnDef;
import forge.itemmanager.ItemManagerConfig;
import forge.itemmanager.views.ItemTableColumn;
import forge.model.FModel;
import forge.screens.deckeditor.AddBasicLandsDialog;
import forge.screens.deckeditor.SEditorIO;
import forge.screens.deckeditor.controllers.ACEditorBase;
import forge.screens.deckeditor.controllers.CEditorConstructed;
import forge.screens.deckeditor.controllers.DeckController;
import forge.screens.deckeditor.views.VAllDecks;
import forge.screens.deckeditor.views.VCurrentDeck;
import forge.screens.deckeditor.views.VDeckgen;
import forge.screens.match.controllers.CDetailPicture;
import forge.util.ItemPool;
import shandalike.data.character.Inventory;

/**
 * Child controller for quest deck editor UI.
 * <br><br>
 * Card catalog and decks are drawn from a QuestController object.
 *
 * <br><br><i>(C at beginning of class name denotes a control class.)</i>
 *
 * @author Forge
 * @version $Id: CEditorQuest.java 24868 2014-02-17 05:08:05Z drdev $
 */
public final class CEditorShandalike extends ACEditorBase<PaperCard, Deck> {
	private final Inventory inventory;
    private final DeckController<Deck> controller;
    private final List<DeckSection> allSections = new ArrayList<DeckSection>();
    private DragCell allDecksParent = null;
    private DragCell deckGenParent = null;

    private Map<PaperCard, Integer> decksUsingMyCards;

    private final Function<Entry<InventoryItem, Integer>, Comparable<?>> fnDeckCompare = new Function<Entry<InventoryItem, Integer>, Comparable<?>>() {
        @Override
        public Comparable<?> apply(final Entry<InventoryItem, Integer> from) {
            final Integer iValue = decksUsingMyCards.get(from.getKey());
            return iValue == null ? Integer.valueOf(0) : iValue;
        }
    };

    private final Function<Entry<? extends InventoryItem, Integer>, Object> fnDeckGet = new Function<Entry<? extends InventoryItem, Integer>, Object>() {
        @Override
        public Object apply(final Entry<? extends InventoryItem, Integer> from) {
            final Integer iValue = decksUsingMyCards.get(from.getKey());
            return iValue == null ? "" : iValue.toString();
        }
    };

    /**
     * Deck editor controller that gets deck data from Shandalike's Inventory object.
     */
    @SuppressWarnings("serial")
    public CEditorShandalike(final Inventory inv, final CDetailPicture cDetailPicture) {
        super(FScreen.DECK_EDITOR_QUEST, cDetailPicture);

        allSections.add(DeckSection.Main);
        allSections.add(DeckSection.Sideboard);
        
        this.inventory = inv;

        final CardManager catalogManager = new CardManager(cDetailPicture, false);
        final CardManager deckManager = new CardManager(cDetailPicture, false);

        catalogManager.setCaption("Inventory");

        catalogManager.setAlwaysNonUnique(true);
        deckManager.setAlwaysNonUnique(true);

        this.setCatalogManager(catalogManager);
        this.setDeckManager(deckManager);

        final Supplier<Deck> newCreator = new Supplier<Deck>() {
            @Override
            public Deck get() {
                return new Deck();
            }
        };

        this.controller = new DeckController<Deck>(inventory.getDeckStorage(), this, newCreator);

        getBtnAddBasicLands().setCommand(new UiCommand() {
            @Override
            public void run() {
                Deck deck = getDeckController().getModel();
                if (deck == null) { return; }

                // XXX: emulate Forge Quest Mode here?
                AddBasicLandsDialog dialog = new AddBasicLandsDialog(deck, FModel.getMagicDb().getEditions().get("ZEN"));
                CardPool landsToAdd = dialog.show();
                if (landsToAdd != null) {
                    onAddItems(landsToAdd, false);
                }
            }
        });
    }

    //=========== Overridden from ACEditorBase

    @Override
    protected CardLimit getCardLimit() {
//        if (FModel.getPreferences().getPrefBoolean(FPref.ENFORCE_DECK_LEGALITY)) {
//            return CardLimit.Default;
//        }
        return CardLimit.None; //if not enforcing deck legality, don't enforce default limit
    }

    /* (non-Javadoc)
     * @see forge.gui.deckeditor.ACEditorBase#onAddItems()
     */
    @Override
    protected void onAddItems(final Iterable<Entry<PaperCard, Integer>> items, final boolean toAlternate) {
        CEditorConstructed.onAddItems(this, items, toAlternate);
    }

    /* (non-Javadoc)
     * @see forge.gui.deckeditor.ACEditorBase#onRemoveItems()
     */
    @Override
    protected void onRemoveItems(final Iterable<Entry<PaperCard, Integer>> items, final boolean toAlternate) {
        CEditorConstructed.onRemoveItems(this, items, toAlternate);
    }

    /* (non-Javadoc)
     * @see forge.gui.deckeditor.ACEditorBase#buildAddContextMenu()
     */
    @Override
    protected void buildAddContextMenu(final EditorContextMenuBuilder cmb) {
        CEditorConstructed.buildAddContextMenu(cmb, sectionMode);
    }

    /* (non-Javadoc)
     * @see forge.gui.deckeditor.ACEditorBase#buildRemoveContextMenu()
     */
    @Override
    protected void buildRemoveContextMenu(final EditorContextMenuBuilder cmb) {
        CEditorConstructed.buildRemoveContextMenu(cmb, sectionMode);
    }

    /*
     * (non-Javadoc)
     *
     * @see forge.gui.deckeditor.ACEditorBase#updateView()
     */
    @Override
    public void resetTables() {
        this.sectionMode = DeckSection.Main;

        final Deck deck = this.controller.getModel();

        final ItemPool<PaperCard> cardpool = new ItemPool<PaperCard>(PaperCard.class);
        // Add player shandalike inventory to cardpool
        cardpool.addAll(inventory.cardPool);
        // remove cards that are in the deck from the card pool
        cardpool.removeAll(deck.getMain());
        // remove sideboard cards from the card pool
        cardpool.removeAll(deck.getOrCreate(DeckSection.Sideboard));
        this.getCatalogManager().setPool(cardpool);
        this.getDeckManager().setPool(deck.getMain());
    }

    //=========== Overridden from ACEditorBase

    /*
     * (non-Javadoc)
     *
     * @see forge.gui.deckeditor.ACEditorBase#getController()
     */
    @Override
    public DeckController<Deck> getDeckController() {
        return this.controller;
    }

    /**
     * Switch between the main deck and the sideboard editor.
     */
    public void cycleEditorMode() {
        int curindex = allSections.indexOf(sectionMode);
        curindex = (curindex + 1) % allSections.size();
        sectionMode = allSections.get(curindex);

        if (sectionMode == DeckSection.Sideboard) {
            this.getDeckManager().setPool(this.controller.getModel().getOrCreate(DeckSection.Sideboard));
        }
        else {
            this.getDeckManager().setPool(this.controller.getModel().getMain());
        }

        this.controller.updateCaptions();
    }

    /* (non-Javadoc)
     * @see forge.gui.deckeditor.ACEditorBase#show(forge.Command)
     */
    @SuppressWarnings("serial")
    @Override
    public void update() {
        this.decksUsingMyCards = this.inventory.countDecksForEachCard(); 

        final Map<ColumnDef, ItemTableColumn> colOverridesCatalog = new HashMap<ColumnDef, ItemTableColumn>();
        final Map<ColumnDef, ItemTableColumn> colOverridesDeck = new HashMap<ColumnDef, ItemTableColumn>();

        ItemTableColumn.addColOverride(ItemManagerConfig.QUEST_EDITOR_POOL, colOverridesCatalog, ColumnDef.NEW, inventory.fnNewCompare, inventory.fnNewGet);
        ItemTableColumn.addColOverride(ItemManagerConfig.QUEST_DECK_EDITOR, colOverridesDeck, ColumnDef.NEW, inventory.fnNewCompare, inventory.fnNewGet);
        ItemTableColumn.addColOverride(ItemManagerConfig.QUEST_DECK_EDITOR, colOverridesDeck, ColumnDef.DECKS, this.fnDeckCompare, this.fnDeckGet);

        this.getCatalogManager().setup(ItemManagerConfig.QUEST_EDITOR_POOL, colOverridesCatalog);
        this.getDeckManager().setup(ItemManagerConfig.QUEST_DECK_EDITOR, colOverridesDeck);

        resetUI();

        VCurrentDeck.SINGLETON_INSTANCE.getBtnSave().setVisible(true);
        VCurrentDeck.SINGLETON_INSTANCE.getBtnImport().setVisible(false);

        this.getBtnCycleSection().setVisible(true);
        this.getBtnCycleSection().setCommand(new UiCommand() {
            @Override public void run() {
                cycleEditorMode();
            }
        });

        deckGenParent = removeTab(VDeckgen.SINGLETON_INSTANCE);
        allDecksParent = removeTab(VAllDecks.SINGLETON_INSTANCE);

        if (this.controller.getModel() == null) {
            this.getDeckController().setModel(new Deck());
        } else {
            this.controller.refreshModel();
        }
    }

    /* (non-Javadoc)
     * @see forge.gui.deckeditor.controllers.ACEditorBase#canSwitchAway()
     */
    @Override
    public boolean canSwitchAway(final boolean isClosing) {
        if (SEditorIO.confirmSaveChanges(FScreen.DECK_EDITOR_QUEST, isClosing)) {
        	// XXX: should we autosave shandalike here?
            //FModel.getQuest().save();
            return true;
        }
        return false;
    }

    /* (non-Javadoc)
     * @see forge.gui.deckeditor.controllers.ACEditorBase#resetUIChanges()
     */
    @Override
    public void resetUIChanges() {
        //CSubmenuQuestDecks.SINGLETON_INSTANCE.update();
        //Re-add tabs
        if (deckGenParent != null) {
            deckGenParent.addDoc(VDeckgen.SINGLETON_INSTANCE);
        }
        if (allDecksParent != null) {
            allDecksParent.addDoc(VAllDecks.SINGLETON_INSTANCE);
        }
    }
}
