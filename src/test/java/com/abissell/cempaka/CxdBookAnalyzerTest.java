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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.Arrays;

import com.abissell.cempaka.data.*;
import com.abissell.fixbridge.*;
import org.junit.jupiter.api.Test;

public class CxdBookAnalyzerTest {
    @Test
    public void findsNoCrossInUncrossedBooks() {
        var book = new MktDataBook(CcyPair.NUMER_2_DENOM_2);
        var now = LocalDateTime.now();
        var bidUpdate = new SidePxQtyUpdate(
                new SidePxQty(Side.BUY,
                    new Price(1700.0d),
                    new OrderQty(0.1d)
                ), now, now);
        var askUpdate = new SidePxQtyUpdate(
                new SidePxQty(Side.SELL,
                    new Price(1700.1d),
                    new OrderQty(0.1d)
                ), now, now);
        book.updateBook(Arrays.asList(bidUpdate), Arrays.asList(askUpdate));
        var analyzer = new CxdBookAnalyzer(Fees.ZERO);
        var analysis = analyzer.analyze(book, Constraints.on(Ccy.NUMER_2).minSigQty());
        assertTrue(analysis == CxdBookAnalysis.NOT_CXD);
    }

    @Test
    public void findsCrossInCrossedBooks() {
        var book = new MktDataBook(CcyPair.NUMER_2_DENOM_2);
        var now = LocalDateTime.now();
        var bidUpdate = new SidePxQtyUpdate(
                new SidePxQty(Side.BUY,
                    new Price(1700.0d),
                    new OrderQty(0.1d)
                ), now, now);
        var askUpdate = new SidePxQtyUpdate(
                new SidePxQty(Side.SELL,
                    new Price(1699.9d),
                    new OrderQty(0.05d)
                ), now, now);
        book.updateBook(Arrays.asList(bidUpdate), Arrays.asList(askUpdate));
        var analyzer = new CxdBookAnalyzer(Fees.ZERO);
        var analysis = analyzer.analyze(book, Constraints.on(Ccy.NUMER_2).minSigQty());
        assertEquals(1, analysis.cxdBidLvls().size());
        assertEquals(1, analysis.cxdAskLvls().size());
        assertEquals(bidUpdate, analysis.cxdBidLvls().get(0).update());
        assertEquals(new OrderQty(0.05d), analysis.cxdBidLvls().get(0).qty());
        assertEquals(askUpdate, analysis.cxdAskLvls().get(0).update());
        assertEquals(new OrderQty(0.05d), analysis.cxdAskLvls().get(0).qty());
    }

    @Test
    public void findsAllCrossesInComplexCrossedBooks() {
        var book = new MktDataBook(CcyPair.NUMER_2_DENOM_2);
        var now = LocalDateTime.now();
        var bidUpdate0 = new SidePxQtyUpdate(
                new SidePxQty(Side.BUY,
                    new Price(1700.0d),
                    new OrderQty(9.0d)
                ), now, now);
        var bidUpdate1 = new SidePxQtyUpdate(
                new SidePxQty(Side.BUY,
                    new Price(1699.0d),
                    new OrderQty(2.0d)
                ), now, now);
        var askUpdate4 = new SidePxQtyUpdate(
                new SidePxQty(Side.SELL,
                    new Price(1701.0d),
                    new OrderQty(100.0d)
                ), now, now);
        var askUpdate3 = new SidePxQtyUpdate(
                new SidePxQty(Side.SELL,
                    new Price(1699.9d),
                    new OrderQty(6.0d)
                ), now, now);
        var askUpdate2 = new SidePxQtyUpdate(
                new SidePxQty(Side.SELL,
                    new Price(1699.8d),
                    new OrderQty(2.0d)
                ), now, now);
        var askUpdate1 = new SidePxQtyUpdate(
                new SidePxQty(Side.SELL,
                    new Price(1698.9d),
                    new OrderQty(1.0d)
                ), now, now);
        var askUpdate0 = new SidePxQtyUpdate(
                new SidePxQty(Side.SELL,
                    new Price(1698.8d),
                    new OrderQty(3.0d)
                ), now, now);
        book.updateBook(
                Arrays.asList(bidUpdate0, bidUpdate1),
                Arrays.asList(askUpdate0, askUpdate1, askUpdate2, askUpdate3, askUpdate4));
        var analyzer = new CxdBookAnalyzer(Fees.ZERO);
        var analysis = analyzer.analyze(book, Constraints.on(Ccy.NUMER_2).minSigQty());
        assertEquals(3.599999999998545, analysis.theoValUsd());
        assertEquals(bidUpdate1, analysis.cxdBidLvls().get(4).update());
        assertEquals(new OrderQty(2.0d), analysis.cxdBidLvls().get(4).qty());
        assertEquals(bidUpdate0, analysis.cxdBidLvls().get(3).update());
        assertEquals(new OrderQty(1.0d), analysis.cxdBidLvls().get(3).qty());
        assertEquals(bidUpdate0, analysis.cxdBidLvls().get(2).update());
        assertEquals(new OrderQty(1.0d), analysis.cxdBidLvls().get(2).qty());
        assertEquals(bidUpdate0, analysis.cxdBidLvls().get(1).update());
        assertEquals(new OrderQty(2.0d), analysis.cxdBidLvls().get(1).qty());
        assertEquals(bidUpdate0, analysis.cxdBidLvls().get(0).update());
        assertEquals(new OrderQty(5.0d), analysis.cxdBidLvls().get(0).qty());
    }

    @Test
    public void findsAllCrossesInComplexCrossedBooksWithHiddenRoadFees() {
        var book = new MktDataBook(CcyPair.NUMER_2_DENOM_2);
        var now = LocalDateTime.now();
        var bidUpdate0 = new SidePxQtyUpdate(
                new SidePxQty(Side.BUY,
                    new Price(1700.0d), // 1699.915 net of fees
                    new OrderQty(9.0d)
                ), now, now);
        var bidUpdate1 = new SidePxQtyUpdate(
                new SidePxQty(Side.BUY,
                    new Price(1699.0d), // 1698.91505 net of fees
                    new OrderQty(2.0d)
                ), now, now);
        var askUpdate4 = new SidePxQtyUpdate(
                new SidePxQty(Side.SELL,
                    new Price(1701.0d),
                    new OrderQty(100.0d)
                ), now, now);
        var askUpdate3 = new SidePxQtyUpdate(
                new SidePxQty(Side.SELL,
                    new Price(1699.9d), // 1699.984995 net of fees
                    new OrderQty(6.0d)
                ), now, now);
        var askUpdate2 = new SidePxQtyUpdate(
                new SidePxQty(Side.SELL,
                    new Price(1699.8d), // 1699.88499 net of fees
                    new OrderQty(2.0d)
                ), now, now);
        var askUpdate1 = new SidePxQtyUpdate(
                new SidePxQty(Side.SELL,
                    new Price(1698.9d), // 1698.984945 net of fees
                    new OrderQty(1.0d)
                ), now, now);
        var askUpdate0 = new SidePxQtyUpdate(
                new SidePxQty(Side.SELL,
                    new Price(1698.8d), // 1698.88494 net of fees
                    new OrderQty(3.0d)
                ), now, now);
        book.updateBook(
                Arrays.asList(bidUpdate0, bidUpdate1),
                Arrays.asList(askUpdate0, askUpdate1, askUpdate2, askUpdate3, askUpdate4));
        var analyzer = new CxdBookAnalyzer(Fees.PCT);
        var analysis = analyzer.analyze(book, Constraints.on(Ccy.NUMER_2).minSigQty());
        assertEquals(2.080354999998235, analysis.theoValUsd());
        assertEquals(bidUpdate1, analysis.cxdBidLvls().get(3).update());
        assertEquals(new OrderQty(2.0d), analysis.cxdBidLvls().get(3).qty());
        assertEquals(bidUpdate0, analysis.cxdBidLvls().get(2).update());
        assertEquals(new OrderQty(1.0d), analysis.cxdBidLvls().get(2).qty());
        assertEquals(bidUpdate0, analysis.cxdBidLvls().get(1).update());
        assertEquals(new OrderQty(1.0d), analysis.cxdBidLvls().get(1).qty());
        assertEquals(bidUpdate0, analysis.cxdBidLvls().get(0).update());
        assertEquals(new OrderQty(2.0d), analysis.cxdBidLvls().get(0).qty());
    }
}
