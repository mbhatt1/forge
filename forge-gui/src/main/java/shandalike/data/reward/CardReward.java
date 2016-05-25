package shandalike.data.reward;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;

import forge.card.CardRules;
import forge.card.CardRulesPredicates;
import forge.card.MagicColor;
import forge.card.PrintSheet;
import forge.deck.CardPool;
import forge.item.InventoryItem;
import forge.item.PaperCard;
import forge.util.ItemPool;
import shandalike.Model;
import shandalike.Util;
import shandalike.data.character.Inventory;
import shandalike.data.entity.town.CardShop;
import shandalike.data.entity.town.Town;
import shandalike.mtg.Format;
import shandalike.mtg.ShopModel;
import shandalike.mtg.ShopModel.TransactionResult;

/**
 * Description of a Shandalike card reward.
 * There is some reward code in Quest Mode that maybe could have been reused here, but I didn't fully understand
 * it and the system seemed a bit limited. This should be a fairly general reward system that can duplicate
 * anything found in the original SHandalar and then some.
 * @author wcj
 *
 */
public class CardReward implements Reward {
	/** Direct list of card names to be awarded, if present. Overrides other options. */
	public List<String> cards;
	/** Number of cards to award. */
	public int n = 0;
	public transient int nRemaining = 0;
	/** Policy for awarding cards (random/pick) */
	public String policy = "random";
	/** 
	 * Number of duplicates to make available, for pick mode. If n>1, any single card
	 * may only be picked this many times.
	 */
	public int dupeQty = 1;
	/** If this is true the player's inventory is used as the base cardpool */
	public boolean duplicate = false;
	/** Predicate to match format cards against to get cardpool. */
	public transient Predicate<PaperCard> predicate = Predicates.alwaysTrue();
	/** Cardpool that reward will be drawn from. */
	private transient CardPool cardPool;
	/** Actual computed reward */
	public transient List<PaperCard> reward;
	/** Textual description of reward */
	public String description = "unknown reward";
	
	public String getDescription() {
		return description;
	}
	
	/**
	 * Add a value filter to the reward cards.
	 * @param min
	 * @param max
	 */
	public void filterValue(final int min, final int max) {
		final Format format = Util.getFormat();
		Predicate<PaperCard> valueFilter = new Predicate<PaperCard>() {
			@Override
			public boolean apply(PaperCard input) {
				int val = format.getCardValue(input);
				return (val >= min && val <= max);
			}
		};
		predicate = Predicates.and(predicate, valueFilter);
	}
	
	public void filterColor(String color) {
		MagicColor.Color c = Util.colorForName(color);
		if(c == null) return;
		Predicate<CardRules> p1;
		if(color.equals("colorless")) {
			p1 = CardRulesPredicates.isMonoColor(c.getColormask());
		} else {
			p1 = CardRulesPredicates.hasColor(c.getColormask());
		}
		final Predicate<CardRules> p2 = p1;
		Predicate<PaperCard> filter = new Predicate<PaperCard>() {
			@Override
			public boolean apply(PaperCard arg0) {
				return p2.apply(arg0.getRules());
			}			
		};
		predicate = Predicates.and(predicate, filter);
	}
	
	/**
	 * Allow the player to dupe a card from his inventory matching the predicates.
	 */
	public void setDuplicateCard() {
		policy = "pick";
		duplicate = true;
	}
	
	public void build() {
		// Early out if card pool already picked.
		if(cardPool != null) return;
		// If a list of cards is specified, those are the reward cards, period.
		if(cards != null) {
			reward = new ArrayList<PaperCard>();
			for(String cardName: cards) {
				PaperCard pc = Util.getFormat().getCardByName(cardName);
				if(pc != null) {
					reward.add(pc);
				}
			}
			return;
		}
		// Generate card pool
		cardPool = new CardPool();
		if(duplicate) {
			for(Entry<PaperCard, Integer> c: Util.getPlayerInventory().cardPool) {
				if(predicate.apply(c.getKey())) {
					cardPool.add(c.getKey(), dupeQty);
				}
			}
			return;
		}
		// Non-dupe -- pull from format
		for(PaperCard c: Util.getFormat().getAllCards()) {
			if(predicate.apply(c)) {
				if(policy.equals("pick")) {
					cardPool.add(c, dupeQty);
				} else {
					cardPool.add(c);
				}
			}
		}
	}
		
	public void award() {
		// THe pick utility automatically adds the cards to inventory
		if(policy.equals("pick")) return;
		if(reward != null) {
			// Pre-computed award
			Util.getPlayerInventory().addAllCards(reward);
			return;
		}
		if(policy.equals("random")) {
			// From BoosterUtils
			PrintSheet ps = new PrintSheet("rewards");
			ps.addAll(cardPool.toFlatList());
			reward.addAll(ps.random(n, true));
			Util.getPlayerInventory().addAllCards(reward);
		}
	}
	
	public boolean requiresChoice() {
		return (policy.equals("pick"));
	}
	
	public void choose() {
		if(policy.equals("pick")) {
			reward = new ArrayList<PaperCard>();
			Util.openShop(getShopModel());
		}
	}
	
	public ShopModel getShopModel() {
		nRemaining = n;
		return new ShopModel() {

			@Override
			public ItemPool<InventoryItem> getPlayerInventory() {
				return ItemPool.createFrom(Model.adventure.getPlayer().getInventory().cardPool, InventoryItem.class);
			}

			@Override
			public ItemPool<InventoryItem> getShopInventory() {
				return ItemPool.createFrom(cardPool, InventoryItem.class);
			}

			@Override
			public long getPlayerCurrency() {
				return nRemaining;
			}

			@Override
			public long getShopCurrency() {
				return 0;
			}

			@Override
			public String getCurrencyName() {
				return "picks";
			}

			@Override
			public boolean canBuy() {
				return true;
			}

			@Override
			public boolean canSell() {
				return false;
			}

			@Override
			public TransactionResult buy(Iterable<Entry<InventoryItem, Integer>> items, boolean dryRun) {
				TransactionResult tr = new TransactionResult();
				
				// Tally legal buys and cost
		        long totalCost = 0;
		        ItemPool<InventoryItem> itemsToBuy = new ItemPool<>(InventoryItem.class);
		        for (Entry<InventoryItem, Integer> itemEntry : items) {
		            final InventoryItem item = itemEntry.getKey();
		            if (item instanceof PaperCard) {
		                final int qty = itemEntry.getValue();
		                itemsToBuy.add(item, qty);
		                totalCost += getBuyPrice(item, qty);
		            }
		        }
		        if (itemsToBuy.isEmpty()) { return tr; }
		        
		        long creditsShort = totalCost - getPlayerCurrency();
		        if(creditsShort > 0) {
		        	tr.totalCurrencyTransferredToShop = totalCost;
		        	tr.errorMessage = "You need " + creditsShort + " more " + getCurrencyName() + " to make this purchase.";
		        	return tr;
		        }
		        
		        if(dryRun) {
		        	tr.totalCurrencyTransferredToShop = totalCost;
		        	tr.transactionComplete = true;
		        	tr.errorMessage = null;
		        	return tr;
		        }
		        
		        // Add cards to player inventory.
		        ItemPool<InventoryItem> itemsToAdd = new ItemPool<>(InventoryItem.class);
		        for (Entry<InventoryItem, Integer> itemEntry : itemsToBuy) {
		            final InventoryItem item = itemEntry.getKey();
		            final int qty = itemEntry.getValue();
		            final long value = getBuyPrice(item, qty);
		            // Take gold from player
		            nRemaining -= value;
	            	// Add card to player
		            for(int i=0; i<qty; i++)
		            	reward.add((PaperCard)item);
		            Util.getPlayerInventory().addCard((PaperCard)item, qty);
	            	itemsToAdd.add(item, qty);
		        }

		        // Return successful transaction details
		        tr.totalCurrencyTransferredToShop = totalCost;
		        tr.transactionComplete = true;
		        tr.errorMessage = null;
		        tr.itemsShop = itemsToBuy;
		        tr.itemsInventory = itemsToAdd;
				return tr;
			}

			@Override
			public TransactionResult sell(Iterable<Entry<InventoryItem, Integer>> items, boolean dryRun) {
				TransactionResult tr = new TransactionResult();
				tr.transactionComplete = false;
				tr.errorMessage = "Cannot sell cards here.";
				return tr;
			}

			@Override
			public long getBuyPrice(InventoryItem item, Integer count) {
				return 1;
			}

			@Override
			public long getSellPrice(InventoryItem item, Integer count) {
				return 0;
			}

			@Override
			public int getQtyOwned(InventoryItem item) {
				return Model.adventure.getPlayer().getInventory().cardPool.count((PaperCard)item);
			}
			
		};
	}
}
