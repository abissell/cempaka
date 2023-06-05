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

import java.util.EnumSet;

import com.abissell.logutil.LogDstSet;

public enum DstSet implements LogDstSet<Dst> {
    APP(Dst.APP),
    STD_OUT(Dst.STD_OUT),
    APP_STD_OUT(Dst.APP, Dst.STD_OUT),
    MKT_DATA(Dst.MKT_DATA),
    EXEC(Dst.EXEC),
    APP_STD_OUT_EXEC(Dst.APP, Dst.STD_OUT, Dst.EXEC);

    private final EnumSet<Dst> set;
    DstSet(Dst... dsts) {
        this.set = EnumSet.of(dsts[0], dsts);
    }

    public EnumSet<Dst> set() {
        return set;
    }
}
