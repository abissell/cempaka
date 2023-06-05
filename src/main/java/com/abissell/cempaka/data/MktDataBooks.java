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

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;

import com.abissell.fixbridge.ParsedFixMsg;
import com.abissell.fixbridge.Tradeable;

public final class MktDataBooks<T extends Tradeable> {
    private final Map<T, MktDataBook> books;
    private final Function<ParsedFixMsg, T> tradeableExtractor;

    public MktDataBooks(
            Map<T, MktDataBook> booksMap,
            Function<ParsedFixMsg, T> tradeableExtractor,
            Collection<T> tradeables) {
        this.books = booksMap;
        this.tradeableExtractor = tradeableExtractor;
        tradeables.forEach(t -> booksMap.put(t, new MktDataBook(t)));
    }

    public MktDataBook get(T tradeable) {
        return books.get(tradeable);
    }

    public MktDataBook updateBook(ParsedFixMsg parsedMsg) {
        var tradeable = tradeableExtractor.apply(parsedMsg);
        var book = books.get(tradeable);
        book.updateBook(parsedMsg);
        return book;
    }
}
