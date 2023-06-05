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
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.function.Supplier;

import com.abissell.javautil.rusty.Result;
import com.abissell.logutil.Log;
import com.abissell.cempaka.data.FixErr;
import com.abissell.cempaka.data.QFJRes;
import com.abissell.cempaka.util.Dst;
import com.abissell.cempaka.util.DstSet;
import com.abissell.cempaka.data.CcyPair;
import com.abissell.fixbridge.FixField;
import com.abissell.fixbridge.FixFieldVal;
import com.abissell.fixbridge.MDEntries;
import com.abissell.fixbridge.MDEntry;
import com.abissell.fixbridge.MDEntryPx;
import com.abissell.fixbridge.MDEntrySize;
import com.abissell.fixbridge.MDEntryType;
import com.abissell.fixbridge.MDReqID;
import com.abissell.fixbridge.MktDataSessionBridge;
import com.abissell.fixbridge.MktDataSubscriptionReq;
import com.abissell.fixbridge.NoMDEntries;
import com.abissell.fixbridge.ParsedFixMsg;
import com.abissell.fixbridge.SubscriptionRequestType;
import com.abissell.fixbridge.Ticker;

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
import quickfix.fix42.MarketDataRequest;
import quickfix.fix42.MarketDataSnapshotFullRefresh;
import quickfix.fix42.TradingSessionStatus;

public final class QFJMktDataSession implements Application, MktDataSessionBridge<QFJRes, FixErr> {
    private final BaseQFJApplication baseQFJApp;
    private final BaseQFJSession baseSession;
    private final MsgQueue<ParsedFixMsg> msgQueue;
    private final Supplier<LocalDateTime> timestampSrc;

    public QFJMktDataSession(
            SessionSettings sessionSettings,
            MsgQueue<ParsedFixMsg> msgQueue,
            Supplier<LocalDateTime> timestampSrc) throws ConfigError {
        this.baseQFJApp = new BaseQFJApplication(sessionSettings);
        this.msgQueue = msgQueue;
        this.timestampSrc = timestampSrc;

        var messageStoreFactory = new NoopStoreFactory();
        var logFactory = new SLF4JLogFactory(sessionSettings);
        var messageFactory = new quickfix.fix42.MessageFactory();

        baseSession = new BaseQFJSession(
                new SocketInitiator(this, messageStoreFactory, sessionSettings, logFactory, messageFactory),
                FixSessionType.MKT_DATA);
    }

    @Override
    public Result<QFJRes, FixErr> subscribe(MktDataSubscriptionReq request,
            Collection<? extends Ticker> tickers
        ) {
        if (!baseSession.loggedOn()) {
            Log.ERROR.to(DstSet.APP_STD_OUT, "Requested market data updates but no active session was present!");
            return Result.err(FixErr.SESSION_NOT_FOUND);
        }

        var qfjRequest = new MarketDataRequest(
                new quickfix.field.MDReqID(request.mdReqID().id()),
                new quickfix.field.SubscriptionRequestType(request.subReqType().fixChar),
                new quickfix.field.MarketDepth(request.marketDepth().depth()));
        qfjRequest.set(new quickfix.field.MDUpdateType(quickfix.field.MDUpdateType.FULL_REFRESH));

        request.minQty().ifPresent(minQty -> qfjRequest.setField(new quickfix.field.MinQty(minQty.minQty())));

        MarketDataRequest.NoMDEntryTypes noMDEntryTypes = new MarketDataRequest.NoMDEntryTypes();
        noMDEntryTypes.set(new quickfix.field.MDEntryType(quickfix.field.MDEntryType.BID));
        qfjRequest.addGroup(noMDEntryTypes);
        noMDEntryTypes.set(new quickfix.field.MDEntryType(quickfix.field.MDEntryType.OFFER));
        qfjRequest.addGroup(noMDEntryTypes);

        MarketDataRequest.NoRelatedSym noRelatedSym = new MarketDataRequest.NoRelatedSym();
        tickers.forEach(ticker -> {
            noRelatedSym.set(new quickfix.field.Symbol(ticker.symbol()));
            qfjRequest.addGroup(noRelatedSym);
        });

        Log.WARN.to(DstSet.APP_STD_OUT, baseSession.logPrefix(), "Sending request for market data updates on " + tickers +
                "... ");
        Log.WARN.to(DstSet.APP_STD_OUT, baseSession.logPrefix(), "MarketDataRequest:\n" + request);
        var reqResult = baseSession.send(qfjRequest);
        Log.WARN.to(DstSet.APP_STD_OUT, baseSession.logPrefix(), "Sent.");
        return reqResult;
    }

    @Override
    public Result<QFJRes, FixErr> unsubscribe(MDReqID mdReqID, Collection<? extends Ticker> tickers) {
        if (!baseSession.loggedOn()) {
            Log.ERROR.to(DstSet.APP_STD_OUT, baseSession.logPrefix(), "Tried to cancel market data updates but no active " +
                    "session was present!");
            return Result.err(FixErr.SESSION_NOT_FOUND);
        }

        MarketDataRequest qfjRequest = new MarketDataRequest(
                new quickfix.field.MDReqID(mdReqID.id()),
                new quickfix.field.SubscriptionRequestType(SubscriptionRequestType.UNSUBSCRIBE.fixChar),
                new quickfix.field.MarketDepth(0));
        qfjRequest.set(new quickfix.field.MDUpdateType(quickfix.field.MDUpdateType.FULL_REFRESH));

        MarketDataRequest.NoMDEntryTypes noMDEntryTypes = new MarketDataRequest.NoMDEntryTypes();
        noMDEntryTypes.set(new quickfix.field.MDEntryType(quickfix.field.MDEntryType.BID));
        qfjRequest.addGroup(noMDEntryTypes);
        noMDEntryTypes.set(new quickfix.field.MDEntryType(quickfix.field.MDEntryType.OFFER));
        qfjRequest.addGroup(noMDEntryTypes);

        MarketDataRequest.NoRelatedSym noRelatedSym = new MarketDataRequest.NoRelatedSym();
        tickers.forEach(ticker -> {
            noRelatedSym.set(new quickfix.field.Symbol(ticker.symbol()));
            qfjRequest.addGroup(noRelatedSym);
        });

        Log.WARN.to(DstSet.APP_STD_OUT, baseSession.logPrefix(), "Sending disable for market data updates on " + tickers +
                "... ");
        return baseSession.send(qfjRequest);
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
            if (quickfix.field.MsgType.LOGON.equals(msgType)) {
                Log.ERROR.to(Dst.STD_OUT, "Enter the password for the Market Data Session:");
                String password = String.valueOf(System.console().readPassword());
                message.setString(quickfix.field.Password.FIELD, password);
            }
        } catch (FieldNotFound e) {
            Log.ERROR.to(Dst.STD_OUT, "Caught FieldNotFound exception: " + e);
        }
    }

    @Override
    public void fromAdmin(Message message, SessionID sessionID) {
        baseQFJApp.fromAdmin(message, baseSession);
    }

    @Override
    public void toApp(Message message, SessionID sessionID) throws DoNotSend {

    }

    @Override
    public void fromApp(Message message, SessionID sessionID) {
        switch (message) {
            case MarketDataSnapshotFullRefresh snapshotRefresh -> onSnapshotRefresh(snapshotRefresh);
            case TradingSessionStatus status -> onTradingSessionStatus(status);
            default -> Log.ERROR.to(DstSet.APP_STD_OUT, baseSession.logPrefix(),
                    "Unrecognized fromApp message of type: " + message.getClass() + ", message:\n" + message);
        }
    }

    private void onSnapshotRefresh(MarketDataSnapshotFullRefresh snapshot) {
        var fields = read(snapshot);
        var parsedFixMsg = new ParsedFixMsg(fields, timestampSrc.get());
        msgQueue.offer(parsedFixMsg);
        if (!parsedFixMsg.isPossDup()) {
            Log.INFO.to(DstSet.MKT_DATA, snapshot::toString);
        }
    }

    private EnumMap<FixField, FixFieldVal> read(MarketDataSnapshotFullRefresh snapshot) {
        var map = new EnumMap<FixField, FixFieldVal>(FixField.class);
        try {
            map = baseQFJApp.readHeaderFields(snapshot, map);

            var mdReqID = new MDReqID(snapshot.getMDReqID().getValue());
            map.put(FixField.MD_REQ_ID, mdReqID);
            var ccyPair = CcyPair.from(snapshot.getSymbol().getValue());
            map.put(FixField.SYMBOL, ccyPair);
            int numEntries = snapshot.getNoMDEntries().getValue();
            var noMDEntries = new NoMDEntries(numEntries);
            map.put(FixField.NO_MD_ENTRIES, noMDEntries);
            var mdEntriesList = new ArrayList<MDEntry>(numEntries);
            for (int i = 0; i < numEntries; i++) {
                // TODO: Can we read this without creating the Group object?
                var group = new quickfix.fix42.MarketDataSnapshotFullRefresh.NoMDEntries();
                final var groupIdx = i + 1;
                snapshot.getGroup(groupIdx, group);
                var mdEntryType = MDEntryType.fromFixChar(group.getMDEntryType().getValue());
                var mdEntryPx = new MDEntryPx(group.getMDEntryPx().getValue());
                var mdEntrySize = new MDEntrySize(group.getMDEntrySize().getValue());
                mdEntriesList.add(new MDEntry(mdEntryType, mdEntryPx, mdEntrySize));
            }
            var mdEntries = new MDEntries(mdEntriesList);
            map.put(FixField.MD_ENTRIES, mdEntries);
            baseQFJApp.readTrailerField(snapshot, map);
        } catch (Exception e) {
            Log.ERROR.to(DstSet.APP_STD_OUT, baseSession.logPrefix(),
                    "ERROR parsing MarketDataSnapshotFullRefresh: " + snapshot);
            Log.ERROR.to(DstSet.APP_STD_OUT, baseSession.logPrefix(), e.getMessage());
        }
        return map;
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
}
