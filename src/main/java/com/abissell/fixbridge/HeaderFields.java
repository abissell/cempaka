package com.abissell.fixbridge;

import com.abissell.javautil.rusty.Opt;

public /* value */ record HeaderFields(
        MsgType msgType,
        MsgSeqNum seqNum,
        SenderCompID senderCompID,
        TargetCompID targetCompID,
        Opt<PossDupFlag> possDupFlg,
        Opt<PossResend> possResnd,
        SendingTime sendingTime) {
}
