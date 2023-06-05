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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.abissell.logutil.EventLog;
import com.abissell.logutil.Log;
import com.abissell.cempaka.util.DstSet;
import com.abissell.fixbridge.ExecType;
import com.abissell.fixbridge.ParsedFixMsg;
import com.abissell.fixbridge.Tradeable;

public final class ExecLedgers<T extends Tradeable> {
    private final Map<T, ExecLedger> ledgers;
    private final Function<ParsedFixMsg, T> tradeableExtractor;

    public ExecLedgers(
            Map<T, ExecLedger> ledgersMap,
            Function<ParsedFixMsg, T> tradeableExtractor,
            Collection<T> tradeables,
            Function<T, Constraints> constraints,
            Fees fees) {
        this.ledgers = ledgersMap;
        this.tradeableExtractor = tradeableExtractor;
        tradeables.forEach(t -> ledgers.put(t, new ExecLedger(t, constraints.apply(t), fees)));
    }

    public ExecLedger get(T tradeable) {
        return ledgers.get(tradeable);
    }

    public ExecLedger handleExecReport(ParsedFixMsg parsedMsg, EventLog<DstSet> buf) {
        var tradeable = tradeableExtractor.apply(parsedMsg);
        var ledger = ledgers.get(tradeable);
        var execType = ledger.handleExecReport(parsedMsg, buf);
        if (execType == ExecType.PARTIAL_FILL || execType == ExecType.FILL) {
            ledgers.values().forEach(l -> {
                if (!isNetFlat(l) && !l.tradeable.equals(tradeable)) {
                    buf.to(DstSet.EXEC, Log.WARN)
                        .add("\n").add(l.tradeable).add(" ").add(l.pnl());
                }
            });
        }
        return ledger;
    }

    public Pnl sumPnls(Predicate<T> include) {
        double netPnl = 0.0d, highWatermark = 0.0d, netQty = 0.0d, grossPnl = 0.0d, fees = 0.0d,
        botQty = 0.0d, botVal = 0.0d, sldQty = 0.0d, sldVal = 0.0d;
        for (var entry : ledgers.entrySet()) {
            if (include.test(entry.getKey())) {
                var pnl = entry.getValue().pnl();
                netPnl += pnl.netPnl();
                highWatermark += pnl.highWatermark();
                netQty += pnl.netQty();
                grossPnl += pnl.grossPnl();
                fees += pnl.fees();
                botQty += pnl.botQty();
                botVal += pnl.botVal();
                sldQty += pnl.sldQty();
                sldVal += pnl.sldVal();
            }
        }
        return new Pnl(netPnl, highWatermark, netQty, grossPnl, fees,
                botQty, botVal, sldQty, sldVal);
    }

    public int fillableOrdersFor(T tradeable) {
        return ledgers.get(tradeable).fillableOrders().size();
    }

    public int allFillableOrdersCount() {
        int count = 0;
        for (var ledger : ledgers.values()) {
            count += ledger.fillableOrders().size();
        }
        return count;
    }

    public void startNewReplay() {
        ledgers.values().forEach(ledger -> ledger.startNewReplay());
    }

    public Map<T, Pnl> getNonflatPnls() {
        // TODO: Could be an EnumMap?
        var map = new HashMap<T, Pnl>();
        ledgers.forEach((t, ledger) -> {
            if (!isNetFlat(ledger)) {
                map.put(t, ledger.pnl());
            }
        });
        return map;
    }

    public Map<T, Pnl> getPnls() {
        return ledgers.entrySet().stream()
            .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().pnl()
                    )
            );
    }


    private boolean isNetFlat(ExecLedger ledger) {
        return Math.abs(ledger.pnl().netQty())
                <= ledger.constraints.minSigQty();
    }
}
