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

import com.abissell.javautil.rusty.Opt;
import com.abissell.fixbridge.FixStrMappedEnum;
import com.abissell.fixbridge.TargetCompID;

public enum TargetID implements FixStrMappedEnum<TargetID>, TargetCompID {
    SENDER_RATE_1("SENDER-RATE-1"),
    SENDER_DEAL_1("SENDER-DEAL-1"),
    COUNTERPARTY("COUNTERPARTY");

    public final String fixStr;

    TargetID(String fixStr) {
        this.fixStr = fixStr;
    }

    @Override
    public String fieldVal() {
        return fixStr;
    }

    // TODO: Make this correct
    public static Opt<TargetID> from(String s) {
        return Opt.of(SENDER_RATE_1);
    }
}
