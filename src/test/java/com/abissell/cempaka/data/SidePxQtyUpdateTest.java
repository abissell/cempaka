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
package com.abissell.cempaka.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.abissell.fixbridge.OrderQty;
import com.abissell.fixbridge.Price;
import com.abissell.fixbridge.Side;

public class SidePxQtyUpdateTest {
    @Test
    public void testSortsBidPxsCorrectly() {
        var time = LocalDateTime.now();
        var qty = new OrderQty(10.0d);
        var list = new ArrayList<SidePxQtyUpdate>();
        var update1 = new SidePxQtyUpdate(new SidePxQty(Side.BUY, new Price(1.00005d), qty), time, time);
        list.add(update1);
        var update2 = new SidePxQtyUpdate(new SidePxQty(Side.BUY, new Price(1.00003d), qty), time, time);
        list.add(update2);
        var update3 = new SidePxQtyUpdate(new SidePxQty(Side.BUY, new Price(1.00006d), qty), time, time);
        list.add(update3);

        Collections.sort(list);
        assertEquals(list, List.of(update3, update1, update2));
    }

    @Test
    public void testSortsAskPxsCorrectly() {
        var time = LocalDateTime.now();
        var qty = new OrderQty(10.0d);
        var list = new ArrayList<SidePxQtyUpdate>();
        var update1 = new SidePxQtyUpdate(new SidePxQty(Side.SELL, new Price(1.00005d), qty), time, time);
        list.add(update1);
        var update2 = new SidePxQtyUpdate(new SidePxQty(Side.SELL, new Price(1.00003d), qty), time, time);
        list.add(update2);
        var update3 = new SidePxQtyUpdate(new SidePxQty(Side.SELL, new Price(1.00006d), qty), time, time);
        list.add(update3);

        Collections.sort(list);
        assertEquals(list, List.of(update2, update1, update3));
    }

    @Test
    public void testSortsQtysCorrectly() {
        var time = LocalDateTime.now();
        var qty = new OrderQty(10.0d);
        var lowerQty = new OrderQty(9.0d);
        var list = new ArrayList<SidePxQtyUpdate>();
        var update1 = new SidePxQtyUpdate(new SidePxQty(Side.SELL, new Price(1.00005d), qty), time, time);
        list.add(update1);
        var update2 = new SidePxQtyUpdate(new SidePxQty(Side.SELL, new Price(1.00004d), qty), time, time);
        list.add(update2);
        var update3 = new SidePxQtyUpdate(new SidePxQty(Side.SELL, new Price(1.00004d), lowerQty), time, time);
        list.add(update3);

        Collections.sort(list);
        assertEquals(list, List.of(update2, update3, update1));
    }
}
