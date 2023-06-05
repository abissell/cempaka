package com.abissell.fixbridge;

import java.time.LocalDateTime;
import java.util.EnumMap;

public /* value */ record ParsedFixMsg(
        EnumMap<FixField, FixFieldVal> msg,
        LocalDateTime recvdTime) {
    public FixFieldVal get(FixField field) {
        return msg.get(field);
    }

    public boolean isPossDup() {
        var possDupFlag = (PossDupFlag) msg.get(FixField.POSS_DUP_FLAG);
        return possDupFlag == PossDupFlag.POSS_DUP;
    }

    public MsgType msgType() {
        return (MsgType) msg.get(FixField.MSG_TYPE);
    }
}
