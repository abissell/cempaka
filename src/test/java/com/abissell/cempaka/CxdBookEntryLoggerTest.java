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

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.abissell.logutil.OptBuf;
import com.abissell.cempaka.data.Constraints;
import com.abissell.cempaka.data.CxOrders;
import com.abissell.cempaka.data.Fees;
import com.abissell.cempaka.data.MktDataBook;
import com.abissell.cempaka.data.SidePxQty;
import com.abissell.cempaka.data.SidePxQtyUpdate;
import com.abissell.cempaka.data.TradingMode;
import com.abissell.cempaka.data.Ccy;
import com.abissell.cempaka.data.CcyPair;
import com.abissell.fixbridge.Order;
import com.abissell.fixbridge.OrderQty;
import com.abissell.fixbridge.Price;
import com.abissell.fixbridge.Side;
import com.abissell.fixbridge.StrClOrdID;
import com.abissell.fixbridge.TimeInForce;
import com.abissell.fixbridge.TransactTime;

public class CxdBookEntryLoggerTest {
    @Test
    public void testLogEntry() {
        // Construct a MktDataBook with two bids crossed with one ask
        var book = new MktDataBook(CcyPair.NUMER_2_DENOM_2);
        var now = LocalDateTime.now();
        var bidUpdate0 = new SidePxQtyUpdate(
                new SidePxQty(Side.BUY,
                    new Price(1701.0d),
                    new OrderQty(0.05d)
                ), now, now);
        var bidUpdate1 = new SidePxQtyUpdate(
                new SidePxQty(Side.BUY,
                    new Price(1700.0d),
                    new OrderQty(0.1d)
                ), now, now);
        var askUpdate = new SidePxQtyUpdate(
                new SidePxQty(Side.SELL,
                    new Price(1699.0d),
                    new OrderQty(0.125d)
                ), now, now);
        book.updateBook(Arrays.asList(bidUpdate0, bidUpdate1), Arrays.asList(askUpdate));
        var analyzer = new CxdBookAnalyzer(Fees.ZERO);
        var cxdBook = analyzer.analyze(book, Constraints.on(Ccy.NUMER_2).minSigQty());
        var buyOrder = new Order(
            new StrClOrdID("buy_id"),
            CcyPair.NUMER_2_DENOM_2,
            Ccy.NUMER_2,
            Side.BUY,
            new OrderQty(0.125d),
            new Price(1699.49d),
            TimeInForce.IMMEDIATE_OR_CANCEL,
            new TransactTime(LocalDateTime.now(ZoneId.of("UTC")))
        );
        var sellOrder = new Order(
            new StrClOrdID("sell_id"),
            CcyPair.NUMER_2_DENOM_2,
            Ccy.NUMER_2,
            Side.SELL,
            new OrderQty(0.125d),
            new Price(1699.51d),
            TimeInForce.IMMEDIATE_OR_CANCEL,
            new TransactTime(LocalDateTime.now(ZoneId.of("UTC")))
        );
        var cxOrders = new CxOrders(buyOrder, sellOrder);
        var logger = new CxdBookEntryLogger();
        var buf = new OptBuf.Buf(new StringBuilder());
        logger.logEntryOrder(buyOrder, TradingMode.DRY_RUN, buf);
        logger.logEntryOrder(sellOrder, TradingMode.DRY_RUN, buf);
        logger.logEntry(cxOrders, cxdBook, book, Fees.PCT, 0.000001d, buf);
    }
}
