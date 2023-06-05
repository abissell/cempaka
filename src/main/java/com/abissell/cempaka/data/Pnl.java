/*
 * cempaka, an algorithmic trading platform written in Java
 * Copyright (C) 2023 Andrew Bissell
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.abissell.cempaka.data;

import com.abissell.javautil.io.ThreadLocalFormat;
import com.abissell.fixbridge.Side;

public /* primitive */ /* value */ record Pnl(
        double netPnl,
        double highWatermark,
        double netQty,
        double grossPnl,
        double fees,
        double botQty,
        double botVal,
        double sldQty,
        double sldVal)
{
    public static final Pnl NO_TRADES = new Pnl(0.0d, 0.0d, 0.0d, 0.0d,
            0.0d, 0.0d, 0.0d, 0.0d, 0.0d);

    public Pnl withChange(Side side, double feesChg, double qtyChg,
            double valChg, Constraints constraints) {
        double newFees = fees + feesChg;
        double newBotQty = botQty, newBotVal = botVal;
        double newSldQty = sldQty, newSldVal = sldVal;
        double newGrossPnl = grossPnl;
        switch (side) {
            case BUY -> {
                newBotQty = botQty + qtyChg;
                newBotVal = botVal + valChg;
                if (Math.abs(newBotQty - newSldQty) <= constraints.minOrderQty()) {
                    newGrossPnl = newSldVal - newBotVal;
                }
            }
            case SELL -> {
                newSldQty = sldQty + qtyChg;
                newSldVal = sldVal + valChg;
                if (Math.abs(newBotQty - newSldQty) <= constraints.minOrderQty()) {
                    newGrossPnl = newSldVal - newBotVal;
                }
            }
            default -> throw new IllegalArgumentException("Can't handle Pnl change with side=" + side);
        };

        double newNetPnl = newGrossPnl - newFees;
        double newHighWatermark = Math.max(highWatermark, newNetPnl);
        return new Pnl(newNetPnl, newHighWatermark, newBotQty - newSldQty, newGrossPnl,
                newFees, newBotQty, newBotVal, newSldQty, newSldVal);
    }

    @Override
    public String toString() {
        var format = ThreadLocalFormat.with8SigDigits();
        var buf = new StringBuilder(32 + 6 * (8 + 1 + 16));
        buf.append("Pnl[")
            .append("netPnl=").append(format.format(netPnl))
            .append("highWatermark=").append(format.format(highWatermark))
            .append(", netQty=").append(format.format(netQty))
            .append(", grossPnl=").append(format.format(grossPnl))
            .append(", fees=").append(format.format(fees))
            .append(", botQty=").append(format.format(botQty))
            .append(", botVal=").append(format.format(botVal))
            .append(", sldQty=").append(format.format(sldQty))
            .append(", sldVal=").append(format.format(sldVal))
            .append("]");
        return buf.toString();
    }
}
