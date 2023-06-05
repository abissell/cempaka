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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.abissell.logutil.EventLog;
import com.abissell.logutil.Log;
import com.abissell.cempaka.util.DstSet;
import com.abissell.fixbridge.AvgPx;
import com.abissell.cempaka.orderid.ClOrdrID;
import com.abissell.fixbridge.CumQty;
import com.abissell.fixbridge.ExecType;
import com.abissell.fixbridge.FixField;
import com.abissell.fixbridge.LastPx;
import com.abissell.fixbridge.LastShares;
import com.abissell.fixbridge.MsgType;
import com.abissell.fixbridge.OrdStatus;
import com.abissell.fixbridge.Order;
import com.abissell.fixbridge.OrdrCxlReq;
import com.abissell.fixbridge.OrigClOrdrID;
import com.abissell.fixbridge.ParsedFixMsg;
import com.abissell.fixbridge.Side;
import com.abissell.fixbridge.Tradeable;

public final class ExecLedger {
    public final Tradeable tradeable;
    public final Constraints constraints;
    private final Fees fees;
    private final Map<ClOrdrID, OrdWithStatus> ordersById = new HashMap<>(4096);
    private final Set<ClOrdrID> fillableOrders = new HashSet<>(16);
    private final Set<ClOrdrID> rejectedOrders = new HashSet<>(16);
    // volatile since may be read from other threads
    private volatile Pnl pnl = Pnl.NO_TRADES;
    private volatile Pnl cumReplayPnl = Pnl.NO_TRADES;

    public ExecLedger(Tradeable tradeable, Constraints constraints, Fees fees) {
        this.tradeable = tradeable;
        this.constraints = constraints;
        this.fees = fees;
    }

    public Pnl pnl() {
        return pnl;
    }

    public Set<ClOrdrID> fillableOrders() {
        return fillableOrders;
    }

    public Set<ClOrdrID> rejectedOrders() {
        return rejectedOrders;
    }

    public void startNewReplay() {
        cumReplayPnl = Pnl.NO_TRADES;
    }

    public void handleReplay(ParsedFixMsg execReport) {
        final var execType = (ExecType) execReport.get(FixField.EXEC_TYPE);
        final var side = (Side) execReport.get(FixField.SIDE);
        switch (execType) {
            case PARTIAL_FILL, FILL -> {
                var qtyChg = (LastShares) execReport.get(FixField.LAST_SHARES);
                var lastPx = (LastPx) execReport.get(FixField.LAST_PX);
                var valChg = qtyChg.qty() * lastPx.px();
                var feesChg = fees.feesChg(qtyChg.qty(), valChg);
                var prevPnl = cumReplayPnl;
                cumReplayPnl = prevPnl.withChange(side, feesChg, qtyChg.qty(),
                        valChg, constraints);
                Log.INFO.to(DstSet.EXEC, "-------------------- REPLAYED FILL --------------------");
                Log.INFO.to(DstSet.EXEC, execReport.msg());
                Log.INFO.to(DstSet.EXEC, "\nREPLAY " + tradeable + ": " + cumReplayPnl);
                Log.INFO.to(DstSet.EXEC, "-------------------------------------------------------\n");
            }
            default -> { }
        }
    }

    public ExecType handleExecReport(ParsedFixMsg execReport, EventLog<DstSet> buf) {
        final var execType = (ExecType) execReport.get(FixField.EXEC_TYPE);

        if (execReport.isPossDup()) {
            handleReplay(execReport);
            return execType;
        }

        var fields = execReport.msg();

        var msgType = (MsgType) fields.get(FixField.MSG_TYPE);
        if (msgType != MsgType.EXECUTION_REPORT) {
            throw new IllegalArgumentException("Do not know how to handle message of type " + msgType + ", msg: " + execReport);
        }

        var symbol = (Tradeable) execReport.get(FixField.SYMBOL);
        if (!symbol.equals(tradeable)) {
            throw new IllegalArgumentException("Passed execReport=" + execReport + " to incorrect OrderExecLedger for tradable=" + tradeable);
        }

        ClOrdrID clOrdrID = (ClOrdrID) execReport.get(FixField.CL_ORD_ID);
        if (execType == ExecType.CANCELED) {
            var origClOrdrID = (OrigClOrdrID) execReport.get(FixField.ORIG_CL_ORD_ID);
            if (origClOrdrID != null) {
                clOrdrID = ClOrdrID.from(origClOrdrID);
            }
        }
        var ordWithStatus = ordersById.get(clOrdrID);
        if (ordWithStatus == null) {
            throw new IllegalStateException("Couldn't find ordWithStatus for clOrdrID=" + clOrdrID);
        }

        switch (execType) {
            case NEW -> {
                // move the order out of PENDING_NEW to NEW
                if (ordWithStatus.status() != OrdStatus.PENDING_NEW) {
                    Log.WARN.to(DstSet.APP_STD_OUT, "For clOrdrID=" + clOrdrID + " saw unexpected change to " + ordWithStatus + " from execReport " + execReport);
                }
                ordersById.put(clOrdrID, ordWithStatus.withNewStatus(OrdStatus.NEW));
            }
            case PARTIAL_FILL, FILL -> {
                CumQty newCumQty = (CumQty) execReport.get(FixField.CUM_QTY);
                AvgPx newAvgPx = (AvgPx) execReport.get(FixField.AVG_PX);
                var qtyChg = (LastShares) execReport.get(FixField.LAST_SHARES);
                var lastPx = (LastPx) execReport.get(FixField.LAST_PX);
                var valChg = qtyChg.qty() * lastPx.px();
                var oldPnl = pnl;
                var feesChg = fees.feesChg(qtyChg.qty(), valChg);
                pnl = oldPnl.withChange(ordWithStatus.order().side(), feesChg,
                        qtyChg.qty(), valChg, constraints);
                OrdStatus newStatus;
                switch (execType) {
                    case PARTIAL_FILL -> newStatus = OrdStatus.PARTIALLY_FILLED;
                    case FILL -> {
                        newStatus = OrdStatus.FILLED;
                        fillableOrders.remove(clOrdrID);
                    }
                    default -> throw new IllegalArgumentException("" + execType);
                }
                ordersById.put(clOrdrID, ordWithStatus.withNewStatusAndFill(newStatus, new Fill(newCumQty, newAvgPx)));
                buf.to(DstSet.APP_STD_OUT_EXEC, Log.WARN).add("\n").add(tradeable).add(" ").add(pnl);
            }
            case CANCELED -> {
                ordersById.put(clOrdrID, ordWithStatus.withNewStatus(OrdStatus.CANCELED));
                fillableOrders.remove(clOrdrID);
            }
            case REJECTED -> {
                ordersById.put(clOrdrID, ordWithStatus.withNewStatus(OrdStatus.REJECTED));
                fillableOrders.remove(clOrdrID);
                rejectedOrders.add(clOrdrID);
            }
            default -> throw new IllegalStateException("Cannot handle execReport " + execReport);
        }

        return execType;
    }

    public Set<ClOrdrID> addPendingNewOrder(Order order) {
        var orderTradeable = order.tradeable();
        if (!orderTradeable.equals(tradeable)) {
            throw new IllegalArgumentException("For ExecLedger for tradeable=" + tradeable + " passed in order " + order);
        }
        var clOrdrID = (ClOrdrID) order.id();
        ordersById.put(clOrdrID, OrdWithStatus.forPendingNewOrder(order));
        fillableOrders.add(clOrdrID);
        return fillableOrders;
    }

    public Set<ClOrdrID> handleOrdrCxlReq(OrdrCxlReq cxlReq) {
        var origOrder = cxlReq.order();
        var orderTradeable = origOrder.tradeable();
        if (!orderTradeable.equals(tradeable)) {
            throw new IllegalArgumentException("For ExecLedger for tradeable=" + tradeable + " passed in cxl request on order " + origOrder);
        }
        var origClOrdrID = (ClOrdrID) cxlReq.order().id();
        var ordWithStatus = ordersById.get(origClOrdrID);
        ordersById.put(origClOrdrID, ordWithStatus.withNewStatus(OrdStatus.PENDING_CANCEL));
        return fillableOrders;
    }

    public Set<ClOrdrID> forceCxlOrder(ClOrdrID id, Order order) {
        var orderTradeable = order.tradeable();
        if (!orderTradeable.equals(tradeable)) {
            throw new IllegalArgumentException("For ExecLedger for tradeable=" + tradeable + " passed in order " + order);
        }

        var ordWithStatus = ordersById.get(id);
        if (ordWithStatus == null) {
            throw new IllegalStateException("Couldn't find ordWithStatus for clOrdrID=" + id);
        }

        ordersById.put(id, ordWithStatus.withNewStatus(OrdStatus.CANCELED));
        fillableOrders.remove(id);
        return fillableOrders;
    }
}
