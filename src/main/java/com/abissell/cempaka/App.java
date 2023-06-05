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

import java.nio.charset.Charset;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import com.abissell.logutil.Log;
import com.abissell.cempaka.data.Fees;
import com.abissell.cempaka.data.Pnl;
import com.abissell.cempaka.data.QFJRes;
import com.abissell.cempaka.data.TradingMode;
import com.abissell.cempaka.util.CempakaIOUtil;
import com.abissell.cempaka.util.DstSet;
import com.abissell.cempaka.data.Ccy;
import com.abissell.cempaka.data.CcyPair;
import com.abissell.fixbridge.ClOrdID;
import com.abissell.cempaka.orderid.ClOrdrID;
import com.abissell.fixbridge.FixSessionBridge;
import com.abissell.fixbridge.Order;
import com.abissell.fixbridge.OrderQty;
import com.abissell.fixbridge.OrdrCxlReq;
import com.abissell.fixbridge.Price;
import com.abissell.fixbridge.Side;
import com.abissell.fixbridge.StrClOrdID;
import com.abissell.fixbridge.TimeInForce;
import com.abissell.fixbridge.TransactTime;

import quickfix.ConfigError;
import quickfix.SessionNotFound;

public class App {
    // optimized timestamp creation, see
    // https://github.com/quickfix-j/quickfixj/pull/455
    private static final Clock UTC_CLOCK = Clock.systemUTC();

    private static LocalDateTime now() {
        var instant = UTC_CLOCK.instant();
        return LocalDateTime.ofEpochSecond(instant.getEpochSecond(), instant.getNano(), ZoneOffset.UTC);
    }

    public static void main(String[] args) throws ConfigError, SessionNotFound, InterruptedException {
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("UTC")));

        Log.ERROR.to(DstSet.APP_STD_OUT, "\nCEMPAKA started, Java runtime details:");
        Log.ERROR.to(DstSet.APP_STD_OUT, "Java Runtime version    : " + Runtime.version());
        Log.ERROR.to(DstSet.APP_STD_OUT, "Available processors    : " + Runtime.getRuntime().availableProcessors());
        Log.ERROR.to(DstSet.APP_STD_OUT, 
                "Free memory             : " + Runtime.getRuntime().freeMemory() / (1024L * 1024L) + "M");
        Log.ERROR.to(DstSet.APP_STD_OUT, 
                "Max memory              : " + Runtime.getRuntime().maxMemory() / (1024L * 1024L) + "M");
        Log.ERROR.to(DstSet.APP_STD_OUT, "TimeZone.getDefault()   : " + TimeZone.getDefault());
        Log.ERROR.to(DstSet.APP_STD_OUT, "Charset.defaultCharset(): " + Charset.defaultCharset());

        Log.WARN.to(DstSet.STD_OUT, "Input environment ('TEST' or 'PROD')");
        final var env = System.console().readLine();
        final String fileSuffix = switch (env) {
            case "TEST" -> "-uat.cfg";
            case "PROD" -> "-prod.cfg";
            default -> {
                Log.ERROR.to(DstSet.APP_STD_OUT, "Didn't recognize env=" + env);
                throw new IllegalArgumentException("env: " + env);
            }
        };

        var ccyPairTrader = new CcyPairTrader(App::now, Fees.PCT, fileSuffix);
        var mktDataSession = ccyPairTrader.mktDataSession;
        var execSession = ccyPairTrader.execSession;
        var placedOrders = new HashMap<String, Order>();
        var canceledOrders = new HashMap<String, OrdrCxlReq>();
        while (true) {
            Log.ERROR.to(DstSet.STD_OUT, """
                    \nChoose MENU (0),
                    START_MD_SESSION (1), SUBSCRIBE (2), UNSUBSCRIBE(3), STOP_MD_SESSION (4),
                    START_EXEC_SESSION (5), PLACE_ORDER (6), CANCEL_ORDER (7), STOP_EXEC_SESSION (8),
                    QUIT (9),
                    RESEND_REQUEST (10),
                    ADJUST_RISK_LIMITS (11),
                    ACTIVATE_PAIRS_FOR_TRADING (12),
                    DEACTIVATE_PAIRS_FOR_TRADING (13),
                    HALT_TRADING (14),
                    PRINT_MARKET_DATA_BOOK (15),
                    REPLAY_PNL (16),
                    PRINT_PNLS (17),
                    TEE_HEDGES (18),
                    PRINT_QUEUE_STATS (19),
                    SET_QUEUES_HEALTHY (20)
                    """);
            String input = System.console().readLine();
            Log.ERROR.to(DstSet.STD_OUT, "\n");

            boolean quit = false;
            try {
                switch (input) {
                    case "0", "MENU" -> {
                    }
                    case "1", "START_MD_SESSION" -> start(mktDataSession, "market data");
                    case "2", "SUBSCRIBE" -> {
                        var ccyPairs = CempakaIOUtil.readCcyPairInput("subscribe to market data updates");
                        ccyPairTrader.subscribeToMktData(ccyPairs);
                    }
                    case "3", "UNSUBSCRIBE" -> {
                        Log.INFO.to(DstSet.APP_STD_OUT, "Are you SURE you want to unsubscribe from market data? (Y/n)");
                        var unsubStr = System.console().readLine();
                        var unsub = switch (unsubStr) {
                                case "Y", "y", "yes" -> true;
                                case "N", "n", "no" -> false;
                                    default ->
                                        throw new IllegalArgumentException(
                                                "Unrecognized unsubscribe boolean option: " + unsubStr);
                        };

                        if (unsub) {
                            ccyPairTrader.unsubscribeFromMktData();
                        }
                    }
                    case "4", "STOP_MD_SESSION" -> {
                        Log.INFO.to(DstSet.APP_STD_OUT, "Trying to stop session");
                        if (mktDataSession.loggedOn()) {
                            mktDataSession.stop();
                            Log.INFO.to(DstSet.APP_STD_OUT, "Session stop() call completed.");
                        } else {
                            Log.ERROR.to(DstSet.APP_STD_OUT, "Could not stop session, no session was created");
                        }
                    }
                    case "5", "START_EXEC_SESSION" -> start(execSession, "execution");
                    case "6", "PLACE_ORDER" -> {
                        if (execSession.loggedOn()) {
                            Log.INFO.to(DstSet.APP_STD_OUT, """
                                    Choose the CcyPair to trade:
                                    (0) 1/1
                                    (1) 1/2
                                    (2) 1/3
                                    (3) 2/1
                                    (4) 2/2
                                    (5) 2/3
                                    """);
                            CcyPair ccyPair = switch (Integer.parseInt(System.console().readLine())) {
                                case 0 -> CcyPair.NUMER_1_DENOM_1;
                                case 1 -> CcyPair.NUMER_1_DENOM_2;
                                case 2 -> CcyPair.NUMER_1_DENOM_3;
                                case 3 -> CcyPair.NUMER_2_DENOM_1;
                                case 4 -> CcyPair.NUMER_2_DENOM_2;
                                case 5 -> CcyPair.NUMER_2_DENOM_3;
                                default -> throw new NumberFormatException("Unrecognized CcyPair input");
                            };
                            var ccy1 = ccyPair.ccy1;
                            var ccy2 = ccyPair.ccy2;
                            Log.INFO.to(DstSet.APP_STD_OUT, """
                                    Choose the base Ccy for trade price:
                                    (0) %s   (1) %s
                                    """.formatted(ccy1, ccy2));
                            Ccy baseCcy = switch (Integer.parseInt(System.console().readLine())) {
                                case 0 -> ccy1;
                                case 1 -> ccy2;
                                default -> throw new NumberFormatException("Unrecognized baseCcy input");
                            };

                            Log.INFO.to(DstSet.APP_STD_OUT, 
                                    "Enter the desired time in force (`DAY` (0), `GOOD_TILL_CANCEL` (1), `IMMEDIATE_OR_CANCEL` (2), `FILL_OR_KILL` (3)):");
                            var tif = switch (Integer.parseInt(System.console().readLine())) {
                                case 0 -> TimeInForce.DAY;
                                case 1 -> TimeInForce.GOOD_TILL_CANCEL;
                                case 2 -> TimeInForce.IMMEDIATE_OR_CANCEL;
                                case 3 -> TimeInForce.FILL_OR_KILL;
                                default -> throw new IllegalArgumentException("Didn't recognize time-in-force input");
                            };
                            Log.INFO.to(DstSet.APP_STD_OUT, "Enter the side (`BUY` or `SELL`):");
                            var sideStr = System.console().readLine();
                            var side = switch (sideStr.toUpperCase()) {
                                case "BUY" -> Side.BUY;
                                case "SELL" -> Side.SELL;
                                    default ->
                                        throw new IllegalArgumentException(
                                                "Didn't recognize 'Side' string: " + sideStr);
                            };
                            Log.INFO.to(DstSet.APP_STD_OUT, "Enter the desired qty as a decimal value:");
                            double qty = Double.parseDouble(System.console().readLine());
                            Log.INFO.to(DstSet.APP_STD_OUT, "Enter the desired px as a decimal value:");
                            double px = Double.parseDouble(System.console().readLine());

                            var now = now();
                            var transactTime = new TransactTime(now);
                            Log.INFO.to(DstSet.APP_STD_OUT, 
                                    "Send order with ccyPair=" + ccyPair + ", baseCcy=" + baseCcy + ", side=" + side
                                    + ", qty=" + qty
                                    + ", px=" + px + ", transactTime=" + transactTime + "? (Y/n) ");
                            var sendStr = System.console().readLine();
                            var send = switch (sendStr) {
                                case "Y", "y", "yes" -> true;
                                case "N", "n", "no" -> false;
                                    default ->
                                        throw new IllegalArgumentException(
                                                "Unrecognized send boolean option: " + sendStr);
                            };
                            if (send) {
                                var ordrID = ClOrdrID.from(now);
                                var order = new Order(ordrID, ccyPair, baseCcy, side, new OrderQty(qty), new Price(px), tif, transactTime);
                                var result = ccyPairTrader.enqueueManualOrder(order);
                                if (!result) {
                                    Log.ERROR.to(DstSet.APP_STD_OUT, "Could not enqueue manual order! " + order);
                                } else {
                                    placedOrders.put(ordrID.fieldVal(), order);
                                }
                            } else {
                                Log.INFO.to(DstSet.APP_STD_OUT, "'No' chosen, not sending order.");
                            }
                        }
                    }
                    case "7", "CANCEL_ORDER" -> {
                        if (execSession.loggedOn()) {
                            Log.INFO.to(DstSet.APP_STD_OUT, "Enter the desired order ID to cancel:");
                            String orderId = System.console().readLine();
                            if (canceledOrders.containsKey(orderId)) {
                                Log.ERROR.to(DstSet.APP_STD_OUT, "Already sent cancel request on order ID " + orderId + "!");
                                break;
                            }
                            var orderToCxl = placedOrders.get(orderId);
                            if (orderToCxl == null) {
                                Log.ERROR.to(DstSet.APP_STD_OUT, "Could not find order with id=" + orderId + " to cancel!");
                                break;
                            }
                            ClOrdID cxlReqID = switch (orderToCxl.id()) {
                                case ClOrdrID ordrID -> new ClOrdrID(ordrID.id().getCxlReqID());
                                case ClOrdID ordID -> new StrClOrdID(ordID.fieldVal() + "_cxl");
                            };
                            var sentTime = new TransactTime(now());
                            Log.INFO.to(DstSet.APP_STD_OUT, 
                                    "Sending cancel request on order with orderId=" + orderId + " ... ");
                            var cxlReq = new OrdrCxlReq(cxlReqID, orderToCxl, sentTime);
                            var result = execSession.sendOrderCancelRequest(cxlReq);
                            if (result.map(QFJRes::success).orElse(false)) {
                                Log.INFO.to(DstSet.APP_STD_OUT, "Cancel Request sent with id " + cxlReqID);
                                canceledOrders.put(orderId, cxlReq);
                            }
                        } else {
                            Log.ERROR.to(DstSet.APP_STD_OUT, 
                                    "Could not send order cancel request, no execution session was present");
                        }
                    }
                    case "8", "STOP_EXEC_SESSION" -> {
                        Log.INFO.to(DstSet.APP_STD_OUT, "Trying to stop session");
                        if (execSession.loggedOn()) {
                            execSession.stop();
                            Log.INFO.to(DstSet.APP_STD_OUT, "Session stop() call completed.");
                        } else {
                            Log.ERROR.to(DstSet.APP_STD_OUT, "Could not stop session, no session was created");
                        }
                    }
                    case "9", "QUIT" -> quit = true;
                    case "10", "RESEND_REQUEST" -> {
                        Log.WARN.to(DstSet.APP_STD_OUT, "Send ResendRequest on which session? (0=DEAL, 1=RATE)");
                        var sessionStr = System.console().readLine();
                        Log.WARN.to(DstSet.APP_STD_OUT, "Enter begin sequence number:");
                        int begin = Integer.parseInt(System.console().readLine());
                        Log.WARN.to(DstSet.APP_STD_OUT, "Enter end sequence number (0=infinity):");
                        int end = Integer.parseInt(System.console().readLine());
                        switch (sessionStr) {
                            case "0", "DEAL" -> {
                                if (execSession.loggedOn()) {
                                    execSession.sendResendRequest(begin, end);
                                } else {
                                    Log.ERROR.to(DstSet.APP_STD_OUT, 
                                            "Couldn't send ResendRequest on dealSession, no session was created");
                                }
                            }
                            case "1", "RATE" -> {
                                if (mktDataSession.loggedOn()) {
                                    mktDataSession.sendResendRequest(begin, end);
                                } else {
                                    Log.ERROR.to(DstSet.APP_STD_OUT, 
                                            "Couldn't send ResendRequest on rateSession, no session was created");
                                }
                            }
                            default -> throw new IllegalArgumentException(sessionStr);
                        }
                    }
                    case "11", "ADJUST_RISK_LIMITS" -> {
                        Log.ERROR.to(DstSet.STD_OUT, """
                                \nChoose limit to adjust:
                                PRINT_CURRENT_LIMITS (0),
                                TRADING_MODE (1), RESET_CIRCUIT_BREAKER (2), LOAD_MAG (3), EMPTY_MAG (4), USD_EQUIV_TRADE_LIMIT (5),
                                NUMER_1_TRADE_QTY_LIMIT (6), NUMER_2_TRADE_QTY_LIMIT (7), USD_EQUIV_POS_LIMIT (8), NUMER_1_POS_LIMIT (9),
                                NUMER_2_POS_LIMIT (10), USD_EQUIV_MAX_LOSS (11), MIN_TRADE_THEO_VAL (12), MAX_CROSS_RATIO (13),
                                BAD_DATA_CROSS_RATIO (14), SYSTEM_MAX_CONCURRENT_ENTRIES (15), MAX_CONCURRENT_ENTRIES_PER_PAIR (16),
                                BACKOFF_INTERVAL (17)
                                """);
                        input = System.console().readLine();
                        Log.ERROR.to(DstSet.STD_OUT, "Input value (CcyPair if resetting circuit breaker): ");
                        String value;
                        switch (input) {
                            case "0", "PRINT_CURRENT_LIMITS",
                                 "4", "EMPTY_MAG" -> value = "";
                            default -> value = System.console().readLine();
                        }
                        var riskLimits = ccyPairTrader.riskLimits;
                        switch (input) {
                            case "0", "PRINT_CURRENT_LIMITS" -> {}
                            case "1", "TRADING_MODE" -> riskLimits.setTradingMode(TradingMode.valueOf(value));
                            case "2", "RESET_CIRCUIT_BREAKER" -> riskLimits.resetCircuitBreaker(CcyPair.valueOf(value));
                            case "3", "LOAD_MAG" -> riskLimits.loadMag(Integer.parseInt(value));
                            case "4", "EMPTY_MAG" -> riskLimits.emptyMag();
                            case "5", "USD_EQUIV_TRADE_LIMIT" -> riskLimits.setUsdEquivTradeLimit(Integer.parseInt(value));
                            case "6", "NUMER_1_TRADE_QTY_LIMIT" -> riskLimits.setNumer1TradeLimit(Double.parseDouble(value));
                            case "7", "NUMER_2_TRADE_QTY_LIMIT" -> riskLimits.setNumer2TradeLimit(Double.parseDouble(value));
                            case "8", "USD_EQUIV_POS_LIMIT" -> riskLimits.setUsdEquivPosLimit(Integer.parseInt(value));
                            case "9", "NUMER_1_POS_LIMIT" -> riskLimits.setNumer1PosLimit(Double.parseDouble(value));
                            case "10", "NUMER_2_POS_LIMIT" -> riskLimits.setNumer2PosLimit(Double.parseDouble(value));
                            case "11", "USD_EQUIV_MAX_LOSS" -> riskLimits.setUsdEquivMaxLoss(Integer.parseInt(value));
                            case "12", "MIN_TRADE_THEO_VAL" -> riskLimits.setMinTheoEntryValUsd(Double.parseDouble(value));
                            case "13", "MAX_CROSS_RATIO" -> riskLimits.setMaxCrossRatio(Double.parseDouble(value));
                            case "14", "BAD_DATA_CROSS_RATIO" -> riskLimits.setBadDataCrossRatio(Double.parseDouble(value));
                            case "15", "SYSTEM_MAX_CONCURRENT_ENTRIES" -> riskLimits.setMaxConcurrentEntries(Integer.parseInt(value));
                            case "16", "MAX_CONCURRENT_ENTRIES_PER_PAIR" -> riskLimits.setMaxConcurrentEntriesPerPair(Integer.parseInt(value));
                            case "17", "BACKOFF_INTERVAL" -> riskLimits.setBackoffInterval(Integer.parseInt(value));
                            default -> throw new IllegalArgumentException("Didn't recognize " + input);
                        }
                        Log.ERROR.to(DstSet.STD_OUT, "RiskLimits are now " + riskLimits.toString());
                    }
                    case "12", "ACTIVATE_PAIRS_FOR_TRADING" -> {
                        Log.INFO.to(DstSet.APP_STD_OUT, "Currently active trading pairs: " + ccyPairTrader.getActiveTradingPairs());
                        var ccyPairs = CempakaIOUtil.readCcyPairInput("activate for trading");
                        ccyPairTrader.activatePairs(ccyPairs);
                    }
                    case "13", "DEACTIVATE_PAIRS_FOR_TRADING" -> {
                        Log.INFO.to(DstSet.APP_STD_OUT, "Currently active trading pairs: " + ccyPairTrader.getActiveTradingPairs());
                        var ccyPairs = CempakaIOUtil.readCcyPairInput("deactivate for trading");
                        ccyPairTrader.deactivatePairs(ccyPairs);
                    }
                    case "14", "HALT_TRADING" -> {
                        ccyPairTrader.riskLimits.halt();
                        ccyPairTrader.deactivate();
                    }
                    case "15", "PRINT_MARKET_DATA_BOOK" -> {
                        Log.INFO.to(DstSet.APP_STD_OUT, 
                                "Enter the CcyPair to print`:\n");
                        var ccyPair = CcyPair.valueOf(System.console().readLine());
                        ccyPairTrader.printMarketDataBook(ccyPair, 5);
                    }
                    case "16", "REPLAY_PNL" -> ccyPairTrader.startNewExecReplay();
                    case "17", "PRINT_PNLS" -> {
                        Map<CcyPair, Pnl> pnls = ccyPairTrader.getPnls();
                        pnls.forEach((__, pnl) -> {
                            Log.INFO.to(DstSet.APP_STD_OUT, pnl);
                        });
                    }
                    case "18", "TEE_HEDGES" -> {
                        Map<CcyPair, Pnl> nonFlatPnls = ccyPairTrader.getNonflatPnls();
                        if (nonFlatPnls.isEmpty()) {
                            Log.ERROR.to(DstSet.APP_STD_OUT, "Did not find any net positions to hedge!");
                            continue;
                        }
                        nonFlatPnls.forEach((ccyPair, pnl) -> {
                            Log.INFO.to(DstSet.APP_STD_OUT, "Teeing hedge for " + ccyPair + " netQty=" + pnl.netQty() + "\n");
                            ccyPairTrader.printMarketDataBook(ccyPair, 3);
                            Ccy baseCcy = ccyPair.ccy1;
                            Side side = pnl.netQty() > 0 ? Side.SELL : Side.BUY;
                            OrderQty qty = ccyPairTrader.sanitizeOrderQty(Math.abs(pnl.netQty()), ccyPair);
                            Log.INFO.to(DstSet.APP_STD_OUT, "Enter the desired px to " + side + " as a decimal value:");
                            double rawPx = Double.parseDouble(System.console().readLine());
                            var px = ccyPairTrader.sanitizePrice(rawPx, side, ccyPair);
                            var now = now();
                            var transactTime = new TransactTime(now);
                            var ordrID = ClOrdrID.from(now);
                            var order = new Order(ordrID, ccyPair, baseCcy, side, qty, px, TimeInForce.IMMEDIATE_OR_CANCEL, transactTime);
                            Log.INFO.to(DstSet.APP_STD_OUT, "Send order " + order + "? (Y/n) ");
                            var sendStr = System.console().readLine();
                            var send = switch (sendStr) {
                                case "Y", "y", "yes" -> true;
                                case "N", "n", "no" -> false;
                                    default ->
                                        throw new IllegalArgumentException(
                                                "Unrecognized send boolean option: " + sendStr);
                            };
                            if (send) {
                                var result = ccyPairTrader.enqueueManualOrder(order);
                                if (!result) {
                                    Log.ERROR.to(DstSet.APP_STD_OUT, "Could not enqueue teed hedge order! " + order);
                                } else {
                                    placedOrders.put(ordrID.fieldVal(), order);
                                }
                            } else {
                                Log.INFO.to(DstSet.APP_STD_OUT, "'No' chosen, not sending order.");
                            }
                        });
                    }
                    case "19", "PRINT_QUEUE_STATS" -> ccyPairTrader.printQueueStats();
                    case "20", "SET_QUEUES_HEALTHY" -> ccyPairTrader.setQueuesHealthy();
                    default -> Log.ERROR.to(DstSet.APP_STD_OUT, "Didn't recognize " + input);
                }
            } catch (Exception e) {
                Log.ERROR.to(DstSet.APP_STD_OUT, "Uncaught exception in main loop!");
                Log.ERROR.to(DstSet.APP_STD_OUT, Arrays.toString(e.getStackTrace()));
            }

            if (quit) {
                break;
            }
        }

        Log.ERROR.to(DstSet.APP_STD_OUT, "Exiting.");
        System.exit(0);
    }

    private static <T extends FixSessionBridge<?, ?>> void start(T session, String sessionType) throws InterruptedException {
        Log.INFO.to(DstSet.APP_STD_OUT, "Trying to start " + sessionType + " session");
        session.start();
        Log.INFO.to(DstSet.APP_STD_OUT, "Session start() call finished.");
        Log.INFO.to(DstSet.APP_STD_OUT, "Waiting up to 10 seconds for session to complete logon ...");
        int count = 0;
        while (count++ < 10) {
            if (session.loggedOn()) {
                Log.INFO.to(DstSet.APP_STD_OUT, "Logon complete!");
                break;
            }
            Thread.sleep(1000L);
            if (count == 19) {
                Log.ERROR.to(DstSet.APP_STD_OUT, "Logon did not complete.");
            }
        }
    }
}
