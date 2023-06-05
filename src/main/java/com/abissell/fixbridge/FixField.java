package com.abissell.fixbridge;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public enum FixField {
    ACCOUNT(1, "Account"),
    AVG_PX(6, "AvgPx"),
    BEGIN_SEQ_NO(7, "BeginSeqNo"),
    CHECK_SUM(10, "CheckSum"),
    CL_ORD_ID(11, "ClOrdID"),
    CUM_QTY(14, "CumQty"),
    CURRENCY(15, "Currency"),
    END_SEQ_NO(16, "EndSeqNo"),
    EXEC_ID(17, "ExecID"),
    EXEC_TRANS_TYPE(20, "ExecTransType"),
    HANDL_INST(21, "HandlInst"),
    LAST_PX(31, "LastPx"),
    LAST_SHARES(32, "LastShares"),
    MSG_SEQ_NUM(34, "MsgSeqNum"),
    MSG_TYPE(35, "MsgType"),
    ORDER_ID(37, "OrderID"),
    ORDER_QTY(38, "OrderQty"),
    ORD_STATUS(39, "OrdStatus"),
    ORD_TYPE(40, "OrdType"),
    ORIG_CL_ORD_ID(41, "OrigClOrdID"),
    POSS_DUP_FLAG(43, "PossDupFlag"),
    PRICE(44, "Price"),
    SENDER_COMP_ID(49, "SenderCompID"),
    SENDING_TIME(52, "SendingTime"),
    SIDE(54, "Side"),
    SYMBOL(55, "Symbol"),
    TARGET_COMP_ID(56, "TargetCompID"),
    TEXT(58, "Text"),
    TIME_IN_FORCE(59, "TimeInForce"),
    TRANSACT_TIME(60, "TransactTime"),
    SETTL_DATE(64, "SettlDate"),
    POSS_RESEND(97, "PossResend"),
    MIN_QTY(110, "MinQty"),
    EXEC_TYPE(150, "ExecType"),
    LEAVES_QTY(151, "LeavesQty"),
    MD_REQ_ID(262, "MDReqID"),
    SUBSCRIPTION_REQUEST_TYPE(263, "SubscriptionRequestType"),
    MARKET_DEPTH(264, "MarketDepth"),
    NO_MD_ENTRIES(268, "NoMDEntries"),
    MD_ENTRY_TYPE(269, "MDEntryType"),
    MD_ENTRY_PX(270, "MDEntryPx"),
    MD_ENTRY_SIZE(271, "MDEntrySize"),
    MD_ENTRIES(-1, "MDEntries");

    public static final EnumSet<FixField> HEADER_FIELDS =
        EnumSet.of(MSG_TYPE, MSG_SEQ_NUM, SENDER_COMP_ID, TARGET_COMP_ID,
                POSS_DUP_FLAG, POSS_RESEND, SENDING_TIME);

    public final int fixTag;
    public final String fixName;
    public final String loglinePrefix;

    FixField(int fixTag, String fixName) {
        this.fixTag = fixTag;
        this.fixName = fixName;
        if (fixTag >= 0) {
            this.loglinePrefix = fixName + " <" + fixTag + ">: ";
        } else {
            this.loglinePrefix = fixName + ": ";
        }
    }

    public static <U> BinaryOperator<U> mergeFunction() {
        return (l, r) -> {
            throw new IllegalArgumentException("Duplicate keys l " + l + " and r " + r);
        };
    }

    public static <K extends Enum<K>, V> EnumMap<K, V> buildEnumCache(
            K[] values, Function<K, V> mapper, Supplier<EnumMap<K, V>> supplier) {
        return Arrays.stream(values)
            .collect(Collectors.toMap(
                        Function.identity(),
                        mapper,
                        mergeFunction(),
                        supplier)
                    );
    }
}
