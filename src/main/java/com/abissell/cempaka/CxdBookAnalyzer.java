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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.abissell.cempaka.data.*;
import com.abissell.fixbridge.OrderQty;
import com.abissell.fixbridge.Side;

public final class CxdBookAnalyzer {
    private final Fees fees;

    CxdBookAnalyzer(Fees fees) {
        this.fees = fees;
    }

    public CxdBookAnalysis analyze(MktDataBook book, double minSigQty) {
        var bids = book.get(Side.BUY);
        if (bids.isEmpty()) {
            return CxdBookAnalysis.NOT_CXD;
        }
        var asks = book.get(Side.SELL);
        if (asks.isEmpty()) {
            return CxdBookAnalysis.NOT_CXD;
        }

        var lowestAskLvl = asks.get(0).level();
        final int lowestCxdBidIdx = findIdxOfLowestCxdBid(bids, lowestAskLvl);
        if (lowestCxdBidIdx < 0) {
            return CxdBookAnalysis.NOT_CXD;
        }

        var crossedBids = new ArrayList<CxdBookLvl>(bids.size());
        var crossedAsks = new ArrayList<CxdBookLvl>(asks.size());
        int bidIdx = lowestCxdBidIdx;
        int askIdx = 0;
        double bidVal = 0.0d, askVal = 0.0d;
        double askQtyConsumed = 0.0d;
        while (bidIdx >= 0) {
            var bid = bids.get(bidIdx);
            var bidLvl = bid.level();
            var bidPx = bidLvl.px().px();
            bidPx = fees.adjustedPx(Side.BUY, bidPx);
            var bidQtyAvail = bidLvl.qty().qty();

            while (askIdx < asks.size()) {
                var ask = asks.get(askIdx);
                var askLvl = ask.level();
                var askPx = askLvl.px().px();
                askPx = fees.adjustedPx(Side.SELL, askPx);
                if (askPx >= bidPx) {
                    // bump to the next higher bid (if any)
                    bidIdx--;
                    break;
                }

                var askQty = askLvl.qty().qty() - askQtyConsumed;
                boolean consumedBid = false, consumedAsk = false;
                if (bidQtyAvail >= askQty) {
                    var qty = new OrderQty(askQty);
                    crossedBids.add(new CxdBookLvl(qty, bid));
                    bidVal += qty.qty() * bidPx;
                    crossedAsks.add(new CxdBookLvl(qty, ask));
                    askVal += qty.qty() * askPx;
                    consumedAsk = true;
                    bidQtyAvail -= askQty;
                    if (bidQtyAvail < minSigQty) {
                        consumedBid = true;
                    }
                } else {
                    var qty = new OrderQty(bidQtyAvail);
                    crossedBids.add(new CxdBookLvl(qty, bid));
                    bidVal += qty.qty() * bidPx;
                    crossedAsks.add(new CxdBookLvl(qty, ask));
                    askVal += qty.qty() * askPx;
                    consumedBid = true;
                    askQtyConsumed += bidQtyAvail;
                    if (askLvl.qty().qty() - askQtyConsumed < minSigQty) {
                        consumedAsk = true;
                    }
                }

                if (consumedAsk) {
                    askQtyConsumed = 0.0d;
                    askIdx++;
                }

                if (consumedBid) {
                    bidIdx--;
                    break;
                }
            }

            if (askIdx >= asks.size()) {
                break;
            }
        }

        Collections.reverse(crossedBids);
        return new CxdBookAnalysis(crossedBids, crossedAsks, bidVal - askVal);
    }

    private int findIdxOfLowestCxdBid(List<SidePxQtyUpdate> bids, SidePxQty lowestAskLvl) {
        var lowestAskPx = lowestAskLvl.px().px();
        int lowestCxdBidIdx = -1;
        for (int i = 0; i < bids.size(); i++) {
            var bidUpdate = bids.get(i);
            if (bidUpdate.level().px().px() > lowestAskPx) {
                lowestCxdBidIdx = i;
            }
        }

        return lowestCxdBidIdx;
    }
}
