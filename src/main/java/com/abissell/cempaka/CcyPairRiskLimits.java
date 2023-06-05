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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.abissell.logutil.EventLog;
import com.abissell.logutil.Log;
import com.abissell.cempaka.data.CxOrders;
import com.abissell.cempaka.data.CxdBookAnalysis;
import com.abissell.cempaka.data.ExecLedger;
import com.abissell.cempaka.data.ExecLedgers;
import com.abissell.cempaka.data.MktDataBook;
import com.abissell.cempaka.data.RiskLimit;
import com.abissell.cempaka.data.SidePxQtyUpdate;
import com.abissell.cempaka.data.TradingMode;
import com.abissell.cempaka.fix.MsgQueue;
import com.abissell.cempaka.util.DstSet;
import com.abissell.cempaka.data.Ccy;
import com.abissell.cempaka.data.CcyPair;
import com.abissell.fixbridge.Order;
import com.abissell.fixbridge.OrderQty;
import com.abissell.fixbridge.Side;

final class CcyPairRiskLimits {
    private final AtomicReference<TradingMode> tradingMode = new AtomicReference<>(TradingMode.HALTED);

    private final AtomicReference<EnumSet<CcyPair>> circuitBreakers
        = new AtomicReference<>(EnumSet.noneOf(CcyPair.class));

    private final AtomicInteger magazine = new AtomicInteger(0);

    // TODO: Adjust limits on USDC/USDT according to their peg

    // The TradeLimits are used to set caps on individual entry orders
    private final AtomicInteger usdEquivTradeLimit = new AtomicInteger(10_000);
    private final AtomicReference<Double> numer1TradeLimit = new AtomicReference<>(0.2d);
    private final AtomicReference<Double> numer2TradeLimit = new AtomicReference<>(2.0d);

    // The PosLimits will trip circuit breakers which must be manually reset if
    // the limits are exceeded
    private final AtomicInteger usdEquivPosLimit = new AtomicInteger(50_000);
    private final AtomicReference<Double> numer1PosLimit = new AtomicReference<>(2.5d);
    private final AtomicReference<Double> numer2PosLimit = new AtomicReference<>(25.0d);

    private final EnumMap<CcyPair, Integer> rejectedOrdersObserved = new EnumMap<>(CcyPair.class);

    private final AtomicInteger usdEquivMaxLoss = new AtomicInteger(200);

    private final AtomicReference<Double> minTradeTheoValUsd = new AtomicReference<>(0.10d);
    private final AtomicReference<Double> maxCrossRatio = new AtomicReference<>(0.03d);
    private final AtomicReference<Double> badDataCrossRatio = new AtomicReference<>(0.1d);

    private final AtomicInteger maxConcurrentEntries = new AtomicInteger(4);
    private final AtomicInteger maxConcurrentEntriesPerPair = new AtomicInteger(1);

    private final EnumMap<CcyPair, LocalDateTime> lastEntryTimes = new EnumMap<>(CcyPair.class);
    private final AtomicReference<Duration> backoffInterval = new AtomicReference<>(Duration.ofSeconds(5L));

    private LocalDateTime lastSkipLogTime = LocalDateTime.of(1990, 1, 1, 1, 1);

    CcyPairRiskLimits(LocalDateTime now) {
        var fiveMinutesAgo = now.minusMinutes(5L);
        for (var ccyPair : CcyPair.values()) {
            rejectedOrdersObserved.put(ccyPair, 0);
            lastEntryTimes.put(ccyPair, fiveMinutesAgo);
        }
    }

    TradingMode getTradingMode() {
        return tradingMode.get();
    }

    TradingMode setTradingMode(TradingMode newTradingMode) {
        return tradingMode.getAndSet(newTradingMode);
    }

    TradingMode halt() {
        return tradingMode.getAndSet(TradingMode.HALTED);
    }

    EnumSet<CcyPair> resetCircuitBreakers() {
        return circuitBreakers.getAndSet(EnumSet.noneOf(CcyPair.class));
    }

    EnumSet<CcyPair> resetCircuitBreaker(CcyPair ccyPair) {
        var prev = circuitBreakers.get();
        var updated = EnumSet.copyOf(prev);
        updated.remove(ccyPair);
        circuitBreakers.set(updated);
        return prev;
    }

    int loadMag(int rounds) {
        return magazine.addAndGet(rounds);
    }

    int roundsInMag() {
        return magazine.get();
    }

    int emptyMag() {
        return magazine.getAndSet(0);
    }

    int sentOrder(CcyPair ccyPair, LocalDateTime sentTime) {
        lastEntryTimes.put(ccyPair, sentTime);
        return magazine.decrementAndGet();
    }

    int usdEquivTradeLimit() {
        return usdEquivTradeLimit.get();
    }

    int setUsdEquivTradeLimit(int newLimit) {
        return usdEquivTradeLimit.getAndSet(newLimit);
    }

    double tradeQtyLimit(CcyPair ccyPair) {
        return switch (ccyPair.ccy1) {
            case NUMER_1 -> numer1TradeLimit.get();
            case NUMER_2 -> numer2TradeLimit.get();
            default -> throw new IllegalArgumentException("" + ccyPair);
        };
    }

    double setNumer1TradeLimit(double newLimit) {
        return numer1TradeLimit.getAndSet(newLimit);
    }

    double setNumer2TradeLimit(double newLimit) {
        return numer2TradeLimit.getAndSet(newLimit);
    }

    int setUsdEquivPosLimit(int newLimit) {
        return usdEquivPosLimit.getAndSet(newLimit);
    }

    double setNumer1PosLimit(double newLimit) {
        return numer1PosLimit.getAndSet(newLimit);
    }

    double setNumer2PosLimit(double newLimit) {
        return numer2PosLimit.getAndSet(newLimit);
    }

    int setUsdEquivMaxLoss(int newLimit) {
        return usdEquivMaxLoss.getAndSet(newLimit);
    }

    double setMinTheoEntryValUsd(double newLimit) {
        return minTradeTheoValUsd.getAndSet(newLimit);
    }

    double setMaxCrossRatio(double newLimit) {
        return maxCrossRatio.getAndSet(newLimit);
    }

    double setBadDataCrossRatio(double newLimit) {
        return badDataCrossRatio.getAndSet(newLimit);
    }

    int setMaxConcurrentEntries(int newLimit) {
        return maxConcurrentEntries.getAndSet(newLimit);
    }

    int setMaxConcurrentEntriesPerPair(int newLimit) {
        if (newLimit > 1) {
            throw new IllegalArgumentException("Code is not yet configured for more than one entry per pair!");
        }
        return maxConcurrentEntriesPerPair.getAndSet(newLimit);
    }

    Duration setBackoffInterval(int newDurationSecs) {
        return backoffInterval.getAndSet(Duration.ofSeconds(newDurationSecs));
    }

    void updatedMktData(MktDataBook book) {
        var ccyPair = (CcyPair) book.tradeable;

        List<SidePxQtyUpdate> bids = book.get(Side.BUY), asks = book.get(Side.SELL);
        if (bids.isEmpty() || asks.isEmpty()) {
            tripCircuitBreaker(ccyPair);
        }

        var highestBid = bids.get(0).level().px().px();
        var lowestAsk = asks.get(0).level().px().px();
        if (highestBid > (1.0d + badDataCrossRatio.get()) * lowestAsk) {
            tripCircuitBreaker(ccyPair);
        }
    }


    void updatedLedger(ExecLedger ledger) {
        var ccyPair = (CcyPair) ledger.tradeable;

        var pnl = ledger.pnl();
        double netQty = 0.0d, avgPx = 0.0d;
        if (pnl.botQty() > pnl.sldQty()) {
            netQty = pnl.botQty() - pnl.sldQty();
            avgPx = pnl.botVal() / pnl.botQty();
        } else if (pnl.sldQty() > pnl.botQty()) {
            netQty = pnl.sldQty() - pnl.botQty();
            avgPx = pnl.sldVal() / pnl.sldQty();
        }

        if (netQty > 0) {
            var usdEquivPos = netQty * avgPx;
            if (usdEquivPos > usdEquivPosLimit.get()) {
                tripCircuitBreaker(ccyPair);
                return;
            } else {
                var ccy1PosLimit = switch (ccyPair.ccy1) {
                    case NUMER_1 -> numer1PosLimit.get();
                    case NUMER_2 -> numer2PosLimit.get();
                    default -> throw new IllegalArgumentException("Can't get ccy1PosLimit for ccyPair " + ccyPair);
                };

                if (netQty > ccy1PosLimit) {
                    tripCircuitBreaker(ccyPair);
                    return;
                }
            }
        }

        var prevRejectedOrders = rejectedOrdersObserved.get(ccyPair);
        var newRejectedOrders = ledger.rejectedOrders().size();
        if (newRejectedOrders > prevRejectedOrders) {
            rejectedOrdersObserved.put(ccyPair, newRejectedOrders);
            tripCircuitBreaker(ccyPair);
            halt();
        }
    }

    private void tripCircuitBreaker(CcyPair ccyPair) {
        var breakers = circuitBreakers.get();
        var newBreakers = EnumSet.copyOf(breakers);
        newBreakers.add(ccyPair);
        circuitBreakers.set(newBreakers);
    }

    RiskLimit preTradeApproved(CcyPair ccyPair, CxdBookAnalysis cxdBook,
            ExecLedgers<CcyPair> execLedgers, Collection<MsgQueue<?>> queues,
            LocalDateTime now, EventLog<DstSet> eventLog) {
        if (tradingMode.get() == TradingMode.HALTED) {
            return RiskLimit.TRADING_HALTED;
        }

        if (circuitBreakers.get().contains(ccyPair)) {
            return RiskLimit.CIRCUIT_BREAKER;
        }

        if (magazine.get() <= 0) {
            return RiskLimit.MAG_EMPTY;
        }

        for (var queue : queues) {
            if (!queue.healthy) {
                return RiskLimit.UNHEALTHY_QUEUE;
            }
        }

        var numer1Pnl = execLedgers.sumPnls(t -> t.ccy1 == Ccy.NUMER_1);
        var numer2Pnl = execLedgers.sumPnls(t -> t.ccy1 == Ccy.NUMER_2);
        var totalPnl = numer1Pnl.netPnl() + numer2Pnl.netPnl();
        if (totalPnl < -1.0d * usdEquivMaxLoss.get()) {
            return RiskLimit.MAX_LOSS;
        }

        var fillableOrdersForPair = execLedgers.fillableOrdersFor(ccyPair);
        if (fillableOrdersForPair >= maxConcurrentEntriesPerPair.get()) {
            return RiskLimit.TRADEABLE_CONCURRENT_ENTRIES;
        }

        var allFillableOrdersCount = execLedgers.allFillableOrdersCount();
        if (allFillableOrdersCount >= maxConcurrentEntries.get()) {
            return RiskLimit.SYSTEM_CONCURRENT_ENTRIES;
        }

        var lastEntryTime = lastEntryTimes.get(ccyPair);
        var elapsed = Duration.between(lastEntryTime, now);
        if (elapsed.toMillis() <= backoffInterval.get().toMillis()) {
            return RiskLimit.BACKOFF_INTERVAL;
        }

        if (cxdBook.theoValUsd() < minTradeTheoValUsd.get()) {
            if (now.isAfter(lastSkipLogTime.plusSeconds(5))) {
                eventLog.to(DstSet.APP, Log.INFO).add(ccyPair + " skipping trade with theoValUsd=" + cxdBook.theoValUsd() + " < minTradeTheoValUsd=" + minTradeTheoValUsd.get());
                lastSkipLogTime = now;
            }
            return RiskLimit.TRADE_THEO_VAL;
        }

        var cxdBidPx = cxdBook.cxdBidLvls().get(0).px().px();
        var cxdAskPx = cxdBook.cxdAskLvls().get(0).px().px();
        var crossRatio = (cxdBidPx - cxdAskPx) / (cxdAskPx);
        if (crossRatio > maxCrossRatio.get()) {
            return RiskLimit.CROSS_RATIO;
        }

        return RiskLimit.APPROVED;
    }

    // TODO: Update this to handle multiple inflight entries, currently assumes
    // it is just working with non-concurrent buy/sell pairs
    public CxOrders riskAdjustOrders(CcyPair ccyPair, CxOrders cxOrders) {
        if (maxConcurrentEntriesPerPair.get() != 1) {
            throw new IllegalStateException("riskAdjustOrders() method is not yet configured to handle multiple inflight entries!");
        }

        boolean adjusted = false;

        Order sellOrder = cxOrders.sell();
        var sellOrderPx = sellOrder.px().px();
        var sellOrderVal = sellOrder.qty().qty() * sellOrderPx;
        var usdTradeLimit = usdEquivTradeLimit.get();
        OrderQty qtyToTrade = sellOrder.qty();
        if (sellOrderVal > usdTradeLimit) {
            qtyToTrade = new OrderQty(usdTradeLimit / sellOrderPx);
            adjusted = true;
        }

        double baseTradeLimit = switch (ccyPair.ccy1) {
            case NUMER_1 -> numer1TradeLimit.get();
            case NUMER_2 -> numer2TradeLimit.get();
            default -> throw new IllegalArgumentException("" + ccyPair);
        };
        if (qtyToTrade.qty() > baseTradeLimit) {
            qtyToTrade = new OrderQty(baseTradeLimit);
            adjusted = true;
        }

        if (adjusted) {
            return new CxOrders(
                    cxOrders.buy().withNewQty(qtyToTrade),
                    sellOrder.withNewQty(qtyToTrade)
                );
        } else {
            return cxOrders;
        }
    }

    @Override
    public String toString() {
        return "CcyPairRiskLimits{tradingMode=" + tradingMode.get() + ", circuitBreakers=" + circuitBreakers.get() + ", magazine="
                + magazine.get() + ", usdEquivTradeLimit=" + usdEquivTradeLimit.get() + ", numer1TradeLimit=" + numer1TradeLimit.get()
                + ", numer2TradeLimit=" + numer2TradeLimit.get() + ", usdEquivPosLimit=" + usdEquivPosLimit.get() +
                ", numer1PosLimit="
                + numer1PosLimit.get() + ", numer2PosLimit=" + numer2PosLimit.get() + ", rejectedOrdersObserved=" + rejectedOrdersObserved
                + ", usdEquivMaxLoss=" + usdEquivMaxLoss.get() + ", minTradeTheoValUsd=" + minTradeTheoValUsd.get() + ", maxCrossRatio=" + maxCrossRatio.get() + ", badDataCrossRatio="
                + badDataCrossRatio.get() + ", maxConcurrentEntries=" + maxConcurrentEntries.get()
                + ", maxConcurrentEntriesPerPair=" + maxConcurrentEntriesPerPair.get()+ ", lastEntryTimes=" + lastEntryTimes
                + ", backoffInterval=" + backoffInterval.get()+ "}";
    }
}
