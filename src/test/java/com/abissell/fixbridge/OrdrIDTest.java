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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.LocalDateTime;

import com.abissell.cempaka.orderid.OrdrID;
import org.junit.jupiter.api.Test;

public class OrdrIDTest {
    @Test
    public void testValidValues() {
        var idStr = "abcABC12";
        var id = OrdrID.from("abcABC12");
        assertEquals(id.asStr(), idStr);
    }

    @Test
    public void testFromLocalDateTimesAndStrings() {
        var dateTime = LocalDateTime.now();
        for (int i = 0; i < 10_000_000; i++) {
            dateTime = dateTime.plusNanos(1L);
            var id = OrdrID.from(dateTime);
            assertNotNull(id);
            assertEquals(id, OrdrID.from(id.asStr()));
        }

        for (int i = 0; i < 24; i++) {
            dateTime = dateTime.withHour(i);
            var id = OrdrID.from(dateTime);
            assertNotNull(id);
            assertEquals(id, OrdrID.from(id.asStr()));
        }

        for (int i = 0; i < 60; i++) {
            dateTime = dateTime.withMinute(i).withSecond(i);
            var id = OrdrID.from(dateTime);
            assertNotNull(id);
            assertEquals(id, OrdrID.from(id.asStr()));
        }
    }

    @Test
    public void testIncrementedOrderIds() {
        var dateTime = LocalDateTime.now();
        for (int i = 0; i < 10_000_000; i++) {
            dateTime = dateTime.plusNanos(1L);
            var id = OrdrID.from(dateTime);
            var secondId = OrdrID.from(dateTime, 1);
            assertNotEquals(id, secondId);
        }
    }
}
