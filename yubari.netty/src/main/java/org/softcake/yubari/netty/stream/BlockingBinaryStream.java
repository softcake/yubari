/*
 * Copyright 2018 softcake.org.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.softcake.yubari.netty.stream;

import com.dukascopy.dds4.transport.msg.system.BinaryPartMessage;
import com.dukascopy.dds4.transport.msg.system.StreamingStatus;
import com.dukascopy.dds4.transport.msg.types.StreamState;
import io.netty.channel.ChannelHandlerContext;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author The softcake Authors.
 */
public class BlockingBinaryStream extends InputStream {

    private static final long READ_TIMEOUT = 50L;
    private static final long AWAIT_SPACE_TIMEOUT = 1000L;
    private final int ioTimeout;
    private byte[] buffer;
    private int in = -1;
    private int out;
    private boolean closedByWriter;
    private volatile boolean closedByReader;
    private final ChannelHandlerContext channelHandlerContext;
    private String streamId;
    private boolean ackDelayed;
    private boolean streamHandled;
    private boolean ioTerminate;
    private String errorMessage;

    /**
     * @param streamId
     * @param channelHandlerContext
     * @param bufferSize
     * @param ioTimeout
     */
    public BlockingBinaryStream(final String streamId,
                                final ChannelHandlerContext channelHandlerContext,
                                final int bufferSize,
                                final int ioTimeout) {

        this.streamId = streamId;
        this.channelHandlerContext = channelHandlerContext;
        this.ioTimeout = ioTimeout;
        this.initBuffer(bufferSize);
    }

    private void initBuffer(final int bufferSize) {

        if (bufferSize <= 0) {
            throw new IllegalArgumentException("Buffer Size <= 0");
        } else {
            this.buffer = new byte[bufferSize];
        }
    }

    /**
     * @return
     */
    public int getIOTimeout() {

        return this.ioTimeout;
    }

    /**
     * @return
     */
    public synchronized boolean isClosed() {

        return this.closedByWriter || this.closedByReader;
    }

    /**
     * @param message
     */
    public synchronized void ioTerminate(final String message) {

        this.ioTerminate = true;
        this.errorMessage = message;
        this.notifyAll();
    }

    /**
     * @param msg
     * @throws IOException
     */
    public synchronized void binaryPartReceived(final BinaryPartMessage msg) throws IOException {

        final byte[] received = msg.getData();

        if (received.length > 0) {
            this.receive(received, 0, received.length);
        }

        if (msg.isEof()) {
            this.receivedLast();
        }

        if (this.buffer.length - this.available() > this.buffer.length - received.length * 2) {
            this.ackDelayed = false;

            final StreamingStatus ss = new StreamingStatus();
            ss.setStreamId(msg.getStreamId());
            ss.setState(StreamState.STATE_OK);

            this.channelHandlerContext.writeAndFlush(ss);

        } else {
            this.ackDelayed = true;
        }

    }

    private synchronized void receive(final byte[] b, int off, final int len) throws IOException {

        this.checkState();
        int bytesToTransfer = len;

        while (bytesToTransfer > 0) {

            if (this.in == this.out) {
                this.awaitSpace();
            }

            int nextTransferAmount = 0;
            if (this.out < this.in) {

                nextTransferAmount = this.buffer.length - this.in;

            } else if (this.in < this.out) {

                if (this.in == -1) {
                    this.in = this.out = 0;
                    nextTransferAmount = this.buffer.length - this.in;

                } else {

                    nextTransferAmount = this.out - this.in;
                }
            }

            if (nextTransferAmount > bytesToTransfer) {
                nextTransferAmount = bytesToTransfer;
            }

            assert nextTransferAmount > 0;

            System.arraycopy(b, off, this.buffer, this.in, nextTransferAmount);
            bytesToTransfer -= nextTransferAmount;

            off += nextTransferAmount;
            this.in += nextTransferAmount;

            if (this.in >= this.buffer.length) {
                this.in = 0;
            }
        }

    }

    private void checkState() throws IOException {

        if (this.closedByWriter || this.closedByReader) {
            throw new IOException("Stream closed");
        }
    }

    @SuppressWarnings("squid:S2273")
    private void awaitSpace() throws IOException {

        while (this.in == this.out) {
            this.checkState();
            this.notifyAll();

            try {
                this.wait(AWAIT_SPACE_TIMEOUT);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

    }

    private synchronized void receivedLast() {

        this.closedByWriter = true;
        this.notifyAll();
    }

    /**
     * @return
     */
    public synchronized boolean isEOF() {

        return this.in < 0 && this.closedByWriter;
    }

    /**
     * Reads the next byte of data from the input stream. The value byte is
     * returned as an <code>int</code> in the range <code>0</code> to
     * <code>255</code>. If no byte is available because the end of the stream
     * has been reached, the value <code>-1</code> is returned. This method
     * blocks until input data is available, the end of the stream is detected,
     * or an exception is thrown.
     * <p>
     * <p> A subclass must provide an implementation of this method.
     *
     * @return the next byte of data, or <code>-1</code> if the end of the
     * stream is reached.
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public synchronized int read() throws IOException {

        this.checkStartTransfer();
        this.checkDelayedAck();

        if (this.closedByReader) {
            throw new IOException("Stream closed");
        }

        if (this.ioTerminate) {
            throw new IOException(this.errorMessage);
        }
        int timeoutThisRead = this.ioTimeout;

        while (this.in < 0) {

            if (this.closedByWriter) {
                this.checkDelayedAck();
                return -1;
            }

            timeoutThisRead -= READ_TIMEOUT;
            if (timeoutThisRead < 0) {
                throw new IOException("Timeout while receiving data");
            }

            this.notifyAll();

            try {
                this.wait(READ_TIMEOUT);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        final int ret = this.buffer[this.out] & 255;

        this.out++;

        if (this.out >= this.buffer.length) {
            this.out = 0;
        }

        if (this.in == this.out) {
            this.in = -1;
        }

        return ret;

    }

    /**
     * Reads up to <code>len</code> bytes of data from the input stream into
     * an array of bytes.  An attempt is made to read as many as
     * <code>len</code> bytes, but a smaller number may be read.
     * The number of bytes actually read is returned as an integer.
     * <p>
     * <p> This method blocks until input data is available, end of file is
     * detected, or an exception is thrown.
     * <p>
     * <p> If <code>len</code> is zero, then no bytes are read and
     * <code>0</code> is returned; otherwise, there is an attempt to read at
     * least one byte. If no byte is available because the stream is at end of
     * file, the value <code>-1</code> is returned; otherwise, at least one
     * byte is read and stored into <code>b</code>.
     * <p>
     * <p> The first byte read is stored into element <code>b[off]</code>, the
     * next one into <code>b[off+1]</code>, and so on. The number of bytes read
     * is, at most, equal to <code>len</code>. Let <i>k</i> be the number of
     * bytes actually read; these bytes will be stored in elements
     * <code>b[off]</code> through <code>b[off+</code><i>k</i><code>-1]</code>,
     * leaving elements <code>b[off+</code><i>k</i><code>]</code> through
     * <code>b[off+len-1]</code> unaffected.
     * <p>
     * <p> In every case, elements <code>b[0]</code> through
     * <code>b[off]</code> and elements <code>b[off+len]</code> through
     * <code>b[b.length-1]</code> are unaffected.
     * <p>
     * <p> The <code>read(b,</code> <code>off,</code> <code>len)</code> method
     * for class <code>InputStream</code> simply calls the method
     * <code>read()</code> repeatedly. If the first such call results in an
     * <code>IOException</code>, that exception is returned from the call to
     * the <code>read(b,</code> <code>off,</code> <code>len)</code> method.  If
     * any subsequent call to <code>read()</code> results in a
     * <code>IOException</code>, the exception is caught and treated as if it
     * were end of file; the bytes read up to that point are stored into
     * <code>b</code> and the number of bytes read before the exception
     * occurred is returned. The default implementation of this method blocks
     * until the requested amount of input data <code>len</code> has been read,
     * end of file is detected, or an exception is thrown. Subclasses are encouraged
     * to provide a more efficient implementation of this method.
     *
     * @param b   the buffer into which the data is read.
     * @param off the start offset in array <code>b</code>
     *            at which the data is written.
     * @param len the maximum number of bytes to read.
     * @return the total number of bytes read into the buffer, or
     * <code>-1</code> if there is no more data because the end of
     * the stream has been reached.
     * @throws IOException               If the first byte cannot be read for any reason
     *                                   other than end of file, or if the input stream has been closed, or if
     *                                   some other I/O error occurs.
     * @throws NullPointerException      If <code>b</code> is <code>null</code>.
     * @throws IndexOutOfBoundsException If <code>off</code> is negative,
     *                                   <code>len</code> is negative, or <code>len</code> is greater than
     *                                   <code>b.length - off</code>
     * @see java.io.InputStream#read()
     */
    @Override
    public synchronized int read(final byte[] b, final int off, int len) throws IOException {

        if (b == null) {
            throw new NullPointerException();
        } else if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return 0;
        }


        final int c = this.read();
        if (c < 0) {
            return -1;
        }

        b[off] = (byte) c;
        int i = 1;

        while (this.in >= 0 && len > 1) {
            int available = (this.in > this.out)
                            ? Math.min(this.buffer.length - this.out, this.in - this.out)
                            : (this.buffer.length - this.out);

            if (available > len - 1) {
                available = len - 1;
            }

            System.arraycopy(this.buffer, this.out, b, off + i, available);
            this.out += available;
            i += available;
            len -= available;

            if (this.out >= this.buffer.length) {
                this.out = 0;
            }

            if (this.in == this.out) {
                this.in = -1;
            }
        }

        return i;


    }

    private synchronized void checkStartTransfer() {

        if (!this.streamHandled) {
            this.streamHandled = true;

            final StreamingStatus status = new StreamingStatus();
            status.setStreamId(this.streamId);
            status.setState(StreamState.STATE_OK);

            this.channelHandlerContext.writeAndFlush(status);
        }

    }

    private synchronized void checkDelayedAck() {

        if (this.ackDelayed && this.buffer.length - this.available() > 0) {
            this.ackDelayed = false;

            final StreamingStatus status = new StreamingStatus();
            status.setStreamId(this.streamId);
            status.setState(StreamState.STATE_OK);

            this.channelHandlerContext.writeAndFlush(status);
        }

    }

    /**
     * Returns an estimate of the number of bytes that can be read (or
     * skipped over) from this input stream without blocking by the next
     * invocation of a method for this input stream. The next invocation
     * might be the same thread or another thread.  A single read or skip of this
     * many bytes will not block, but may read or skip fewer bytes.
     * <p>
     * <p> Note that while some implementations of {@code InputStream} will return
     * the total number of bytes in the stream, many will not.  It is
     * never correct to use the return value of this method to allocate
     * a buffer intended to hold all data in this stream.
     * <p>
     * <p> A subclass' implementation of this method may choose to throw an
     * {@link IOException} if this input stream has been closed by
     * invoking the {@link #close()} method.
     * <p>
     * <p> The {@code available} method for class {@code InputStream} always
     * returns {@code 0}.
     * <p>
     * <p> This method should be overridden by subclasses.
     *
     * @return an estimate of the number of bytes that can be read (or skipped
     * over) from this input stream without blocking or {@code 0} when
     * it reaches the end of the input stream.
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public synchronized int available() {

        if (this.in < 0) {
            return 0;
        } else if (this.in == this.out) {
            return this.buffer.length;
        } else {
            return (this.in > this.out) ? (this.in - this.out) : ((this.in + this.buffer.length) - this.out);
        }
    }

    /**
     * Closes this input stream and releases any system resources associated
     * with the stream.
     *
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public void close() throws IOException {

        this.closedByReader = true;
        synchronized (this) {
            this.in = -1;
        }
    }

    public String getStreamId() {

        return this.streamId;
    }

    public void setStreamId(final String streamId) {

        this.streamId = streamId;
    }
}
