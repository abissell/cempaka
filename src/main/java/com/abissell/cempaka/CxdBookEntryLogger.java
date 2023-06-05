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
package com.abissell.cempaka;

import com.abissell.javautil.io.ThreadLocalFormat;
import com.abissell.logutil.OptBuf;
import com.abissell.cempaka.data.CxOrders;
import com.abissell.cempaka.data.CxdBookAnalysis;
import com.abissell.cempaka.data.CxdBookLvl;
import com.abissell.cempaka.data.Fees;
import com.abissell.cempaka.data.MktDataBook;
import com.abissell.cempaka.data.TradingMode;
import com.abissell.fixbridge.Order;
import com.abissell.fixbridge.Side;

final class CxdBookEntryLogger {
    private CumPnl cumPnl;

    CxdBookEntryLogger() {
        this(CumPnl.NO_TRADES);
    }

    CxdBookEntryLogger(CumPnl cumPnl) {
        this.cumPnl = cumPnl;
    }

    OptBuf logEntryOrder(Order order, TradingMode tradingMode, OptBuf buf) {
        if (order.side() == Side.BUY) {
            buf.add("\n-------------------- ").add(tradingMode).add(" --------------------\n");
        }
        buf.add(order.toString()).add("\n");
        return buf;
    }

    static /* primitive */ /* value */ record CumPnl(
            double net, double gross, double fees) {
        static final CumPnl NO_TRADES = new CumPnl(0.0d, 0.0d, 0.0d);

        CumPnl withChg(double netChg, double grossChg, double feesChg) {
            return new CumPnl(net + netChg, gross + grossChg, fees + feesChg);
        }
    }

    OptBuf logEntry(CxOrders orders, CxdBookAnalysis cxdBook, MktDataBook book,
            Fees fees, double minSigQty, OptBuf buf) {
        var df = ThreadLocalFormat.with8SigDigits();
        double toBuyQty = orders.buy().qty().qty();
        double botQty = 0.0d, botVal = 0.0d;
        var asksBuf = new StringBuilder();
        asksBuf.append("ASKS[");
        for (CxdBookLvl lvl : cxdBook.cxdAskLvls()) {
            if (orders.buy().px().px() < lvl.px().px()) {
                break;
            }
            double qtyAvail = lvl.qty().qty();
            double qtyToUse = Math.min(qtyAvail, toBuyQty);
            botQty += qtyToUse;
            double lvlPx = lvl.px().px();
            botVal += (qtyToUse * lvlPx);
            toBuyQty -= qtyToUse;
            asksBuf.append(df.format(qtyToUse)).append(" @ ").append(df.format(lvlPx)).append(", ");
            if (toBuyQty < minSigQty) {
                break;
            }
        }
        asksBuf.append("]");

        double toSellQty = orders.sell().qty().qty();
        double sldQty = 0.0d, sldVal = 0.0d;
        var bidsBuf = new StringBuilder();
        bidsBuf.append("BIDS[");
        for (CxdBookLvl lvl : cxdBook.cxdBidLvls()) {
            if (orders.sell().px().px() > lvl.px().px()) {
                break;
            }
            double qtyAvail = lvl.qty().qty();
            double qtyToUse = Math.min(qtyAvail, toSellQty);
            sldQty += qtyToUse;
            double lvlPx = lvl.px().px();
            sldVal += (qtyToUse * lvlPx);
            toSellQty -= qtyToUse;
            bidsBuf.append(df.format(qtyToUse)).append(" @ ").append(df.format(lvlPx)).append(", ");
            if (toSellQty < minSigQty) {
                break;
            }
        }
        bidsBuf.append("]");

        double feesChg = fees.feesChg(botQty + sldQty, botVal + sldVal);
        double grossPnl = sldVal - botVal;
        double netPnl = grossPnl - feesChg;

        cumPnl = cumPnl.withChg(netPnl, grossPnl, feesChg);

        final var df4 = ThreadLocalFormat.with4SigDigits();

        buf.add("THEO PNL: net=").add(df4.format(netPnl))
            .add(", gross=").add(df4.format(grossPnl))
            .add(", fees=").add(df4.format(feesChg)).add('\n');
        buf.add("CUM THEO PNL: net=").add(df4.format(cumPnl.net()))
            .add(", gross=").add(df4.format(cumPnl.gross()))
            .add(", fees=").add(df4.format(cumPnl.fees())).add('\n');
        buf.add(bidsBuf).add('\n');
        buf.add(asksBuf).add('\n');
        buf.add(book.print(2)).add('\n');
        buf.add("-------------------------------------------------\n");
        return buf;
    }
}
