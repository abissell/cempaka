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
package com.abissell.cempaka.util;

import java.util.EnumMap;

import com.abissell.javautil.io.ThreadLocalFormat;
import com.abissell.logutil.Log;
import com.abissell.logutil.LogDstSet;
import com.abissell.logutil.OptBuf;
import com.abissell.fixbridge.AvgPx;
import com.abissell.cempaka.data.CcyPair;
import com.abissell.cempaka.orderid.ClOrdrID;
import com.abissell.fixbridge.CumQty;
import com.abissell.fixbridge.ExecID;
import com.abissell.fixbridge.ExecType;
import com.abissell.fixbridge.FixField;
import com.abissell.fixbridge.FixFieldVal;
import com.abissell.fixbridge.LastPx;
import com.abissell.fixbridge.LastShares;
import com.abissell.fixbridge.OrdStatus;
import com.abissell.fixbridge.OrderQty;
import com.abissell.fixbridge.OrigClOrdrID;
import com.abissell.fixbridge.Price;
import com.abissell.fixbridge.Side;
import com.abissell.fixbridge.TimeInForce;

public enum CempakaLog {
    ; // Enum singleton

    public void to(Log log, LogDstSet<?> dstSet, String prefix, EnumMap<FixField, FixFieldVal> fixFields) {
        if (log.isEnabled(dstSet)) {
            dstSet.set().forEach(dst ->
                fixFields.forEach((__, v) -> log.to(dst, prefix + fixLogline(v)))
            );
        }
    }

    public String fixLogline(FixFieldVal val) {
        int fixTag = val.fixTag();
        if (fixTag >= 0) {
            return val.fixName() + " <" + val.fixTag() + ">: " + val;
        } else {
            return val.fixName() + ": " + val;
        }
    }

    public static void logExecutionReport(EnumMap<FixField, FixFieldVal> execReport,
            OptBuf buf) {
        if (buf instanceof OptBuf.Noop) {
            return;
        }

        CcyPair ccyPair = (CcyPair) execReport.get(FixField.SYMBOL);
        buf.add("\n").add(ccyPair).add(" ");
        ExecType execType = (ExecType) execReport.get(FixField.EXEC_TYPE);
        buf.add(getType(execType)).add(" ");
        Side side = (Side) execReport.get(FixField.SIDE);
        buf.add(getAction(execType, side)).add(" ");
        buf.add(getQty(execType, execReport)).add(" @ ");
        buf.add(getPx(execType, execReport));
        OrdStatus status = (OrdStatus) execReport.get(FixField.ORD_STATUS);
        if (status != null) {
            buf.add(" | STATUS: ").add(status);
        }
        TimeInForce tif = (TimeInForce) execReport.get(FixField.TIME_IN_FORCE);
        if (tif != null) {
            buf.add(" | TIF: ").add(tif);
        }
        buf.add(" | TS: ").add(execReport.get(FixField.TRANSACT_TIME));
        buf.add(" | ORD_ID: ").add(((ClOrdrID) execReport.get(FixField.CL_ORD_ID)).fieldVal());
        CumQty cumQty = (CumQty) execReport.get(FixField.CUM_QTY);
        if (cumQty != null) {
            buf.add(" | CUM_QTY: ").add(cumQty.decimalString());
        }
        AvgPx avgPx = (AvgPx) execReport.get(FixField.AVG_PX);
        if (avgPx != null) {
            buf.add(" | AVG_PX: ").add(avgPx.decimalString());
        }
        OrigClOrdrID origID = (OrigClOrdrID) execReport.get(FixField.ORIG_CL_ORD_ID);
        if (origID != null) {
            buf.add(" | ORIG_ORD_ID: ").add(origID.fieldVal());
        }
        ExecID execID = (ExecID) execReport.get(FixField.EXEC_ID);
        if (execID != null) {
            buf.add(" | EXEC_ID: ").add(execID.fieldVal());
        }
    }

    private static String getType(ExecType execType) {
        return switch (execType) {
            case PARTIAL_FILL -> "FILL";
            case CANCELED -> "CXLD";
            case PENDING_CANCEL -> "PEND_CXL";
            case PENDING_NEW -> "PEND_NEW";
            default -> execType.toString();
        };
    }

    private static String getAction(ExecType type, Side side) {
        return switch (type) {
            case PENDING_NEW, NEW, PENDING_CANCEL, CANCELED, REJECTED -> {
                yield switch (side) {
                    case BUY -> "BID";
                    case SELL -> "ASK";
                    default -> throw new IllegalArgumentException("" + side);
                };
            }
            case PARTIAL_FILL, FILL -> {
                yield switch (side) {
                    case BUY -> "BOT";
                    case SELL -> "SLD";
                    default -> throw new IllegalArgumentException("" + side);
                };
            }
            default -> throw new IllegalArgumentException("" + type);
        };
    }

    private static String getQty(ExecType type, EnumMap<FixField, FixFieldVal> msg) {
        return switch (type) {
            case PARTIAL_FILL, FILL -> ((LastShares) msg.get(FixField.LAST_SHARES)).decimalString();
            case PENDING_CANCEL, CANCELED -> {
                CumQty cumQty = (CumQty) msg.get(FixField.CUM_QTY);
                double cumQtyVal;
                if (cumQty != null) {
                    cumQtyVal = cumQty.fieldVal();
                } else {
                    cumQtyVal = 0.0d;
                }
                OrderQty orderQty = (OrderQty) msg.get(FixField.ORDER_QTY);
                double cxldVal = orderQty.fieldVal() - cumQtyVal;
                yield ThreadLocalFormat.with8SigDigits().format(cxldVal);
            }
            default -> ((OrderQty) msg.get(FixField.ORDER_QTY)).decimalString();
        };
    }

    private static String getPx(ExecType type, EnumMap<FixField, FixFieldVal> msg) {
        return switch (type) {
            case PARTIAL_FILL, FILL -> ((LastPx) msg.get(FixField.LAST_PX)).decimalString();
            default -> ((Price) msg.get(FixField.PRICE)).decimalString();
        };
    }
}
