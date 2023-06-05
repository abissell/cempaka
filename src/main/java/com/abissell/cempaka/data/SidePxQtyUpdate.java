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

import java.time.LocalDateTime;

import com.abissell.fixbridge.Price;

public /* value */ record SidePxQtyUpdate(
        SidePxQty level,
        LocalDateTime sendingTime,
        LocalDateTime recvdTime) implements Comparable<SidePxQtyUpdate> {

    public Price px() {
        return level.px();
    }

    @Override
    public int compareTo(SidePxQtyUpdate o) {
        int levelCompare = level.compareTo(o.level);
        if (levelCompare != 0) {
            return levelCompare;
        }

        // favor more recent updates over older ones
        int sendingTimeCompare = -1 * sendingTime.compareTo(o.sendingTime());
        if (sendingTimeCompare != 0) {
            return sendingTimeCompare;
        }

        return -1 * recvdTime.compareTo(o.recvdTime());
    }
}
