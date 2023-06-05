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
import com.abissell.fixbridge.OrdStatus;
import com.abissell.fixbridge.Order;

public /* value */ record OrdWithStatus(
        Order order,
        OrdStatus status,
        Opt<Fill> lastFill) {
    OrdWithStatus(Order order, OrdStatus status) {
        this(order, status, Opt.none());
    }

    OrdWithStatus(Order order, OrdStatus status, Fill fill) {
        this(order, status, Opt.of(fill));
    }

    public OrdWithStatus withNewStatus(OrdStatus newStatus) {
        return new OrdWithStatus(order, newStatus, lastFill);
    }

    public OrdWithStatus withNewStatusAndFill(OrdStatus newStatus, Fill fill) {
        return new OrdWithStatus(order, newStatus, fill);
    }

    public static OrdWithStatus forPendingNewOrder(Order order) {
        return new OrdWithStatus(order, OrdStatus.PENDING_NEW);
    }
}
