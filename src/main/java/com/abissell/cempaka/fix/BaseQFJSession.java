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

import com.abissell.javautil.rusty.Result;
import com.abissell.logutil.Log;
import com.abissell.cempaka.data.FixErr;
import com.abissell.cempaka.data.QFJRes;
import com.abissell.cempaka.util.DstSet;
import com.abissell.fixbridge.FixSessionBridge;
import com.abissell.fixbridge.SessionState;

import com.abissell.fixbridge.SessionState.Active;
import quickfix.ConfigError;
import quickfix.Message;
import quickfix.Session;
import quickfix.SessionNotFound;
import quickfix.SocketInitiator;
import quickfix.field.BeginSeqNo;
import quickfix.field.EndSeqNo;
import quickfix.fix42.ResendRequest;

public final class BaseQFJSession implements FixSessionBridge<QFJRes, FixErr> {

    private final SocketInitiator initiator;
    private final FixSessionType sessionType;
    private final String inactiveSessionPrefix;

    private volatile SessionState sessionState = SessionState.inactive();

    BaseQFJSession(SocketInitiator initiator, FixSessionType sessionType) {
        this.initiator = initiator;
        this.sessionType = sessionType;
        this.inactiveSessionPrefix = "[INACTIVE " + sessionType + " SESSION] ";
    }

    public Result<QFJRes, FixErr> start() {
        try {
            initiator.start();
        } catch (ConfigError e) {
            Log.ERROR.to(DstSet.APP_STD_OUT, "BaseQuickFixJSession hit error on initiator start");
            Log.ERROR.to(DstSet.APP_STD_OUT, e);
            return Result.err(FixErr.CONFIG_ERROR);
        }

        var sessions = initiator.getSessions();
        if (sessions.size() != 1) {
            Log.ERROR.to(DstSet.APP_STD_OUT, "BaseQuickFixJSession SocketInitiator had 0 or multiple sessions:");
            sessions.forEach(session -> Log.ERROR.to(DstSet.APP_STD_OUT, session));
            return Result.err(FixErr.CONFIG_ERROR);
        } else {
            var sessionID = sessions.get(0);
            var logPrefix = "[" + sessionID + "] ";
            var sessionDetails = new QFJSessionDetails(sessionID, logPrefix);
            sessionState = new SessionState.Active<>(sessionDetails);
            return Result.of(new QFJRes(true));
        }
    }

    public Result<QFJRes, FixErr> sendResendRequest(int begin, int end) {
        var resendRequest = new ResendRequest(new BeginSeqNo(begin), new EndSeqNo(end));
        Log.WARN.to(DstSet.APP_STD_OUT, "Sending ResendRequest:");
        Log.WARN.to(DstSet.APP_STD_OUT, resendRequest);
        return send(resendRequest);
    }

    public void stop() {
        stop(false);
    }

    public void stop(boolean forceDisconnect) {
        sessionState = SessionState.inactive();
        initiator.stop(forceDisconnect);
    }

    public boolean stopped() {
        return !sessionState.active();
    }

    public boolean loggedOn() {
        return sessionState.active() && initiator.isLoggedOn();
    }

    public int queueSize() {
        return initiator.getQueueSize();
    }

    Result<QFJRes, FixErr> send(Message message) {
        return switch (sessionState) {
            case Active<QFJSessionDetails>(QFJSessionDetails sessionDetails) -> {
            // case SessionState.Active<QuickFixJSessionDetails>(QuickFixJSessionDetails(SessionID sessionID, String logPrefix) details) -> {
            // TODO: Would like to use this line with nested record pattern but it kills Eclipse LSP
                Log.DEBUG.to(DstSet.APP_STD_OUT, sessionDetails.logPrefix(), "Sending message:");
                Log.DEBUG.to(DstSet.APP_STD_OUT, sessionDetails.logPrefix(), message);
                try {
                    boolean sendResult = Session.sendToTarget(message, sessionDetails.sessionID());
                    yield Result.of(new QFJRes(sendResult));
                } catch (SessionNotFound e) {
                    Log.ERROR.to(DstSet.APP_STD_OUT, e);
                    yield Result.err(FixErr.SESSION_NOT_FOUND);
                }
            }
            case SessionState.Inactive<QFJSessionDetails>() -> {
                Log.WARN.to(DstSet.APP_STD_OUT, "On session type " + sessionType + " tried to send Message=" + message + " but session was not found!");
                yield Result.err(FixErr.SESSION_NOT_FOUND);
            }
            case null, default -> {
                Log.ERROR.to(DstSet.APP_STD_OUT, "Couldn't correctly match sessionState to associated records!");
                yield Result.err(FixErr.CRITICAL_ERROR);
            }
        };
    }

    String logPrefix() {
        if (sessionState instanceof SessionState.Active<QFJSessionDetails>(QFJSessionDetails sessionDetails)) {
            return sessionDetails.logPrefix();
        } else {
            return inactiveSessionPrefix;
        }
    }
}
