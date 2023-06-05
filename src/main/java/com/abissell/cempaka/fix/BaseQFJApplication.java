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

import java.util.Arrays;
import java.util.EnumMap;
import java.util.function.Function;

import com.abissell.javautil.rusty.Opt;
import com.abissell.javautil.rusty.Some;
import com.abissell.logutil.Log;
import com.abissell.cempaka.util.DstSet;
import com.abissell.cempaka.data.Ccy;
import com.abissell.cempaka.data.CcyPair;
import com.abissell.fixbridge.CheckSum;
import com.abissell.fixbridge.FixField;
import com.abissell.fixbridge.FixFieldVal;
import com.abissell.fixbridge.MsgSeqNum;
import com.abissell.fixbridge.MsgType;
import com.abissell.fixbridge.PossDupFlag;
import com.abissell.fixbridge.PossResend;
import com.abissell.fixbridge.SenderCompID;
import com.abissell.cempaka.data.SenderID;
import com.abissell.fixbridge.SendingTime;
import com.abissell.fixbridge.SendrID;
import com.abissell.fixbridge.Side;
import com.abissell.fixbridge.TargetCompID;
import com.abissell.cempaka.data.TargetID;
import com.abissell.fixbridge.TargtID;
import com.abissell.fixbridge.Text;

import quickfix.FieldNotFound;
import quickfix.Message;
import quickfix.SessionSettings;
import quickfix.fix42.Heartbeat;
import quickfix.fix42.Logon;
import quickfix.fix42.Logout;
import quickfix.fix42.SequenceReset;
import quickfix.fix42.TradingSessionStatus;

final class BaseQFJApplication {
    private final SessionSettings sessionSettings;

    private final EnumMap<Side, quickfix.field.Side> sideCache;
    private final EnumMap<MsgType, quickfix.field.MsgType> msgTypeCache;
    private final EnumMap<CcyPair, quickfix.field.Symbol> ccyPairCache;
    private final EnumMap<Ccy, quickfix.field.Currency> ccyCache;

    private final Function<String, ? extends SenderCompID> senderIdReader =
        constructReader(SenderID::from, SendrID::new);
    private final Function<String, ? extends TargetCompID> targetIdReader =
        constructReader(TargetID::from, TargtID::new);

    BaseQFJApplication(SessionSettings sessionSettings) {
        this.sessionSettings = sessionSettings;

        this.sideCache = FixField.buildEnumCache(
                Side.values(),
                side -> new quickfix.field.Side(side.fixChar),
                () -> new EnumMap<>(Side.class));
        this.msgTypeCache = FixField.buildEnumCache(
                MsgType.values(),
                msgType -> new quickfix.field.MsgType(msgType.fixStr),
                () -> new EnumMap<>(MsgType.class));
        this.ccyPairCache = FixField.buildEnumCache(
                CcyPair.values(),
                ccyPair -> new quickfix.field.Symbol(ccyPair.fixStr),
                () -> new EnumMap<>(CcyPair.class));
        this.ccyCache = FixField.buildEnumCache(
                Ccy.values(),
                ccy -> new quickfix.field.Currency(ccy.fixStr),
                () -> new EnumMap<>(Ccy.class));
    }

    void onCreate() {
        Log.WARN.to(DstSet.APP_STD_OUT, "Created new session with settings:\n" + sessionSettings);
    }

    void onLogon(String logPrefix) {
        Log.WARN.to(DstSet.APP_STD_OUT, logPrefix, "Logon completed.");
    }

    void onLogout(String logPrefix) {
        Log.WARN.to(DstSet.APP_STD_OUT, logPrefix, "onLogout() called.");
    }

    void fromAdmin(Message message, BaseQFJSession baseQFJSession) {
        var logPrefix = baseQFJSession.logPrefix();
        switch (message) {
            case Heartbeat heartbeat -> {
                Log.DEBUG.to(DstSet.APP_STD_OUT, logPrefix, "Heartbeat:");
                Log.DEBUG.to(DstSet.APP_STD_OUT, heartbeat);
            }
            case Logon logon -> {
                Log.WARN.to(DstSet.APP_STD_OUT, logPrefix, "Received Logon.");
                Log.WARN.to(DstSet.APP_STD_OUT, logon);
            }
            case Logout logout -> {
                Log.WARN.to(DstSet.APP_STD_OUT, logPrefix, "Received Logout.");
                Log.WARN.to(DstSet.APP_STD_OUT, logout);
                readLogout(logout, logPrefix);
                if (!baseQFJSession.stopped()) {
                    Log.ERROR.to(DstSet.APP_STD_OUT, logPrefix, "Initiating logout process after received logout request.");
                    baseQFJSession.stop();
                }
            }
            case TradingSessionStatus sessionStatus -> {
            }
            case SequenceReset sequenceReset -> {
                onSequenceReset(sequenceReset, logPrefix);
            }
            default -> {
                Log.ERROR.to(DstSet.APP_STD_OUT, "Unrecognized fromAdmin message with type=" + message.getClass() + ":");
                Log.ERROR.to(DstSet.APP_STD_OUT, message);
            }
        }
    }

    private EnumMap<FixField, FixFieldVal> readLogout(Logout logout, String logPrefix) {
        EnumMap<FixField, FixFieldVal> map = new EnumMap<>(FixField.class);
        try {
            Log.INFO.to(DstSet.APP_STD_OUT, logPrefix, "---------------- Logout -----------------");
            readHeaderFields(logout, map);
            if (logout.isSetText()) {
                var text = new Text(logout.getText().getValue());
                map.put(FixField.TEXT, text);
            }
            readTrailerField(logout, map);
            Log.INFO.to(DstSet.APP_STD_OUT, logPrefix, map);
            Log.INFO.to(DstSet.APP_STD_OUT, logPrefix, "-----------------------------------------");
        } catch (Exception e) {
            Log.ERROR.to(DstSet.APP_STD_OUT, logPrefix, "ERROR parsing Logout: " + logout);
            Log.ERROR.to(DstSet.APP_STD_OUT, logPrefix, e.getMessage());
            Log.ERROR.to(DstSet.APP_STD_OUT, logPrefix, Arrays.toString(e.getStackTrace()));
        }
        return map;
    }

    EnumMap<FixField, FixFieldVal> readHeaderFields(Message message, EnumMap<FixField, FixFieldVal> to) throws FieldNotFound {
        var header = message.getHeader();
        var msgType = MsgType.from(header.getString(quickfix.field.MsgType.FIELD));
        to.put(FixField.MSG_TYPE, msgType);
        var msgSeqNum = new MsgSeqNum(header.getInt(quickfix.field.MsgSeqNum.FIELD));
        to.put(FixField.MSG_SEQ_NUM, msgSeqNum);
        var senderCompID = senderIdReader.apply(header.getString(quickfix.field.SenderCompID.FIELD));
        to.put(FixField.SENDER_COMP_ID, senderCompID);
        var targetCompID = targetIdReader.apply(header.getString(quickfix.field.TargetCompID.FIELD));
        to.put(FixField.TARGET_COMP_ID, targetCompID);
        if (header.isSetField(quickfix.field.PossDupFlag.FIELD)) {
            var possDupFlag = PossDupFlag.from(header.getBoolean(quickfix.field.PossDupFlag.FIELD));
            to.put(FixField.POSS_DUP_FLAG, possDupFlag);
        }
        if (header.isSetField(quickfix.field.PossResend.FIELD)) {
            var possResend = PossResend.from(header.getBoolean(quickfix.field.PossResend.FIELD));
            to.put(FixField.POSS_RESEND, possResend);
        }
        var sendTime = new SendingTime(header.getUtcTimeStamp(quickfix.field.SendingTime.FIELD));
        to.put(FixField.SENDING_TIME, sendTime);
        return to;
    }

    EnumMap<FixField, FixFieldVal> readTrailerField(Message message, EnumMap<FixField, FixFieldVal> to) throws FieldNotFound {
        var checkSum = new CheckSum(message.getTrailer().getInt(quickfix.field.CheckSum.FIELD));
        to.put(FixField.CHECK_SUM, checkSum);
        return to;
    }

    void onSequenceReset(SequenceReset sequenceReset, String logPrefix) {

    }

    EnumMap<FixField, FixFieldVal> onTradingSessionStatus(TradingSessionStatus tss, String logPrefix) {
        Log.ERROR.to(DstSet.APP_STD_OUT, logPrefix, "Got TradingSessionStatus:");
        Log.ERROR.to(DstSet.APP_STD_OUT, logPrefix, tss);
        var fields = new EnumMap<FixField, FixFieldVal>(FixField.class);
        fields.put(FixField.MSG_TYPE, MsgType.TRADING_SESSION_STATUS);
        return fields;
    }

    Opt<quickfix.field.Side> getCachedSide(Side side) {
        return Opt.ofNullable(sideCache.get(side));
    }

    Opt<quickfix.field.MsgType> getCachedMsgType(MsgType msgType) {
        return Opt.ofNullable(msgTypeCache.get(msgType));
    }

    Opt<quickfix.field.Symbol> getCachedCcyPair(CcyPair ccyPair) {
        return Opt.ofNullable(ccyPairCache.get(ccyPair));
    }

    Opt<quickfix.field.Currency> getCachedCcy(Ccy ccy) {
        return Opt.ofNullable(ccyCache.get(ccy));
    }

    private <T> Function<String, T> constructReader(
            Function<String, Opt<? extends T>> cacheLookup,
            Function<String, ? extends T> initNew) {
        return str -> {
            var optVal = cacheLookup.apply(str);
            // IntelliJ flags spurious error here
            if (optVal instanceof Some<? extends T>(T t)) {
                return t;
            } else {
                return initNew.apply(str);
            }
        };
    }
}
