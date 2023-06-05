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

import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Supplier;

import com.abissell.javautil.io.IO;
import com.abissell.javautil.io.YesNo;
import com.abissell.javautil.math.DoubleRounder;
import com.abissell.javautil.rusty.Opt;
import com.abissell.javautil.rusty.Result;
import com.abissell.logutil.EventLog;
import com.abissell.logutil.Log;
import com.abissell.logutil.LogBuf;
import com.abissell.logutil.OptBuf;
import com.abissell.cempaka.data.Constraints;
import com.abissell.cempaka.data.CxOrders;
import com.abissell.cempaka.data.CxdBookAnalysis;
import com.abissell.cempaka.data.CxdBookLvl;
import com.abissell.cempaka.data.ExecLedger;
import com.abissell.cempaka.data.ExecLedgers;
import com.abissell.cempaka.data.Fees;
import com.abissell.cempaka.data.FixErr;
import com.abissell.cempaka.data.MktDataBook;
import com.abissell.cempaka.data.MktDataBooks;
import com.abissell.cempaka.data.Pnl;
import com.abissell.cempaka.data.QFJRes;
import com.abissell.cempaka.data.RiskLimit;
import com.abissell.cempaka.data.TradingMode;
import com.abissell.cempaka.fix.MsgQueue;
import com.abissell.cempaka.fix.QFJExecSession;
import com.abissell.cempaka.fix.QFJMktDataSession;
import com.abissell.cempaka.util.CempakaLog;
import com.abissell.cempaka.util.DstSet;
import com.abissell.cempaka.data.CcyPair;
import com.abissell.cempaka.orderid.ClOrdrID;
import com.abissell.fixbridge.ExecSessionBridge;
import com.abissell.fixbridge.FixField;
import com.abissell.fixbridge.FixFieldVal;
import com.abissell.fixbridge.MDReqID;
import com.abissell.fixbridge.MarketDepth;
import com.abissell.fixbridge.MinQty;
import com.abissell.fixbridge.MktDataSessionBridge;
import com.abissell.fixbridge.MktDataSubscriptionReq;
import com.abissell.fixbridge.MsgType;
import com.abissell.fixbridge.Order;
import com.abissell.fixbridge.OrderQty;
import com.abissell.fixbridge.ParsedFixMsg;
import com.abissell.fixbridge.Price;
import com.abissell.fixbridge.Side;
import com.abissell.fixbridge.SubscriptionRequestType;
import com.abissell.fixbridge.TimeInForce;
import com.abissell.fixbridge.TransactTime;

import quickfix.ConfigError;
import quickfix.SessionSettings;

public final class CcyPairTrader {

    private static final MDReqID MD_REQ_ID = new MDReqID("t");

    // TODO: Genericize these and their result types
    public final MktDataSessionBridge<QFJRes, FixErr> mktDataSession;
    private static final int MKT_DATA_QUEUE_SIZE = 1000;
    private final MsgQueue<ParsedFixMsg> mktDataQueue = new MsgQueue<>(MKT_DATA_QUEUE_SIZE);
    public final ExecSessionBridge<QFJRes, FixErr> execSession;
    private static final int EXEC_QUEUE_SIZE = 200;
    private final MsgQueue<ParsedFixMsg> execQueue = new MsgQueue<>(EXEC_QUEUE_SIZE);
    private final MsgQueue<Order> manualOrderQueue = new MsgQueue<>(10);
    private final List<MsgQueue<?>> queueList = List.of(mktDataQueue, execQueue, manualOrderQueue);

    private final MktDataBooks<CcyPair> mktDataBooks;
    private final ExecLedgers<CcyPair> execLedgers;
    private final Fees fees;
    public final CcyPairRiskLimits riskLimits;

    private volatile EnumSet<CcyPair> mktDataSubscribedPairs = EnumSet.noneOf(CcyPair.class);
    private volatile EnumSet<CcyPair> activeTradingPairs = EnumSet.noneOf(CcyPair.class);
    private final CxdBookAnalyzer cxdBookAnalyzer;
    private final CxdBookEntryLogger entryLogger = new CxdBookEntryLogger();

    private final Map<ClOrdrID, Order> dryRunOrders = new HashMap<>();
    private static final Duration DRY_RUN_EXPIRY = Duration.ofSeconds(10L);

    private static final Duration RISK_LIMIT_LOG_INTERVAL = Duration.ofSeconds(10L);
    private LocalDateTime lastRiskLimitLogTime;

    private final DoubleRounder doubleRounder = createDoubleRounder();

    private final LogBuf<DstSet> logBuf = LogBuf.create(DstSet.class);

    private final ExecutorService loopRunner = Executors.newSingleThreadExecutor();

    public CcyPairTrader(Supplier<LocalDateTime> timestampSrc, Fees fees,
            String fileSuffix) throws ConfigError {
        this.mktDataSession = new QFJMktDataSession(new SessionSettings("marketdatasession" + fileSuffix), mktDataQueue, timestampSrc);
        this.execSession = new QFJExecSession(new SessionSettings("executionsession" + fileSuffix), execQueue, timestampSrc);

        Function<ParsedFixMsg, CcyPair> tradeableExtractor =
            msg -> (CcyPair) msg.msg().get(FixField.SYMBOL);
        this.mktDataBooks = new MktDataBooks<>(
                new EnumMap<>(CcyPair.class),
                tradeableExtractor,
                EnumSet.allOf(CcyPair.class));
        this.execLedgers = new ExecLedgers<>(
                new EnumMap<>(CcyPair.class),
                tradeableExtractor,
                EnumSet.allOf(CcyPair.class),
                t -> Constraints.on(t.ccy1),
                fees);
        this.fees = fees;
        this.riskLimits = new CcyPairRiskLimits(timestampSrc.get());
        this.cxdBookAnalyzer = new CxdBookAnalyzer(fees);
        this.lastRiskLimitLogTime = timestampSrc.get();

        loopRunner.submit(this::loop);
    }

    private DoubleRounder createDoubleRounder() {
        final var places = new HashSet<Integer>();
        Constraints.SPECD_CCYS.forEach(ccy -> {
            var constraints = Constraints.on(ccy);
            places.add(constraints.qtyMaxDecimalPts());
            places.add(constraints.pxMaxDecimalPts());
        });
        return new DoubleRounder(places);
    }

    public EnumSet<CcyPair> subscribeToMktData(Collection<CcyPair> ccyPairs) {
        if (mktDataSession.loggedOn()) {
            if (!mktDataSubscribedPairs.isEmpty()) {
                Log.ERROR.to(DstSet.APP_STD_OUT, """
                    Already have an active market data subscription.
                    Unsubscribe first and then re-subscribe to the
                    new set of CcyPairs.
                    """);
                return EnumSet.noneOf(CcyPair.class);
            }

            Log.INFO.to(DstSet.APP_STD_OUT, 
                    "Enter the max number of levels to subscribe, 1 thru 10, 0 for uncapped:\n");
            var levels = Integer.parseInt(System.console().readLine());
            levels = Math.max(levels, 0);
            if (levels > 10) {
                throw new IllegalArgumentException("Cannot request max levels " + levels);
            }
            Log.INFO.to(DstSet.APP_STD_OUT, "Enter the min qty to subscribe as decimal, 0.0 for no min:\n");
            var minQtyDouble = Double.parseDouble(System.console().readLine());
            Opt<MinQty> minQty;
            if (minQtyDouble > 0.0d) {
                // TODO: Allow different minSigQtys for the different base currencies
                if (minQtyDouble < Constraints.getLowestMinSigQty()) {
                    var prompt = "Subscribe to minQty=" + minQtyDouble +
                            " less than minimum significant qty " + Constraints.getLowestMinSigQty() + "?";
                    Log.INFO.to(DstSet.APP_STD_OUT, prompt + " (Y/n) ");
                    var proceed = IO.readYesOrNoFromConsole();
                    if (proceed.isNone() || proceed.get() != YesNo.YES) {
                        Log.ERROR.to(DstSet.APP_STD_OUT, "Canceling market data subscription request!");
                        return EnumSet.noneOf(CcyPair.class);
                    }
                }
                minQty = Opt.of(new MinQty(minQtyDouble));
            } else {
                minQty = Opt.none();
            }
            var request = new MktDataSubscriptionReq(
                    MD_REQ_ID,
                    SubscriptionRequestType.SUBSCRIBE,
                    new MarketDepth(levels),
                    minQty
            );
            var result = mktDataSession.subscribe(request, ccyPairs);
            if (result.map(QFJRes::success).orElse(false)) {
                mktDataSubscribedPairs = EnumSet.copyOf(ccyPairs);
                Log.INFO.to(DstSet.APP_STD_OUT, "Session subscribeToMktData() call completed.");
                Log.INFO.to(DstSet.APP_STD_OUT, "subscribedPairs are now: " + mktDataSubscribedPairs);
                return mktDataSubscribedPairs;
            } else {
                Log.ERROR.to(DstSet.APP_STD_OUT, "Request to subscribe to market data failed!");
                return EnumSet.noneOf(CcyPair.class);
            }
        } else {
            Log.ERROR.to(DstSet.APP_STD_OUT, "Could not subscribe, session was not logged on");
            return EnumSet.noneOf(CcyPair.class);
        }
    }

    public void unsubscribeFromMktData() {
        Log.INFO.to(DstSet.APP_STD_OUT, "Trying to unsubscribe to market data updates");

        if (!activeTradingPairs.isEmpty()) {
            Log.ERROR.to(DstSet.APP_STD_OUT, "Unsubscribed from mkt data while pairs were active for trading: " + activeTradingPairs);
            haltAndDeactivate();
        }

        if (mktDataSession.loggedOn()) {
            var result = mktDataSession.unsubscribe(MD_REQ_ID, mktDataSubscribedPairs);
            if (result.map(QFJRes::success).orElse(false)) {
                mktDataSubscribedPairs.clear();
            }
            Log.INFO.to(DstSet.APP_STD_OUT, "Session disableMarketDataUpdates() call completed.");
            Log.INFO.to(DstSet.APP_STD_OUT, "subscribedPairs are now: " + mktDataSubscribedPairs);
        } else {
            Log.ERROR.to(DstSet.APP_STD_OUT, "Could not disable with session, no session was logged on");
        }
    }

    /*
     * @return The set of newly-activated CcyPairs
     */
    public EnumSet<CcyPair> activatePairs(Collection<CcyPair> pairs) {
        var pairsToActivate = EnumSet.copyOf(pairs);
        pairs.forEach(pair -> {
            if (!mktDataSubscribedPairs.contains(pair)) {
                Log.ERROR.to(DstSet.APP_STD_OUT, 
                        "Pair " + pair + " was not subscribed to mkt data! Will not activate!");
                pairsToActivate.remove(pair);
            }
        });
        var newlyActivated = EnumSet.noneOf(CcyPair.class);
        var activated = EnumSet.copyOf(activeTradingPairs);
        pairsToActivate.forEach(pair -> {
            if (!activated.contains(pair)) {
                activated.add(pair);
                newlyActivated.add(pair);
            }
        });
        activeTradingPairs = activated;
        return newlyActivated;
    }

    public EnumSet<CcyPair> getActiveTradingPairs() {
        return EnumSet.copyOf(activeTradingPairs);
    }

    public void deactivatePairs(Collection<CcyPair> pairs) {
        var newPairs = EnumSet.copyOf(activeTradingPairs);
        pairs.forEach(pair -> {
            if (!newPairs.contains(pair)) {
                Log.WARN.to(DstSet.APP_STD_OUT, "Requested deactivation of pair " + pair + " which was not active");
            }
        });
        newPairs.removeAll(pairs);
        activeTradingPairs = newPairs;
        Log.INFO.to(DstSet.APP_STD_OUT, "Deactivated " + pairs + " from trading.");
    }

    public TradingMode haltAndDeactivate() {
        var prevMode = riskLimits.halt();
        deactivate();
        return prevMode;
    }

    public EnumSet<CcyPair> deactivate() {
        var wereActive = activeTradingPairs;
        activeTradingPairs = EnumSet.noneOf(CcyPair.class);
        Log.INFO.to(DstSet.APP_STD_OUT, "Deactivated " + wereActive + " from trading.");
        return wereActive;
    }

    private void loop() {
        var msgs = new ArrayList<ParsedFixMsg>(8);
        var mktDataMap = new EnumMap<CcyPair, ParsedFixMsg>(CcyPair.class);
        var manualOrders = new ArrayList<Order>(8);
        while (true) {
            try {
                executeLoop(msgs, mktDataMap, manualOrders);
            } catch (Throwable t) {
                riskLimits.halt();
                Log.ERROR.to(DstSet.APP_STD_OUT, "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                Log.ERROR.to(DstSet.APP_STD_OUT, "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                Log.ERROR.to(DstSet.APP_STD_OUT, "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                Log.ERROR.to(DstSet.APP_STD_OUT, " Main trading loop caught throwable! ");
                Log.ERROR.to(DstSet.APP_STD_OUT, " Throwable: " + t.toString());
                Log.ERROR.to(DstSet.APP_STD_OUT, " stack trace: " + Arrays.toString(t.getStackTrace()));
                Log.ERROR.to(DstSet.APP_STD_OUT, "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                Log.ERROR.to(DstSet.APP_STD_OUT, "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                Log.ERROR.to(DstSet.APP_STD_OUT, "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            }
        }
    }

    private void executeLoop(final List<ParsedFixMsg> msgs,
            final EnumMap<CcyPair, ParsedFixMsg> mktDataMap,
            final List<Order> manualOrders) {
        var drained = execQueue.drainTo(msgs);
        if (drained > 0) {
            for (var msg : msgs) {
                switch (msg.msgType()) {
                    case EXECUTION_REPORT -> onExecutionReport(msg);
                    case TRADING_SESSION_STATUS -> onExecTradingSessionStatus();
                    default -> Log.ERROR.to(DstSet.APP_STD_OUT, "Unsure how to handle msg in execQueue: " + msg);
                }
            }
        } else {
            drained = mktDataQueue.drainTo(msgs);
            if (drained > 0) {
                if (drained > 1) {
                    var coalesced = coalesceMktData(msgs, mktDataMap);
                    msgs.clear();
                    msgs.addAll(coalesced);
                }

                for (var msg : msgs) {
                    switch (msg.msgType()) {
                        case MARKET_DATA_SNAPSHOT_FULL_REFRESH -> onMktDataSnapshotFullRefresh(msg);
                        case TRADING_SESSION_STATUS -> onMktDataTradingSessionStatus();
                        default -> Log.ERROR.to(DstSet.APP_STD_OUT, "Unsure how to handle msg in mktDataQueue: " + msg);
                    }
                }
            } else {
                drained = manualOrderQueue.drainTo(manualOrders);
                if (drained > 0) {
                    manualOrders.forEach(order -> submitManualOrder(order));
                }
            }
        }

        msgs.clear();
        mktDataMap.clear();
        manualOrders.clear();
    }

    private static final int COALESCED_LIST_LENGTH = CcyPair.values().length * 2;
    // TODO: Should be possible to coalesce the list in place using removal by index
    private List<ParsedFixMsg> coalesceMktData(List<ParsedFixMsg> msgs,
            EnumMap<CcyPair, ParsedFixMsg> mktDataMap) {
        if (!mktDataMap.isEmpty()) {
            throw new IllegalStateException("mktDataMap should always be empty here!");
        }

        var coalesced = new ArrayList<ParsedFixMsg>(COALESCED_LIST_LENGTH);
        for (var msg : msgs) {
            if (msg.msgType() == MsgType.MARKET_DATA_SNAPSHOT_FULL_REFRESH) {
                var ccyPair = (CcyPair) msg.get(FixField.SYMBOL);
                mktDataMap.put(ccyPair, msg);
            } else {
                coalesced.add(msg);
            }
        }

        mktDataMap.values().forEach(coalesced::add);
        return coalesced;
    }

    private void onMktDataSnapshotFullRefresh(ParsedFixMsg msg) {
        try (var eventLog = new EventLog<>(logBuf)) {
            var updatedBook = mktDataBooks.updateBook(msg);
            riskLimits.updatedMktData(updatedBook);
            var ccyPair = (CcyPair) updatedBook.tradeable;
            var ledger = execLedgers.get(ccyPair);
            trade(ccyPair, updatedBook, ledger, msg.recvdTime(), eventLog);
            var buf = eventLog.to(DstSet.APP, Log.DEBUG)
                    .add("----- MarketDataSnapshotFullRefresh -----\n");
            buf.add(msg.msg().values().iterator(), FixFieldVal::toLogline);
            buf.add("-----------------------------------------\n");
        }
    }

    private void onMktDataTradingSessionStatus() {
        var prevMode = haltAndDeactivate();
        unsubscribeFromMktData();
        Log.ERROR.to(DstSet.APP_STD_OUT, "MktDataSession called onTradingSessionStatus(), halting trading! Was in mode: " + prevMode);
    }

    private void onExecutionReport(ParsedFixMsg msg) {
        try (var eventLog = new EventLog<>(logBuf)) {
            var updatedLedger = execLedgers.handleExecReport(msg, eventLog);
            riskLimits.updatedLedger(updatedLedger);
            var ccyPair = (CcyPair) updatedLedger.tradeable;
            var mktDataBook = mktDataBooks.get(ccyPair);
            trade(ccyPair, mktDataBook, updatedLedger, msg.recvdTime(), eventLog);
            var buf = eventLog.to(DstSet.APP_STD_OUT_EXEC, Log.INFO);
            if (buf instanceof OptBuf.Buf) {
                CempakaLog.logExecutionReport(msg.msg(), buf);
            }
        }
    }

    private void onExecTradingSessionStatus() {
        var prevMode = haltAndDeactivate();
        Log.ERROR.to(DstSet.APP_STD_OUT, "ExecSession called onTradingSessionStatus(), halting trading! Was in mode: " + prevMode);
    }

    void printMarketDataBook(CcyPair ccyPair, int maxUncxdLvls) {
        var book = mktDataBooks.get(ccyPair);
        Log.INFO.to(DstSet.APP_STD_OUT, book.print(maxUncxdLvls));
    }

    public boolean enqueueManualOrder(Order order) {
        return manualOrderQueue.offer(order);
    }

    private Result<QFJRes, FixErr> submitManualOrder(Order order) {
        var result = execSession.sendNewOrderSingle(order);

        if (result.map(QFJRes::success).orElse(false)) {
            var execLedger = execLedgers.get((CcyPair) order.tradeable());
            execLedger.addPendingNewOrder(order);
            Log.INFO.to(DstSet.APP_STD_OUT, "Order sent: " + order);
        } else {
            Log.ERROR.to(DstSet.APP_STD_OUT,
                    "Couldn't find session to send order on! result:");
            Log.ERROR.to(DstSet.APP_STD_OUT, result);
        }

        return result;
    }

    // TODO: When we reach the end of the loop, we should check the FIX
    // incoming message queue, and if it is not empty, do nothing and wait for
    // the last message before sending orders
    private void trade(CcyPair ccyPair, MktDataBook mktDataBook,
            ExecLedger execLedger, LocalDateTime now, EventLog<DstSet> eventLog) {
        final var constraints = Constraints.on(ccyPair.ccy1);

        var cxdBook = cxdBookAnalyzer.analyze(mktDataBook, constraints.minSigQty());
        if (cxdBook == CxdBookAnalysis.NOT_CXD) {
            return;
        }

        clearDryRunOrders(ccyPair, execLedger, now);

        var riskLimit = riskLimits.preTradeApproved(ccyPair, cxdBook,
                execLedgers, queueList, now, eventLog);
        if (riskLimit != RiskLimit.APPROVED) {
            var newRiskLimitLogTime = lastRiskLimitLogTime.plus(RISK_LIMIT_LOG_INTERVAL);
            if (now.isAfter(newRiskLimitLogTime)) {
                lastRiskLimitLogTime = now;
                Log.INFO.to(DstSet.APP, "Saw crossed mkt in " + ccyPair + " entry blocked by riskLimit=" + riskLimit);
            }
            return;
        }

        var cxOrdersOpt = generateOrders(ccyPair, cxdBook, now, constraints);
        if (cxOrdersOpt.isNone()) {
            return;
        }

        var cxOrders = cxOrdersOpt.get();
        var riskAdjOrders = riskLimits.riskAdjustOrders(ccyPair, cxOrders);
        // TODO: Can use == once we have value types
        if (!cxOrders.equals(riskAdjOrders)) {
            Log.WARN.to(DstSet.APP, () -> "RiskLimits adjusted orders from " + cxOrders + " to " + riskAdjOrders);
        }

        final var tradingMode = riskLimits.getTradingMode();
        final var entryBuf = eventLog.to(DstSet.EXEC, Log.INFO);
        boolean buySent;
        switch (tradingMode) {
            case DO_IT_LIVE -> {
                var buyResult = execSession.sendNewOrderSingle(riskAdjOrders.buy());
                buySent = buyResult.map(QFJRes::success).orElse(false);
                // Even if order send fails we apply backoff period and
                // decrement the magazine
                riskLimits.sentOrder(ccyPair, now);
                if (buySent) {
                    entryLogger.logEntryOrder(riskAdjOrders.buy(), tradingMode, entryBuf);
                    eventLog.to(DstSet.APP, Log.INFO)
                        .add("------------ NewOrderSingle -------------\n")
                        .add(riskAdjOrders.buy()).add("\n")
                        .add("-----------------------------------------\n");
                }
            }
            case DRY_RUN -> {
                dryRunOrders.put((ClOrdrID) riskAdjOrders.buy().id(), riskAdjOrders.buy());
                buySent = true;
                riskLimits.sentOrder(ccyPair, now);
                entryLogger.logEntryOrder(riskAdjOrders.buy(), tradingMode, entryBuf);
            }
            default -> throw new IllegalStateException("" + tradingMode);
        }

        if (buySent) {
            execLedger.addPendingNewOrder(riskAdjOrders.buy());

            boolean sellSent;
            switch (tradingMode) {
                case DO_IT_LIVE -> {
                    var sellResult = execSession.sendNewOrderSingle(riskAdjOrders.sell());
                    sellSent = sellResult.map(QFJRes::success).orElse(false);
                    // Even if order send fails we apply backoff period and
                    // decrement the magazine
                    riskLimits.sentOrder(ccyPair, now);
                    if (sellSent) {
                        entryLogger.logEntryOrder(riskAdjOrders.sell(), tradingMode, entryBuf);
                        eventLog.to(DstSet.APP, Log.INFO)
                            .add("------------ NewOrderSingle -------------\n")
                            .add(riskAdjOrders.sell()).add("\n")
                            .add("-----------------------------------------\n");
                    }
                }
                case DRY_RUN -> {
                    dryRunOrders.put((ClOrdrID) riskAdjOrders.sell().id(), riskAdjOrders.sell());
                    sellSent = true;
                    riskLimits.sentOrder(ccyPair, now);
                    entryLogger.logEntryOrder(riskAdjOrders.sell(), tradingMode, entryBuf);
                }
                default -> throw new IllegalStateException("" + tradingMode);
            }

            if (sellSent) {
                execLedger.addPendingNewOrder(riskAdjOrders.sell());
                entryLogger.logEntry(riskAdjOrders, cxdBook, mktDataBook, fees, constraints.minSigQty(), entryBuf);
            } else {
                Log.ERROR.to(DstSet.APP_STD_OUT, "SENT BUY ORDER WITHOUT MATCHING SELL!");
                Log.ERROR.to(DstSet.APP_STD_OUT, "Buy order: " + riskAdjOrders.buy());
                Log.ERROR.to(DstSet.APP_STD_OUT, "FAILED sell order: " + riskAdjOrders.sell());
            }
        }
    }

    private void clearDryRunOrders(CcyPair ccyPair, ExecLedger execLedger,
            LocalDateTime now) {
        Set<ClOrdrID> toRemove = new HashSet<>();
        dryRunOrders.forEach((id, order) -> {
            if (order.tradeable().equals(ccyPair)) {
                LocalDateTime orderExp = order.sentTime().fieldVal().plus(DRY_RUN_EXPIRY);
                if (now.isAfter(orderExp)) {
                    execLedger.forceCxlOrder(id, order);
                    toRemove.add(id);
                }
            }
        });
        dryRunOrders.keySet().removeAll(toRemove);
    }

    private Opt<CxOrders> generateOrders(CcyPair ccyPair, CxdBookAnalysis cxdBook, LocalDateTime now, Constraints constraints) {
        final var qtyLimit = riskLimits.tradeQtyLimit(ccyPair);
        final var valLimit = riskLimits.usdEquivTradeLimit();

        var buyParams = findParamsToTradeAgainst(cxdBook.cxdAskLvls(), qtyLimit,
                valLimit);
        var sellParams = findParamsToTradeAgainst(cxdBook.cxdBidLvls(), qtyLimit,
                valLimit);

        if (Math.abs(buyParams.qty() - sellParams.qty()) >= constraints.minSigQty()) {
            Log.ERROR.to(DstSet.APP_STD_OUT, "Had qty mismatch! buyParams=" + buyParams + ", sellParams=" + sellParams + ", " +
                    "cxdBook=" + cxdBook);
            return Opt.none();
        }

        var qty = sanitizeOrderQty(Math.min(buyParams.qty(), sellParams.qty()), constraints);
        if (qty.qty() < constraints.minOrderQty()) {
            return Opt.none();
        }

        var midPxs = getMidPxs(sellParams.worstPx(), buyParams.worstPx(), constraints);
        var transactTime = new TransactTime(now);
        var buyOrder = new Order(
                ClOrdrID.from(now),
                ccyPair,
                ccyPair.ccy1,
                Side.BUY,
                qty,
                sanitizePrice(midPxs.askPx(), Side.BUY, constraints),
                TimeInForce.IMMEDIATE_OR_CANCEL,
                transactTime
        );
        var sellOrder = new Order(
                ClOrdrID.from(now, 1),
                ccyPair,
                ccyPair.ccy1,
                Side.SELL,
                qty,
                sanitizePrice(midPxs.bidPx(), Side.SELL, constraints),
                TimeInForce.IMMEDIATE_OR_CANCEL,
                transactTime
        );

        return Opt.of(new CxOrders(buyOrder, sellOrder));
    }

    Price sanitizePrice(double px, Side side, CcyPair ccyPair) {
        var constraints = Constraints.on(ccyPair.ccy1);
        return sanitizePrice(px, side, constraints);
    }

    private Price sanitizePrice(double px, Side side, Constraints constraints) {
        var roundingMode = switch (side) {
            case BUY -> RoundingMode.DOWN;
            case SELL -> RoundingMode.UP;
            default -> throw new IllegalArgumentException("" + side);
        };

        return new Price(doubleRounder.round(px, constraints.pxMaxDecimalPts(), roundingMode));
    }

    OrderQty sanitizeOrderQty(double qty, CcyPair ccyPair) {
        var constraints = Constraints.on(ccyPair.ccy1);
        return sanitizeOrderQty(qty, constraints);
    }

    private OrderQty sanitizeOrderQty(double qty, Constraints constraints) {
        return new OrderQty(doubleRounder.round(qty, constraints.qtyMaxDecimalPts(), RoundingMode.DOWN));
    }

    private ParamsToTrade findParamsToTradeAgainst(List<CxdBookLvl> lvls,
            final double qtyLimit, final double valLimit) {
        double qtyToTrade = 0.0d, valToTrade = 0.0d, worstPxToTrade = 0.0d;
        for (var lvl : lvls) {
            var lvlPx = lvl.px().px();
            double qtyToAdd = findQtyToAdd(qtyToTrade, valToTrade, lvl.qty().qty(),
                    lvlPx, qtyLimit, valLimit);
            if (qtyToAdd > 0.0d) {
                qtyToTrade += qtyToAdd;
                valToTrade += qtyToAdd * lvlPx;
                worstPxToTrade = lvlPx;
            } else {
                break;
            }
        }

        return new ParamsToTrade(qtyToTrade, valToTrade, worstPxToTrade);
    }

    private /* primitive */ /* value */ record ParamsToTrade(
            double qty, double val, double worstPx) { }

    // TODO: Make this more sophisticated
    private MidPxs getMidPxs(double bidPx, double askPx, Constraints constraints) {
        double mid = (bidPx + askPx) / 2.0d;
        double newBidPx = mid + 2.0d * constraints.minPxTick();
        double newAskPx = mid - 2.0d * constraints.minPxTick();
        return new MidPxs(Math.min(newBidPx, bidPx), Math.max(newAskPx, askPx));
    }

    private /* primitive */ /* value */ record MidPxs(double bidPx, double askPx) { }

    private double findQtyToAdd(double qty, double val, double qtyAvail,
            double px, final double qtyLimit, final double valLimit) {
        if (qty >= qtyLimit || val >= valLimit) {
            return 0.0d;
        }

        if (qty + qtyAvail <= qtyLimit) {
            if (val + (qtyAvail * px) <= valLimit) {
                return qtyAvail;
            } else {
                var valToAdd = valLimit - val;
                return valToAdd / px;
            }
        } else {
            var qtyToAdd = qtyLimit - qty;
            var valWouldAdd = qtyToAdd * px;
            if (val + valWouldAdd <= valLimit) {
                return qtyToAdd;
            } else {
                var valToAdd = valLimit - val;
                return valToAdd / px;
            }
        }
    }

    void startNewExecReplay() {
        execLedgers.startNewReplay();
        execSession.sendResendRequest(1, 0);
    }

    Map<CcyPair, Pnl> getNonflatPnls() {
        return execLedgers.getNonflatPnls();
    }

    Map<CcyPair, Pnl> getPnls() {
        return execLedgers.getPnls();
    }

    void printQueueStats() {
        Log.WARN.to(DstSet.APP_STD_OUT, "mktDataQueue size=" + mktDataQueue.size() + ", remCapacity=" + mktDataQueue.remainingCapacity() + ", healthy=" + mktDataQueue.healthy);
        Log.WARN.to(DstSet.APP_STD_OUT, "execQueue size=" + execQueue.size() + ", remCapacity=" + execQueue.remainingCapacity() + ", healthy=" + execQueue.healthy);
        Log.WARN.to(DstSet.APP_STD_OUT, "manualOrderQueue size=" + manualOrderQueue.size() + ", remCapacity=" + manualOrderQueue.remainingCapacity() + ", healthy=" + manualOrderQueue.healthy);
    }

    void setQueuesHealthy() {
        mktDataQueue.setHealthy();
        execQueue.setHealthy();
        manualOrderQueue.setHealthy();
    }
}
