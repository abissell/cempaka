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

import com.abissell.javautil.rusty.Err;
import com.abissell.javautil.rusty.ErrType;

public enum FixErr implements ErrType<FixErr> {
    CACHED_FIELD_NOT_FOUND,
    SESSION_NOT_FOUND,
    CONFIG_ERROR,
    CRITICAL_ERROR;

    public final Err<?, FixErr> err;

    FixErr() {
        this.err = new Err<>(this);
    }

    @SuppressWarnings("unchecked")
    public <T> Err<T, FixErr> err() {
        return (Err<T, FixErr>) err;
    }
}
