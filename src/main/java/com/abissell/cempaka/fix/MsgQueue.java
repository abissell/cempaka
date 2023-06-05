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
package com.abissell.cempaka.fix;

import java.util.Collection;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import com.abissell.logutil.Log;
import com.abissell.cempaka.util.DstSet;

public final class MsgQueue<T> {
    private final BlockingQueue<T> queue;
    public volatile boolean healthy = true;

    public MsgQueue(int capacity) {
        this.queue = new ArrayBlockingQueue<>(capacity);
    }

    public boolean offer(T item) {
        var succeeded = queue.offer(item);
        if (!succeeded) {
            healthy = false;
            Log.ERROR.to(DstSet.APP_STD_OUT, "queue is unhealthy!");
            Log.ERROR.to(DstSet.APP_STD_OUT, "queue size=" + queue.size() + ", remainingCapacity=" + queue.remainingCapacity());
            Log.ERROR.to(DstSet.APP_STD_OUT, "attempting to add item: " + item);
        }
        return succeeded;
    }

    public int drainTo(Collection<? super T> c) {
        return queue.drainTo(c);
    }

    public int size() {
        return queue.size();
    }

    public int remainingCapacity() {
        return queue.remainingCapacity();
    }

    public void setHealthy() {
        healthy = true;
    }
}
