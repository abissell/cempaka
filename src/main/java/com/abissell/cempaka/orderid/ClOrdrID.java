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
package com.abissell.cempaka.orderid;

import com.abissell.fixbridge.ClOrdID;
import com.abissell.fixbridge.OrigClOrdrID;

import java.time.LocalDateTime;

public /* primitive */ record ClOrdrID(OrdrID id) implements ClOrdID {

    @Override
    public String fieldVal() {
        return id.asStr();
    }

    public static ClOrdrID from(String s) {
        return new ClOrdrID(OrdrID.from(s));
    }

    public static ClOrdrID from(LocalDateTime ldt) {
        return new ClOrdrID(OrdrID.from(ldt));
    }

    public static ClOrdrID from(LocalDateTime ldt, int plusNanos) {
        return new ClOrdrID(OrdrID.from(ldt, plusNanos));
    }

    public static ClOrdrID from(OrigClOrdrID o) {
        return new ClOrdrID(o.id());
    }
}
