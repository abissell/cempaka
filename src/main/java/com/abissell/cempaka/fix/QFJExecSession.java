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
package com.abissell.cempaka.fix;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.function.Supplier;

import com.abissell.javautil.rusty.None;
import com.abissell.javautil.rusty.Result;
import com.abissell.javautil.rusty.Some;
import com.abissell.logutil.Log;
import com.abissell.cempaka.data.FixErr;
import com.abissell.cempaka.data.QFJRes;
import com.abissell.cempaka.util.Dst;
import com.abissell.cempaka.util.DstSet;
import com.abissell.fixbridge.Account;
import com.abissell.fixbridge.AvgPx;
import com.abissell.fixbridge.CachedTradeable;
import com.abissell.cempaka.data.Ccy;
import com.abissell.cempaka.data.CcyPair;
import com.abissell.cempaka.orderid.ClOrdrID;
import com.abissell.fixbridge.CumQty;
import com.abissell.fixbridge.ExecID;
import com.abissell.fixbridge.ExecSessionBridge;
import com.abissell.fixbridge.ExecTransType;
import com.abissell.fixbridge.ExecType;
import com.abissell.fixbridge.FixField;
import com.abissell.fixbridge.FixFieldVal;
import com.abissell.fixbridge.HandlInst;
import com.abissell.fixbridge.LastPx;
import com.abissell.fixbridge.LastShares;
import com.abissell.fixbridge.LeavesQty;
import com.abissell.fixbridge.MsgType;
import com.abissell.fixbridge.OrdStatus;
import com.abissell.fixbridge.OrdType;
import com.abissell.fixbridge.Order;
import com.abissell.fixbridge.OrderID;
import com.abissell.fixbridge.OrderQty;
import com.abissell.fixbridge.OrdrCxlReq;
import com.abissell.fixbridge.OrigClOrdrID;
import com.abissell.fixbridge.ParsedFixMsg;
import com.abissell.fixbridge.Price;
import com.abissell.fixbridge.SettlDate;
import com.abissell.fixbridge.Side;
import com.abissell.fixbridge.StrDate;
import com.abissell.fixbridge.Text;
import com.abissell.fixbridge.TimeInForce;
import com.abissell.fixbridge.TransactTime;

import quickfix.Application;
import quickfix.ConfigError;
import quickfix.DoNotSend;
import quickfix.FieldNotFound;
import quickfix.Message;
import quickfix.NoopStoreFactory;
import quickfix.SLF4JLogFactory;
import quickfix.SessionID;
import quickfix.SessionSettings;
import quickfix.SocketInitiator;
import quickfix.field.Password;
import quickfix.field.Symbol;
import quickfix.fix42.ExecutionReport;
import quickfix.fix42.MessageFactory;
import quickfix.fix42.NewOrderSingle;
import quickfix.fix42.OrderCancelRequest;
import quickfix.fix42.TradingSessionStatus;

public final class QFJExecSession implements Application, ExecSessionBridge<QFJRes, FixErr> {
    private static final quickfix.field.OrdType LIMIT_ORD_TYPE = new quickfix.field.OrdType(OrdType.LIMIT.fixChar);

    private final BaseQFJApplication baseQFJApp;
    private final BaseQFJSession baseSession;
    private final MsgQueue<ParsedFixMsg> msgQueue;
    private final Supplier<LocalDateTime> timestampSrc;

    private final quickfix.field.HandlInst HANDL_INST = new quickfix.field.HandlInst(
            HandlInst.AUTOMATED_EXECUTION_ORDER_PRIVATE_NO_BROKER_INTERVENTION.fixChar);

    public QFJExecSession(
            SessionSettings sessionSettings,
            MsgQueue<ParsedFixMsg> msgQueue,
            Supplier<LocalDateTime> timestampSrc) throws ConfigError {
        var sessionType = FixSessionType.EXECUTION;
        baseQFJApp = new BaseQFJApplication(sessionSettings);
        var messageStoreFactory = new NoopStoreFactory();
        var logFactory = new SLF4JLogFactory(sessionSettings);
        MessageFactory messageFactory = new quickfix.fix42.MessageFactory();

        baseSession = new BaseQFJSession(
                new SocketInitiator(this, messageStoreFactory, sessionSettings, logFactory, messageFactory),
                sessionType);

        this.msgQueue = msgQueue;
        this.timestampSrc = timestampSrc;
    }

    public Result<QFJRes, FixErr> sendNewOrderSingle(Order order) {
        if (!baseSession.loggedOn()) {
            Log.ERROR.to(DstSet.APP_STD_OUT, "Tried to send order but no execution session was logged on!");
            return Result.err(FixErr.SESSION_NOT_FOUND);
        }

        var ccyPair = (CcyPair) order.tradeable();
        quickfix.field.Symbol symbol;
        switch (baseQFJApp.getCachedCcyPair(ccyPair)) {
            case Some<quickfix.field.Symbol>(quickfix.field.Symbol s) -> symbol = s;
            case None<quickfix.field.Symbol>() -> {
                Log.ERROR.to(DstSet.APP_STD_OUT, "Couldn't find cached quickfix.field.Symbol for CcyPair " + ccyPair);
                return Result.err(FixErr.CACHED_FIELD_NOT_FOUND);
            }
            default -> { return criticalError(); }
        }

        var side = order.side();
        final quickfix.field.Side sideField;
        switch (baseQFJApp.getCachedSide(side)) {
            case Some<quickfix.field.Side>(quickfix.field.Side s) -> sideField = s;
            case None<quickfix.field.Side>() -> {
                Log.ERROR.to(DstSet.APP_STD_OUT, "Couldn't find cached quickfix.field.Side for Side " + side);
                return Result.err(FixErr.CACHED_FIELD_NOT_FOUND);
            }
            default -> { return criticalError(); }
        }

        NewOrderSingle newOrder = new NewOrderSingle(
                new quickfix.field.ClOrdID(order.idStr()),
                HANDL_INST,
                symbol,
                sideField,
                new quickfix.field.TransactTime(order.sentTime().fieldVal()),
                LIMIT_ORD_TYPE);

        var base = (Ccy) order.base();
        switch (baseQFJApp.getCachedCcy(base)) {
            case Some<quickfix.field.Currency>(quickfix.field.Currency c) -> newOrder.set(c);
            case None<quickfix.field.Currency>() -> {
                Log.ERROR.to(DstSet.APP_STD_OUT, "Couldn't find cached quickfix.field.Currency for Ccy " + base);
                return Result.err(FixErr.CACHED_FIELD_NOT_FOUND);
            }
            default -> { return criticalError(); }
        }

        newOrder.set(new quickfix.field.OrderQty(order.qty().qty()));
        newOrder.set(new quickfix.field.Price(order.px().px()));
        newOrder.set(new quickfix.field.TimeInForce(order.timeInForce().fixChar));

        return baseSession.send(newOrder);
    }

    public Result<QFJRes, FixErr> sendOrderCancelRequest(OrdrCxlReq request) {
        if (!baseSession.loggedOn()) {
            Log.ERROR
                    .to(DstSet.APP_STD_OUT, "Tried to send order cancel request but no active execution session was logged on!");
            return Result.err(FixErr.SESSION_NOT_FOUND);
        }

        var tradeable = request.order().tradeable();
        final Symbol symbol;
        if (tradeable instanceof CachedTradeable<?> ct)  {
            switch (ct) {
                case CcyPair ccyPair -> {
                    var ccyPairRes = baseQFJApp.getCachedCcyPair(ccyPair);
                    switch (ccyPairRes) {
                        case Some<Symbol>(Symbol s) -> symbol = s;
                        case None<Symbol>() -> {
                            Log.ERROR.to(DstSet.APP_STD_OUT, "Could not find cached symbol for CcyPair! " + ccyPair);
                            return Result.err(FixErr.CACHED_FIELD_NOT_FOUND);
                        }
                        default -> { return criticalError(); }
                    }
                }
                default -> {
                    Log.ERROR.to(DstSet.APP_STD_OUT, "Did not know how to lookup CachedTradeable " + ct);
                    return Result.err(FixErr.CACHED_FIELD_NOT_FOUND);
                }
            }
        } else {
            symbol = new Symbol(tradeable.symbol());
        }

        var requestSide = request.order().side();
        final quickfix.field.Side side;
        switch (baseQFJApp.getCachedSide(requestSide)) {
            case Some<quickfix.field.Side>(quickfix.field.Side s) -> side = s;
            case None<quickfix.field.Side>() -> {
                Log.ERROR.to(DstSet.APP_STD_OUT, "Could not find cached side for Side! :\n" + requestSide);
                return Result.err(FixErr.CACHED_FIELD_NOT_FOUND);
            }
            default -> { return criticalError(); }
        }

        var cancelRequest = new OrderCancelRequest(
                new quickfix.field.OrigClOrdID(request.order().idStr()),
                new quickfix.field.ClOrdID(request.idStr()),
                symbol,
                side,
                new quickfix.field.TransactTime(request.sentTime().fieldVal()));

        cancelRequest.set(new quickfix.field.OrderQty(request.order().qty().qty()));

        return baseSession.send(cancelRequest);
    }

    @Override
    public void onCreate(SessionID sessionID) {
        baseQFJApp.onCreate();
    }

    @Override
    public void onLogon(SessionID sessionID) {
        baseQFJApp.onLogon(baseSession.logPrefix());
    }

    @Override
    public void onLogout(SessionID sessionID) {
        baseQFJApp.onLogout(baseSession.logPrefix());
    }

    @Override
    public void toAdmin(Message message, SessionID sessionID) {
        try {
            String msgType = message.getHeader().getString(quickfix.field.MsgType.FIELD);
            if (MsgType.LOGON == MsgType.from(msgType)) {
                Log.ERROR.to(Dst.STD_OUT, "Enter the password for the Execution Session:");
                String password = String.valueOf(System.console().readPassword());
                message.setString(Password.FIELD, password);
            }
        } catch (FieldNotFound e) {
            Log.ERROR.to(DstSet.APP_STD_OUT, "Caught FieldNotFound exception: " + e);
        }
    }

    @Override
    public void fromAdmin(Message message, SessionID sessionID) {
        baseQFJApp.fromAdmin(message, baseSession);
    }

    @Override
    public void toApp(Message message, SessionID sessionID) throws DoNotSend {
        boolean possDupFlag;
        try {
            possDupFlag = message.getHeader().getBoolean(quickfix.field.PossDupFlag.FIELD);
        } catch (FieldNotFound e) {
            possDupFlag = false;
        }

        switch (message) {
            case NewOrderSingle order -> {
                if (possDupFlag) {
                    Log.ERROR.to(DstSet.APP_STD_OUT, baseSession.logPrefix(),
                            "Suppressing resend on NewOrderSingle with PossDupFlag set to true! Message:");
                    Log.ERROR.to(DstSet.APP_STD_OUT, baseSession.logPrefix(), order);
                    throw new DoNotSend();
                }
                onNewOrder(order);
            }
            case OrderCancelRequest cancel -> {
            }
            default -> {
                Log.ERROR.to(DstSet.APP_STD_OUT, baseSession.logPrefix(),
                        "toApp saw unrecognized message of type: " + message.getClass() + ", Message:");
                Log.ERROR.to(DstSet.APP_STD_OUT, baseSession.logPrefix(), message);
            }
        }
    }

    @Override
    public void fromApp(Message message, SessionID sessionID) {
        switch (message) {
            case ExecutionReport er -> onExecutionReport(er);
            case TradingSessionStatus tss -> onTradingSessionStatus(tss);
            default -> Log.ERROR.to(DstSet.APP_STD_OUT, baseSession.logPrefix(),
                    "Unrecognized fromApp message of type: " + message.getClass() + ", message:\n" + message);
        }
    }

    private void onNewOrder(NewOrderSingle newOrderSingle) {
        Log.DEBUG.to(DstSet.APP_STD_OUT, baseSession.logPrefix(), "Sending NewOrderSingle:");
        Log.DEBUG.to(DstSet.APP_STD_OUT, baseSession.logPrefix(), newOrderSingle);
        read(newOrderSingle);
    }

    private void read(NewOrderSingle order) {
        try {
            var fields = new EnumMap<FixField, FixFieldVal>(FixField.class);
            fields = baseQFJApp.readHeaderFields(order, fields);
            fields.put(FixField.CL_ORD_ID, ClOrdrID.from(order.getClOrdID().getValue()));
            fields.put(FixField.TRANSACT_TIME, new TransactTime(order.getTransactTime().getValue()));
            fields.put(FixField.SYMBOL, CcyPair.from(order.getSymbol().getValue()));
            fields.put(FixField.CURRENCY, Ccy.fromFixStr(order.getCurrency().getValue()));
            fields.put(FixField.SIDE, Side.fromFixChar(order.getSide().getValue()));
            fields.put(FixField.ORDER_QTY, new OrderQty(order.getOrderQty().getValue()));
            fields.put(FixField.PRICE, new Price(order.getPrice().getValue()));
            fields.put(FixField.TIME_IN_FORCE, TimeInForce.fromFixChar(order.getTimeInForce().getValue()));
            fields.put(FixField.ORD_TYPE, OrdType.fromFixChar(order.getOrdType().getValue()));
            fields.put(FixField.HANDL_INST, HandlInst.fromFixChar(order.getHandlInst().getValue()));
        } catch (Exception e) {
            Log.ERROR.to(DstSet.APP_STD_OUT, baseSession.logPrefix(), "ERROR parsing NewOrderSingle: " + order);
            Log.ERROR.to(DstSet.APP_STD_OUT, baseSession.logPrefix(), e.getMessage());
        }
    }

    private void onExecutionReport(ExecutionReport executionReport) {
        var fields = read(executionReport);
        var parsedFixMsg = new ParsedFixMsg(fields, timestampSrc.get());
        msgQueue.offer(parsedFixMsg);
    }

    private EnumMap<FixField, FixFieldVal> read(ExecutionReport executionReport) {
        var fields = new EnumMap<FixField, FixFieldVal>(FixField.class);
        try {
            baseQFJApp.readHeaderFields(executionReport, fields);

            fields.put(FixField.ORDER_ID, new OrderID(executionReport.getOrderID().getValue()));
            fields.put(FixField.CL_ORD_ID, ClOrdrID.from(executionReport.getClOrdID().getValue()));
            if (executionReport.isSetOrigClOrdID()) { // Only set for cancels and cancel-replaces
                fields.put(FixField.ORIG_CL_ORD_ID, OrigClOrdrID.from(executionReport.getOrigClOrdID().getValue()));
            }
            fields.put(FixField.EXEC_ID, new ExecID(executionReport.getExecID().getValue()));
            if (executionReport.isSetHandlInst()) {
                // TODO: Could reference cached version
                fields.put(FixField.HANDL_INST, HandlInst.fromFixChar(executionReport.getHandlInst().getValue()));
            }
            // TODO: Can probably be cached
            fields.put(FixField.ACCOUNT, new Account(executionReport.getAccount().getValue()));
            fields.put(FixField.EXEC_TRANS_TYPE, ExecTransType.fromFixChar(executionReport.getExecTransType().getValue()));
            fields.put(FixField.EXEC_TYPE, ExecType.fromFixChar(executionReport.getExecType().getValue()));
            fields.put(FixField.ORD_STATUS, OrdStatus.fromFixChar(executionReport.getOrdStatus().getValue()));
            fields.put(FixField.SYMBOL, CcyPair.from(executionReport.getSymbol().getValue()));
            if (executionReport.isSetText()) {
                fields.put(FixField.TEXT, new Text(executionReport.getText().getValue()));
            }
            fields.put(FixField.CURRENCY, Ccy.fromFixStr(executionReport.getCurrency().getValue()));
            if (executionReport.isSetLastPx()) {
                fields.put(FixField.LAST_PX, new LastPx(executionReport.getLastPx().getValue()));
            }
            if (executionReport.isSetLastShares()) {
                fields.put(FixField.LAST_SHARES, new LastShares(executionReport.getLastShares().getValue()));
            }
            fields.put(FixField.SIDE, Side.fromFixChar(executionReport.getSide().getValue()));
            fields.put(FixField.ORDER_QTY, new OrderQty(executionReport.getOrderQty().getValue()));
            fields.put(FixField.PRICE, new Price(executionReport.getPrice().getValue()));
            if (executionReport.isSetOrdType()) {
                fields.put(FixField.ORD_TYPE, OrdType.fromFixChar(executionReport.getOrdType().getValue()));
            }
            if (executionReport.isSetTimeInForce()) {
                fields.put(FixField.TIME_IN_FORCE, TimeInForce.fromFixChar(executionReport.getTimeInForce().getValue()));
            }
            fields.put(FixField.TRANSACT_TIME, new TransactTime(executionReport.getTransactTime().getValue()));
            // TODO: Maybe cache, but will JVM String interning take care of it?
            fields.put(FixField.SETTL_DATE, new SettlDate(new StrDate(executionReport.getField(new quickfix.field.SettlDate()).getValue())));
            // TODO: Cache zero values on qtys? (probably not)
            fields.put(FixField.LEAVES_QTY, new LeavesQty(executionReport.getLeavesQty().getValue()));
            fields.put(FixField.CUM_QTY, new CumQty(executionReport.getCumQty().getValue()));
            fields.put(FixField.AVG_PX, new AvgPx(executionReport.getAvgPx().getValue()));
            return fields;
        } catch (Exception e) {
            // TODO: Should let these Exceptions bubble up once we are confident in parsing
            Log.ERROR.to(DstSet.APP_STD_OUT, baseSession.logPrefix(), "ERROR parsing ExecutionReport: " + executionReport);
            Log.ERROR.to(DstSet.APP_STD_OUT, baseSession.logPrefix(), e.getMessage());
            Log.ERROR.to(DstSet.APP_STD_OUT, baseSession.logPrefix(), Arrays.toString(e.getStackTrace()));
            return new EnumMap<>(FixField.class);
        }
    }

    private void onTradingSessionStatus(TradingSessionStatus tss) {
        var fields = baseQFJApp.onTradingSessionStatus(tss, baseSession.logPrefix());
        msgQueue.offer(new ParsedFixMsg(fields, timestampSrc.get()));
    }

    @Override
    public Result<QFJRes, FixErr> start() {
        return baseSession.start();
    }

    @Override
    public Result<QFJRes, FixErr> sendResendRequest(int begin, int end) {
        return baseSession.sendResendRequest(begin, end);
    }

    @Override
    public void stop() {
        baseSession.stop();
    }

    @Override
    public void stop(boolean forceDisconnect) {
        baseSession.stop(forceDisconnect);
    }

    @Override
    public boolean stopped() {
        return baseSession.stopped();
    }

    @Override
    public boolean loggedOn() {
        return baseSession.loggedOn();
    }

    @Override
    public int queueSize() {
        return baseSession.queueSize();
    }

    private Result<QFJRes, FixErr> criticalError() {
        Log.ERROR.to(DstSet.APP_STD_OUT, "issue with pattern matching");
        return Result.err(FixErr.CRITICAL_ERROR);
    }
}
