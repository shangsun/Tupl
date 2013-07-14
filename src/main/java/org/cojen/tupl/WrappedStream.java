/*
 *  Copyright 2013 Brian S O'Neill
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

import java.io.IOException;

/**
 * 
 *
 * @author Brian S O'Neill
 */
abstract class WrappedStream extends Stream {
    final Stream mSource;

    WrappedStream(Stream source) {
        mSource = source;
    }

    @Override
    public long length() throws IOException {
        return mSource.length();
    }

    @Override
    public void setLength(long length) throws IOException {
        mSource.setLength(length);
    }

    @Override
    int doRead(long pos, byte[] buf, int off, int len) throws IOException {
        return mSource.doRead(pos, buf, off, len);
    }

    @Override
    void doWrite(long pos, byte[] buf, int off, int len) throws IOException {
        mSource.doWrite(pos, buf, off, len);
    }

    @Override
    int pageSize() {
        return mSource.pageSize();
    }

    @Override
    void checkOpen() {
        mSource.checkOpen();
    }

    @Override
    void doClose() {
        mSource.close();
    }
}
