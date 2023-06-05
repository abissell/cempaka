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

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.abissell.fixbridge.FixField;
import com.abissell.fixbridge.MDEntries;
import com.abissell.fixbridge.MsgType;
import com.abissell.fixbridge.OrderQty;
import com.abissell.fixbridge.ParsedFixMsg;
import com.abissell.fixbridge.Price;
import com.abissell.fixbridge.SendingTime;
import com.abissell.fixbridge.Side;
import com.abissell.fixbridge.Tradeable;

public final class MktDataBook {
    public final Tradeable tradeable;
    private final EnumMap<Side, List<SidePxQtyUpdate>> book;

    public MktDataBook(Tradeable tradeable) {
        this.tradeable = tradeable;
        this.book = new EnumMap<>(Side.class);
        book.put(Side.BUY, Collections.emptyList());
        book.put(Side.SELL, Collections.emptyList());
    }

    public List<SidePxQtyUpdate> get(Side side) {
        return book.get(side);
    }

    public EnumMap<Side, List<SidePxQtyUpdate>> updateBook(ParsedFixMsg parsedMsg) {
        if (parsedMsg.isPossDup()) {
            return book;
        }

        var fields = parsedMsg.msg();

        var msgType = (MsgType) fields.get(FixField.MSG_TYPE);
        if (msgType != MsgType.MARKET_DATA_SNAPSHOT_FULL_REFRESH) {
            throw new IllegalArgumentException("Do not know how to handle message of type " + msgType + ", msg: " + parsedMsg);
        }

        var symbol = (Tradeable) fields.get(FixField.SYMBOL);
        if (!symbol.equals(tradeable)) {
            throw new IllegalArgumentException("Tried to update marketDataBook for " + tradeable + " with updates on symbol " + symbol);
        }

        var sendingTime = (SendingTime) fields.get(FixField.SENDING_TIME);

        var mdEntries = (MDEntries) fields.get(FixField.MD_ENTRIES);
        var newBidUpdateList = new ArrayList<SidePxQtyUpdate>();
        var newAskUpdateList = new ArrayList<SidePxQtyUpdate>();
        mdEntries.entries().forEach(entry -> {
            ArrayList<SidePxQtyUpdate> newUpdateList;
            Side newUpdateSide;
            switch (entry.type()) {
                case BID -> {
                    newUpdateList = newBidUpdateList;
                    newUpdateSide = Side.BUY;
                }
                case OFFER -> {
                    newUpdateList = newAskUpdateList;
                    newUpdateSide = Side.SELL;
                }
                default -> throw new IllegalArgumentException("" + entry.type());
            }

            // TODO: Could recycle SidePxQtys from earlier book entries if present
            var newUpdate = new SidePxQtyUpdate(
                    new SidePxQty(
                        newUpdateSide,
                        new Price(entry.px().px()),
                        new OrderQty(entry.size().size())
                    ),
                    sendingTime.sendingTime(),
                    parsedMsg.recvdTime()
                );

            newUpdateList.add(newUpdate);
        });

        return updateBook(newBidUpdateList, newAskUpdateList);
    }

    public EnumMap<Side, List<SidePxQtyUpdate>> updateBook(List<SidePxQtyUpdate> newBidUpdateList, List<SidePxQtyUpdate> newAskUpdateList) {
        Collections.sort(newBidUpdateList);
        book.put(Side.BUY, newBidUpdateList);
        Collections.sort(newAskUpdateList);
        book.put(Side.SELL, newAskUpdateList);
        return book;
    }

    public String print(int maxUncxdLvls) {
        List<SidePxQtyUpdate> bidUpdates = book.getOrDefault(Side.BUY, Collections.emptyList());
        List<SidePxQtyUpdate> askUpdates = book.getOrDefault(Side.SELL, Collections.emptyList());
        Set<Price> pxs = new HashSet<>();
        int maxBidQtyChars = 0, maxBidPxChars = 0, maxAskPxChars = 0, maxAskQtyChars = 0;
        double highestBidPx = Double.MIN_VALUE + 1.0d, lowestAskPx = Double.MAX_VALUE - 1.0d;
        for (var u : bidUpdates) {
            maxBidQtyChars = Math.max(maxBidQtyChars, u.level().qty().decimalString().length());
            maxBidPxChars = Math.max(maxBidPxChars, u.level().px().decimalString().length());
            var px = u.level().px();
            pxs.add(px);
            highestBidPx = Math.max(px.px(), highestBidPx);
        }
        for (var u : askUpdates) {
            maxAskQtyChars = Math.max(maxAskQtyChars, u.level().qty().decimalString().length());
            maxAskPxChars = Math.max(maxAskPxChars, u.level().px().decimalString().length());
            var px = u.level().px();
            pxs.add(px);
            lowestAskPx = Math.min(px.px(), lowestAskPx);
        }

        maxBidQtyChars = Math.max(maxBidQtyChars, 3);
        maxBidPxChars = Math.max(maxBidPxChars, 3);
        maxAskPxChars = Math.max(maxAskPxChars, 3);
        maxAskQtyChars = Math.max(maxAskQtyChars, 3);

        List<Price> sortedPxs = new ArrayList<>(pxs);
        Collections.sort(sortedPxs, (px1, px2) -> {
            if (px1.px() > px2.px()) {
                return -1;
            } else if (px1.px() == px2.px()) {
                return 0;
            } else {
                return 1;
            }
        });

        var buf = new StringBuilder();
        buf.append("MKT_DATA_BOOK ").append(tradeable).append(":\n");
        int extraCharsWidth = "[ ".length() + " @ ".length() + " ]".length();
        int bidSectionWidth = extraCharsWidth + maxBidQtyChars + maxBidPxChars;
        int bidHeaderLeading = ((bidSectionWidth - "BIDS".length()) / 2) + "BIDS".length();
        int askSectionWidth = extraCharsWidth + maxAskQtyChars + maxAskPxChars;
        int askHeaderLeading = ((askSectionWidth - "ASKS".length()) / 2) + "ASKS".length();
        String bidsHeaderLeft = String.format("%1$" + bidHeaderLeading + "s", "BIDS");
        String bidsHeader = String.format("%1$-" + bidSectionWidth + "s", bidsHeaderLeft);
        String asksHeaderLeft = String.format("%1$" + askHeaderLeading + "s", "ASKS");
        String asksHeader = String.format("%1$-" + askSectionWidth + "s", asksHeaderLeft);
        buf.append(bidsHeader).append(" | ").append(asksHeader);

        double highestAskToAppend = lowestAskPx;
        int uncxdAsksAppended = 0;
        for (var askUpdate : askUpdates) {
            var px = askUpdate.level().px().px();
            if (px > highestBidPx) {
                uncxdAsksAppended++;
                highestAskToAppend = px;
            }

            if (uncxdAsksAppended >= maxUncxdLvls) {
                break;
            }
        }

        double lowestBidToAppend = highestBidPx;
        int uncxdBidsAppended = 0;
        for (var bidUpdate : bidUpdates) {
            var px = bidUpdate.level().px().px();
            if (px < lowestAskPx) {
                uncxdBidsAppended++;
                lowestBidToAppend = px;
            }

            if (uncxdBidsAppended >= maxUncxdLvls) {
                break;
            }
        }

        for (Price px : sortedPxs) {
            Optional<SidePxQtyUpdate> bidUpdate = bidUpdates.stream()
                .filter(u -> u.level().px().equals(px))
                .findAny();
            var appendBid = bidUpdate.isPresent() && bidUpdate.get().px().px() >= lowestBidToAppend;
            String bidSide;
            if (appendBid) {
                var u = bidUpdate.get();
                var qtyStr = String.format("%1$" + maxBidQtyChars + "s", u.level().qty().decimalString());
                var pxStr = String.format("%1$" + maxBidPxChars + "s", u.level().px().decimalString());
                bidSide = "[ " + qtyStr + " @ " + pxStr + " ]";
            } else {
                bidSide = String.format("%1$" + bidSectionWidth + "s", "");
            }

            Optional<SidePxQtyUpdate> askUpdate = askUpdates.stream()
                .filter(u -> u.level().px().equals(px))
                .findAny();
            var appendAsk = askUpdate.isPresent() && askUpdate.get().px().px() <= highestAskToAppend;
            String askSide;
            if (appendAsk) {
                var u = askUpdate.get();
                var pxStr = String.format("%1$" + maxAskPxChars + "s", u.level().px().decimalString());
                var qtyStr = String.format("%1$" + maxAskQtyChars + "s", u.level().qty().decimalString());
                askSide = "[ " + pxStr + " @ " + qtyStr + " ]";
            } else {
                askSide = String.format("%1$" + askSectionWidth + "s", "");
            }

            if (!bidSide.isBlank() || !askSide.isBlank()) {
                buf.append('\n').append(bidSide).append(" | ").append(askSide);
            }
        }

        return buf.toString();
    }

    // TODO: Decide how/whether to incorporate send & recv times
    @Override
    public String toString() {
        var buf = new StringBuilder();
        buf.append("MKT_DATA_BOOK ").append(tradeable).append(":");
        List<SidePxQtyUpdate> bidUpdates = book.getOrDefault(Side.BUY, Collections.emptyList());
        List<SidePxQtyUpdate> askUpdates = book.getOrDefault(Side.SELL, Collections.emptyList());
        for (var update : bidUpdates) {
            buf.append("[ ").append(update.level().qty().qty()).append(" @ ").append(update.level().px().px()).append(" ] ");
        }
        buf.append("<-> ");
        for (var update : askUpdates) {
            buf.append("[ ").append(update.level().px().px()).append(" @ ").append(update.level().qty().qty()).append(" ] ");
        }
        buf.append("}");
        return buf.toString();
    }
}
