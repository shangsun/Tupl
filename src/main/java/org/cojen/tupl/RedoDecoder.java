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

import java.io.IOException;

import static org.cojen.tupl.RedoOps.*;

/**
 * 
 *
 * @author Brian S O'Neill
 * @see RedoWriter
 */
class RedoDecoder {
    private final DataIn mIn;
    private final boolean mLenient;

    private long mTxnId;

    RedoDecoder(DataIn in, boolean lenient, long initialTxnId) {
        mIn = in;
        mLenient = lenient;
        mTxnId = initialTxnId;
    }

    /**
     * Reads from the stream, passing operations to the visitor, until the end
     * of stream is reached or visitor returns false.
     *
     * @return true if end of stream reached; false if visitor returned false
     */
    boolean run(RedoVisitor visitor) throws IOException {
        DataIn in = mIn;
        int op;
        while ((op = in.read()) >= 0) {
            switch (op &= 0xff) {
            case 0:
                if (mLenient) {
                    // Assume redo log did not flush completely.
                    return true;
                }
                // fallthrough to next case...

            default:
                throw new DatabaseException("Unknown redo log operation: " + op);

            case OP_RESET:
                if (!verifyTerminator(in)) {
                    return false;
                }
                mTxnId = 0;
                if (!visitor.reset()) {
                    return false;
                }
                break;

            case OP_TIMESTAMP:
                long ts = in.readLongLE();
                if (!verifyTerminator(in) || !visitor.timestamp(ts)) {
                    return false;
                }
                break;

            case OP_SHUTDOWN:
                ts = in.readLongLE();
                if (!verifyTerminator(in) || !visitor.shutdown(ts)) {
                    return false;
                }
                break;

            case OP_CLOSE:
                ts = in.readLongLE();
                if (!verifyTerminator(in) || !visitor.close(ts)) {
                    return false;
                }
                break;

            case OP_END_FILE:
                ts = in.readLongLE();
                if (!verifyTerminator(in) || !visitor.endFile(ts)) {
                    return false;
                }
                break;

            case OP_TXN_ENTER:
                long txnId = readTxnId(in);
                if (!verifyTerminator(in) || !visitor.txnEnter(txnId)) {
                    return false;
                }
                break;

            case OP_TXN_ROLLBACK:
                txnId = readTxnId(in);
                if (!verifyTerminator(in) || !visitor.txnRollback(txnId)) {
                    return false;
                }
                break;

            case OP_TXN_ROLLBACK_FINAL:
                txnId = readTxnId(in);
                if (!verifyTerminator(in) || !visitor.txnRollbackFinal(txnId)) {
                    return false;
                }
                break;

            case OP_TXN_COMMIT:
                txnId = readTxnId(in);
                if (!verifyTerminator(in) || !visitor.txnCommit(txnId)) {
                    return false;
                }
                break;

            case OP_TXN_COMMIT_FINAL:
                txnId = readTxnId(in);
                if (!verifyTerminator(in) || !visitor.txnCommitFinal(txnId)) {
                    return false;
                }
                break;

            case OP_STORE:
                long indexId = in.readLongLE();
                byte[] key = in.readBytes();
                byte[] value = in.readBytes();
                if (!verifyTerminator(in) || !visitor.store(indexId, key, value)) {
                    return false;
                }
                break;

            case OP_STORE_NO_LOCK:
                indexId = in.readLongLE();
                key = in.readBytes();
                value = in.readBytes();
                if (!verifyTerminator(in) || !visitor.storeNoLock(indexId, key, value)) {
                    return false;
                }
                break;

            case OP_DELETE:
                indexId = in.readLongLE();
                key = in.readBytes();
                if (!verifyTerminator(in) || !visitor.store(indexId, key, null)) {
                    return false;
                }
                break;

            case OP_DELETE_NO_LOCK:
                indexId = in.readLongLE();
                key = in.readBytes();
                if (!verifyTerminator(in) || !visitor.storeNoLock(indexId, key, null)) {
                    return false;
                }
                break;

            case OP_TXN_ENTER_STORE:
                txnId = readTxnId(in);
                indexId = in.readLongLE();
                key = in.readBytes();
                value = in.readBytes();
                if (!verifyTerminator(in)
                    || !visitor.txnEnter(txnId)
                    || !visitor.txnStore(txnId, indexId, key, value))
                {
                    return false;
                }
                break;

            case OP_TXN_STORE:
                txnId = readTxnId(in);
                indexId = in.readLongLE();
                key = in.readBytes();
                value = in.readBytes();
                if (!verifyTerminator(in) || !visitor.txnStore(txnId, indexId, key, value)) {
                    return false;
                }
                break;

            case OP_TXN_STORE_COMMIT:
                txnId = readTxnId(in);
                indexId = in.readLongLE();
                key = in.readBytes();
                value = in.readBytes();
                if (!verifyTerminator(in)
                    || !visitor.txnStore(txnId, indexId, key, value)
                    || !visitor.txnCommit(txnId))
                {
                    return false;
                }
                break;

            case OP_TXN_STORE_COMMIT_FINAL:
                txnId = readTxnId(in);
                indexId = in.readLongLE();
                key = in.readBytes();
                value = in.readBytes();
                if (!verifyTerminator(in)
                    || !visitor.txnStoreCommitFinal(txnId, indexId, key, value))
                {
                    return false;
                }
                break;

            case OP_TXN_ENTER_DELETE:
                txnId = readTxnId(in);
                indexId = in.readLongLE();
                key = in.readBytes();
                if (!verifyTerminator(in)
                    || !visitor.txnEnter(txnId)
                    || !visitor.txnStore(txnId, indexId, key, null))
                {
                    return false;
                }
                break;

            case OP_TXN_DELETE:
                txnId = readTxnId(in);
                indexId = in.readLongLE();
                key = in.readBytes();
                if (!verifyTerminator(in) || !visitor.txnStore(txnId, indexId, key, null)) {
                    return false;
                }
                break;

            case OP_TXN_DELETE_COMMIT:
                txnId = readTxnId(in);
                indexId = in.readLongLE();
                key = in.readBytes();
                if (!verifyTerminator(in)
                    || !visitor.txnStore(txnId, indexId, key, null)
                    || !visitor.txnCommitFinal(txnId))
                {
                    return false;
                }
                break;

            case OP_TXN_DELETE_COMMIT_FINAL:
                txnId = readTxnId(in);
                indexId = in.readLongLE();
                key = in.readBytes();
                if (!verifyTerminator(in)
                    || !visitor.txnStoreCommitFinal(txnId, indexId, key, null))
                {
                    return false;
                }
                break;
            }
        }

        return true;
    }

    private long readTxnId(DataIn in) throws IOException {
        return mTxnId += in.readSignedVarLong();
    }

    /**
     * If false is returned, assume rest of redo data is corrupt.
     * Implementation can return true if no redo terminators were written.
     */
    boolean verifyTerminator(DataIn in) throws IOException {
        return true;
    }
}