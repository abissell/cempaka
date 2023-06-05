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
package com.abissell.fixbridge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;

import com.abissell.cempaka.data.SenderID;
import org.junit.jupiter.api.Test;

import com.abissell.javautil.rusty.Opt;

public class SenderIDTest {
    @Test
    public void testSenderIDLookup() {
        Arrays.stream(SenderID.values()).forEach(senderID ->
                assertEquals(SenderID.from(senderID.fixStr), Opt.of(senderID)));
    }
}
