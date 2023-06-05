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

import com.abissell.fixbridge.OrderQty;
import com.abissell.fixbridge.Price;
import com.abissell.fixbridge.Side;

public /* value */ record SidePxQty(
        Side side,
        Price px,
        OrderQty qty) implements Comparable<SidePxQty> {

    @Override
    public int compareTo(SidePxQty o) {
        if (side != o.side) {
            throw new IllegalStateException("Cannot compare snapshots " + this + " and " + o);
        }

        if (side != Side.BUY && side != Side.SELL) {
            throw new IllegalArgumentException("Do not know how to compare " + this + " and " + o);
        }

        int pxCompare = switch (side) {
            case BUY -> -1 * Double.compare(px.px(), o.px().px());
            case SELL -> Double.compare(px.px(), o.px().px());
            default -> throw new IllegalArgumentException("Do not know how to compare " + this + " and " + o);
        };
        if (pxCompare != 0) {
            return pxCompare;
        }

        int qtyCompare = Double.compare(qty.qty(), o.qty().qty());
        return -1 * qtyCompare;
    }
}
