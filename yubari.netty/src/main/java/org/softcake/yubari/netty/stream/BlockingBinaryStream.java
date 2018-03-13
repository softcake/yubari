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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;

public class BlockingBinaryStream extends InputStream {
    private static final Logger log = LoggerFactory.getLogger(BlockingBinaryStream.class);
    boolean closedByWriter = false;
    volatile boolean closedByReader = false;
    protected byte[] buffer;
    protected int in = -1;
    protected int out = 0;
    protected final int ioTimeout;
    private ChannelHandlerContext channelHandlerContext;
    private String streamId;
    private boolean ackDelayed;
    private boolean streamHandled;
    private boolean ioTerminate;
    private String errorMessage;

    public BlockingBinaryStream(String streamId, ChannelHandlerContext channelHandlerContext, int bufferSize, int ioTimeout) {
        this.streamId = streamId;
        this.channelHandlerContext = channelHandlerContext;
        this.ioTimeout = ioTimeout;
        this.initBuffer(bufferSize);
    }

    private void initBuffer(int bufferSize) {
        if (bufferSize <= 0) {
            throw new IllegalArgumentException("Buffer Size <= 0");
        } else {
            this.buffer = new byte[bufferSize];
        }
    }

    public int getIOTimeout() {
        return this.ioTimeout;
    }

    public synchronized boolean isClosed() {
        return this.closedByWriter || this.closedByReader;
    }

    public synchronized void ioTerminate(String message) {
        this.ioTerminate = true;
        this.errorMessage = message;
        this.notifyAll();
    }

    public synchronized void binaryPartReceived(BinaryPartMessage msg) throws IOException {
        byte[] received = msg.getData();
        if (received.length > 0) {
            this.receive(received, 0, received.length);
        }

        if (msg.isEof()) {
            this.receivedLast();
        }

        if (this.buffer.length - this.available() > this.buffer.length - received.length * 2) {
            this.ackDelayed = false;
            StreamingStatus ss = new StreamingStatus();
            ss.setStreamId(msg.getStreamId());
            ss.setState(StreamState.STATE_OK);
            this.channelHandlerContext.writeAndFlush(ss);
        } else {
            this.ackDelayed = true;
        }

    }

    private synchronized void receive(byte[] b, int off, int len) throws IOException {
        this.checkState();
        int bytesToTransfer = len;

        while(bytesToTransfer > 0) {
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

    private void awaitSpace() throws IOException {
        while(this.in == this.out) {
            this.checkState();
            this.notifyAll();

            try {
                this.wait(1000L);
            } catch (InterruptedException var2) {
                throw new InterruptedIOException();
            }
        }

    }

    private synchronized void receivedLast() {
        this.closedByWriter = true;
        this.notifyAll();
    }

    public synchronized boolean isEOF() {
        return this.in < 0 && this.closedByWriter;
    }

    public synchronized int read() throws IOException {
        this.checkStartTransfer();
        this.checkDelayedAck();
        if (this.closedByReader) {
            throw new IOException("Stream closed");
        } else if (this.ioTerminate) {
            throw new IOException(this.errorMessage);
        } else {
            int timeoutThisRead = this.ioTimeout;

            while(this.in < 0) {
                if (this.closedByWriter) {
                    this.checkDelayedAck();
                    return -1;
                }

                timeoutThisRead -= 50;
                if (timeoutThisRead < 0) {
                    throw new IOException("Timeout while receiving data");
                }

                this.notifyAll();

                try {
                    this.wait(50L);
                } catch (InterruptedException var3) {
                    throw new InterruptedIOException();
                }
            }

            int ret = this.buffer[this.out++] & 255;
            if (this.out >= this.buffer.length) {
                this.out = 0;
            }

            if (this.in == this.out) {
                this.in = -1;
            }

            return ret;
        }
    }

    public synchronized int read(byte[] b, int off, int len) throws IOException {
        if (b == null) {
            throw new NullPointerException();
        } else if (off >= 0 && len >= 0 && len <= b.length - off) {
            if (len == 0) {
                return 0;
            } else {
                int c = this.read();
                if (c < 0) {
                    return -1;
                } else {
                    b[off] = (byte)c;
                    int rlen = 1;

                    while(this.in >= 0 && len > 1) {
                        int available;
                        if (this.in > this.out) {
                            available = Math.min(this.buffer.length - this.out, this.in - this.out);
                        } else {
                            available = this.buffer.length - this.out;
                        }

                        if (available > len - 1) {
                            available = len - 1;
                        }

                        System.arraycopy(this.buffer, this.out, b, off + rlen, available);
                        this.out += available;
                        rlen += available;
                        len -= available;
                        if (this.out >= this.buffer.length) {
                            this.out = 0;
                        }

                        if (this.in == this.out) {
                            this.in = -1;
                        }
                    }

                    return rlen;
                }
            }
        } else {
            throw new IndexOutOfBoundsException();
        }
    }

    private synchronized void checkStartTransfer() {
        if (!this.streamHandled) {
            this.streamHandled = true;
            StreamingStatus ss = new StreamingStatus();
            ss.setStreamId(this.streamId);
            ss.setState(StreamState.STATE_OK);
            this.channelHandlerContext.writeAndFlush(ss);
        }

    }

    private synchronized void checkDelayedAck() {
        if (this.ackDelayed && this.buffer.length - this.available() > 0) {
            this.ackDelayed = false;
            StreamingStatus ss = new StreamingStatus();
            ss.setStreamId(this.streamId);
            ss.setState(StreamState.STATE_OK);
            this.channelHandlerContext.writeAndFlush(ss);
        }

    }

    public synchronized int available() {
        if (this.in < 0) {
            return 0;
        } else if (this.in == this.out) {
            return this.buffer.length;
        } else {
            return this.in > this.out ? this.in - this.out : this.in + this.buffer.length - this.out;
        }
    }

    public void close() throws IOException {
        this.closedByReader = true;
        synchronized(this) {
            this.in = -1;
        }
    }

    public String getStreamId() {
        return this.streamId;
    }

    public void setStreamId(String streamId) {
        this.streamId = streamId;
    }
}
