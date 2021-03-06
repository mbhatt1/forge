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
package forge.game.cost;

import forge.game.ability.AbilityUtils;
import forge.game.card.Card;
import forge.game.card.CounterType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;


public class CostPayEnergy extends CostPart {
    int paidAmount = 0;

    /**
     * Instantiates a new cost pay energy.
     *
     * @param amount
     *            the amount
     */
    public CostPayEnergy(final String amount) {
        this.setAmount(amount);
    }

    @Override
    public int paymentOrder() { return 7; }

    /*
     * (non-Javadoc)
     *
     * @see forge.card.cost.CostPart#toString()
     */
    @Override
    public final String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("Pay ");

        int count = Integer.parseInt(getAmount()) - paidAmount;
        for (int i = 0; i < count; i++) {
            sb.append("{E}");
        }
        return sb.toString();
    }

    /*
     * (non-Javadoc)
     *
     * @see forge.card.cost.CostPart#refund(forge.Card)
     */
    @Override
    public final void refund(final Card source) {
        // Really should be activating player
        source.getController().loseEnergy(this.paidAmount * -1);
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * forge.card.cost.CostPart#canPay(forge.card.spellability.SpellAbility,
     * forge.Card, forge.Player, forge.card.cost.Cost)
     */
    @Override
    public final boolean canPay(final SpellAbility ability) {
        Integer amount = this.convertAmount();
        Player activator = ability.getActivatingPlayer();
        if (amount == null) { // try to calculate when it's defined.
            String sAmount = getAmount();
            String sVar = ability.getSVar(sAmount);
            if (!sVar.startsWith("XChoice")) {
                amount = AbilityUtils.calculateAmount(ability.getHostCard(), getAmount(), ability);
            }
        } else {
            return activator.getCounters(CounterType.ENERGY) >= amount;
        }

        return true;
    }

    @Override
    public boolean payAsDecided(Player ai, PaymentDecision decision, SpellAbility ability) {
        paidAmount = decision.c;
        return ai.payEnergy(paidAmount, null);
    }

    public <T> T accept(ICostVisitor<T> visitor) {
        return visitor.visit(this);
    }

}
