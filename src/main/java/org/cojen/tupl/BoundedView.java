/*
 *  Copyright 2012-2013 Brian S O'Neill
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.cojen.tupl;

import static org.cojen.tupl.Utils.*;

/**
 * 
 *
 * @author Brian S O'Neill
 */
final class BoundedView extends SubView {
    static final int START_EXCLUSIVE = 0xfffffffe, END_EXCLUSIVE = 1;

    final byte[] mStart;
    final byte[] mEnd;
    final int mMode;

    BoundedView(View source, byte[] start, byte[] end, int mode) {
        super(source);
        mStart = start;
        mEnd = end;
        mMode = mode;
    }

    @Override
    public Cursor newCursor(Transaction txn) {
        return new BoundedCursor(this, mSource.newCursor(txn));
    }

    @Override
    public View viewGe(byte[] key) {
        if (key == null) {
            throw new NullPointerException("Key is null");
        }
        if (startRangeCompare(key) <= 0) {
            return this;
        }
        return new BoundedView(mSource, key, mEnd, mMode & ~START_EXCLUSIVE);
    }

    @Override
    public View viewGt(byte[] key) {
        if (key == null) {
            throw new NullPointerException("Key is null");
        }
        if (startRangeCompare(key) < 0) {
            return this;
        }
        return new BoundedView(mSource, key, mEnd, mMode | START_EXCLUSIVE);
    }

    @Override
    public View viewLe(byte[] key) {
        if (key == null) {
            throw new NullPointerException("Key is null");
        }
        if (endRangeCompare(key) >= 0) {
            return this;
        }
        return new BoundedView(mSource, mStart, key, mMode & ~END_EXCLUSIVE);
    }

    @Override
    public View viewLt(byte[] key) {
        if (key == null) {
            throw new NullPointerException("Key is null");
        }
        if (endRangeCompare(key) > 0) {
            return this;
        }
        return new BoundedView(mSource, mStart, key, mMode | END_EXCLUSIVE);
    }

    @Override
    boolean inRange(byte[] key) {
        return startRangeCompare(key) >= 0 && endRangeCompare(key) <= 0;
    }

    /**
     * @return <0 if less than start, 0 if equal (in range), >0 if higher (in range)
     */
    int startRangeCompare(byte[] key) {
        byte[] start = mStart;
        if (start == null) {
            return 1;
        }
        int result = compareKeys(key, 0, key.length, start, 0, start.length);
        return result != 0 ? result : (mMode & START_EXCLUSIVE);
    }

    /**
     * @param start must not be null
     * @param key must not be null
     * @return <0 if less than start, 0 if equal (in range), >0 if higher (in range)
     */
    int startRangeCompare(byte[] start, byte[] key) {
        int result = compareKeys(key, 0, key.length, start, 0, start.length);
        return result != 0 ? result : (mMode & START_EXCLUSIVE);
    }

    /**
     * @return <0 if less than end (in range), 0 if equal (in range), >0 if higher
     */
    int endRangeCompare(byte[] key) {
        byte[] end = mEnd;
        if (end == null) {
            return -1;
        }
        int result = compareKeys(key, 0, key.length, end, 0, end.length);
        return result != 0 ? result : (mMode & END_EXCLUSIVE);
    }

    /**
     * @param end must not be null
     * @param key must not be null
     * @return <0 if less than end (in range), 0 if equal (in range), >0 if higher
     */
    int endRangeCompare(byte[] end, byte[] key) {
        int result = compareKeys(key, 0, key.length, end, 0, end.length);
        return result != 0 ? result : (mMode & END_EXCLUSIVE);
    }
}
