/*
 * Copyright (C) 2005 Robey Pointer <robey@lag.net>
 *
 * This file is part of paramiko.
 *
 * Paramiko is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * Paramiko is distrubuted in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Paramiko; if not, write to the Free Software Foundation, Inc.,
 * 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA.
 *
 *
 * Created on May 7, 2005
 */

package net.lag.paramiko;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.ShortBufferException;


/**
 * Stream for reading and writing SSH2 {@link Message} objects.  Encryption and
 * re-keying are handled at this layer.
 * 
 * @author robey
 */
/* package */ class Packetizer
{
    public
    Packetizer (InputStream in, OutputStream out, SecureRandom random)
        throws IOException
    {
        mInStream = in;
        mOutStream = out;
        mRandom = random;
        mClosed = false;
        mLog = new NullLog();
        mDumpPackets = false;
        mKeepAliveInterval = 0;
        
        mWriteLock = new Object();
        mReadBuffer = new byte[64];
    }
    
    public void
    setLog (LogSink log)
    {
        mLog = log;
    }

    public void
    setDumpPackets (boolean dump)
    {
        mDumpPackets = dump;
    }
    
    public synchronized void
    close ()
    {
        mClosed = true;
    }
    
    public synchronized void
    setKeepAlive (int interval, KeepAliveHandler handler)
    {
        mKeepAliveInterval = interval;
        mKeepAliveHandler = handler;
        mKeepAliveLast = System.currentTimeMillis();
    }
    
    /**
     * Return true if it's time to re-negotiate keys on this session.  This
     * needs to be done after about every 1GB of traffic.
     * 
     * @return true if it's time to rekey
     */
    public synchronized boolean
    needRekey ()
    {
        return mNeedRekey;
    }
    
    public void
    setOutboundCipher (Cipher cipher, int blockSize, Mac mac, int macSize)
    {
        synchronized (mWriteLock) {
            mBlockEngineOut = cipher;
            mBlockSizeOut = blockSize;
            mMacEngineOut = mac;
            mMacSizeOut = macSize;
            
            mSentBytes = 0;
            mSentPackets = 0;
            mNeedRekey = false;
            
            mMacBufferOut = new byte[32];
        }
    }
    
    // FIXME: how do i guarantee that nobody's in read() while this is happening?
    public void
    setInboundCipher (Cipher cipher, int blockSize, Mac mac, int macSize)
    {
        mBlockEngineIn = cipher;
        mBlockSizeIn = blockSize;
        mMacEngineIn = mac;
        mMacSizeIn = macSize;
     
        mReceivedBytes = 0;
        mReceivedPackets = 0;
        mReceivedPacketsOverflow = 0;
        mNeedRekey = false;
        
        mMacBufferIn = new byte[32];
    }
    
    /**
     * Write an SSH2 message to the stream.  The message will be packetized
     * (padded up to the block size), and if the outbound cipher is on, the
     * message will also be enciphered.
     * 
     * @param msg the message to send
     * @throws IOException if an exception is thrown while writing data
     */
    public void
    write (Message msg)
        throws IOException
    {
        msg.packetize(mRandom, mBlockSizeOut);
        byte[] packet = msg.toByteArray();
        int length = msg.getPosition();
        mLog.debug("Write packet <" + MessageType.getDescription(packet[5]) + ">, length " + length);
        if (mDumpPackets) {
            mLog.dump("OUT", packet, 0, length);
        }
        
        synchronized (mWriteLock) {
            if (mBlockEngineOut != null) {
                try {
                    mBlockEngineOut.update(packet, 0, length, packet, 0);
                } catch (ShortBufferException x) {
                    throw new IOException("encipher error: " + x);
                }

                new Message(mMacBufferOut).putInt(mSequenceNumberOut);
                mMacEngineOut.update(mMacBufferOut, 0, 4);
                mMacEngineOut.update(packet, 0, length);
                try {
                    mMacEngineOut.doFinal(mMacBufferOut, 0);
                } catch (ShortBufferException x) {
                    throw new IOException("mac error: " + x);
                }
            }
            
            mSequenceNumberOut++;
            write(packet, 0, length);
            if (mBlockEngineOut != null) {
                write(mMacBufferOut, 0, mMacSizeOut);
            }
            
            mSentBytes += length;
            mSentPackets++;
            if (((mSentPackets >= REKEY_PACKETS) || (mSentBytes >= REKEY_BYTES)) && ! mNeedRekey) {
                // only ask once for rekeying
                mLog.debug("Rekeying (hit " + mSentPackets + " packets, " + mSentBytes + " bytes sent)");
                mReceivedPacketsOverflow = 0;
                triggerRekey();
            }
        }
    }
    
    // only 1 thread will be here at one time
    // return null on EOF
    public Message
    read ()
        throws IOException
    {
        // [ab]use mMacBufferIn for reading the first block
        if (read(mReadBuffer, 0, mBlockSizeIn) < 0) {
            return null;
        }
        if (mBlockEngineIn != null) {
            try {
                mBlockEngineIn.update(mReadBuffer, 0, mBlockSizeIn, mReadBuffer, 0);
            } catch (ShortBufferException x) {
                throw new IOException("decode error: " + x);
            }
        }
        if (mDumpPackets) {
            mLog.dump("IN", mReadBuffer, 0, mBlockSizeIn);
        }
        int length = new Message(mReadBuffer).getInt();
        int leftover = mBlockSizeIn - 5;
        if ((length + 4) % mBlockSizeIn != 0) {
            throw new IOException("Invalid packet blocking");
        }
        int padding = mReadBuffer[4];       // all cipher block sizes are >= 8
        if ((padding < 0) || (padding > 32)) {
            throw new IOException("invalid padding");
        }

        byte[] packet = new byte[length - 1];
        System.arraycopy(mReadBuffer, 5, packet, 0, leftover);
        if (read(packet, leftover, length - leftover - 1) < 0) {
            return null;
        }
        if (mBlockEngineIn != null) {
            try {
                mBlockEngineIn.update(packet, leftover, length - leftover - 1, packet, leftover);
            } catch (ShortBufferException x) {
                throw new IOException("decode error: " + x);
            }

            // now, compute the mac
            new Message(mMacBufferIn).putInt(mSequenceNumberIn);
            mMacEngineIn.update(mMacBufferIn, 0, 4);
            mMacEngineIn.update(mReadBuffer, 0, 5);
            mMacEngineIn.update(packet, 0, length - 1);        
            try {
                mMacEngineIn.doFinal(mMacBufferIn, 0);
            } catch (ShortBufferException x) {
                throw new IOException("mac error: " + x);
            }
            if (read(mReadBuffer, 0, mMacSizeIn) < 0) {
                return null;
            }
            
            // i'm pretty mad that there's no more efficient way to do this. >:(
            for (int i = 0; i < mMacSizeIn; i++) {
                if (mReadBuffer[i] != mMacBufferIn[i]) {
                    throw new IOException("mac mismatch");
                }
            }
        }
        if (mDumpPackets) {
            mLog.dump("IN", packet, leftover, length - leftover - 1);
        }

        Message msg = new Message(packet, 0, length - padding - 1, mSequenceNumberIn);
        mSequenceNumberIn++;
        
        // check for rekey
        mReceivedBytes += length + mMacSizeIn + 4;
        mReceivedPackets++;
        if (needRekey()) {
            // we've asked to rekey -- give them 20 packets to comply before dropping the connection
            mReceivedPacketsOverflow++;
            if (mReceivedPacketsOverflow >= 20) {
                throw new IOException("rekey requests are being ignored");
            }
        } else if ((mReceivedPackets >= REKEY_PACKETS) || (mReceivedBytes >= REKEY_BYTES)) {
            // only ask once
            mLog.debug("Rekeying (hit " + mReceivedPackets + " packets, " +
                       mReceivedBytes + " bytes received)");
            mReceivedPacketsOverflow = 0;
            triggerRekey();
        }

        return msg;
    }
    
    // do not return until the entire buffer is read, or EOF
    private int
    read (byte[] buffer, int offset, int length)
        throws IOException
    {
        int total = 0;
        while (true) {
            try {
                int n = mInStream.read(buffer, offset + total, length - total);
                if (n > 0) {
                    total += n;
                }
                if (n < 0) {
                    // EOF: no partial results
                    return n;
                }
            } catch (SocketTimeoutException x) {
                // pass
            }
            
            if (total == length) {
                return total;
            }

            synchronized (this) {
                if (mClosed) {
                    return -1;
                }
            }
            checkKeepAlive();
        }
    }
    
    private void
    write (byte[] buffer, int offset, int length)
        throws IOException
    {
        // setSoTimeout() does not affect writes in java
        mOutStream.write(buffer, offset, length);
    }
    
    // really inefficient, but only used for 1 line at the start of the session
    private String
    readline ()
        throws IOException
    {
        StringBuffer line = new StringBuffer();
        
        while (true) {
            int c = mInStream.read();
            if (c < 0) {
                return null;
            }
            // only ASCII is allowed here, so this is ok; calm down. :)
            if ((char)c == '\n') {
                if ((line.length() > 0) && (line.charAt(line.length() - 1) == '\r')) {
                    line.setLength(line.length() - 1);
                }
                return line.toString();
            }
            line.append((char)c);
        }
    }
    
    private void
    checkKeepAlive ()
    {
        if ((mKeepAliveInterval == 0) || (mBlockEngineOut == null) || mNeedRekey) {
            // wait till we're in a normal state
            return;
        }
        long now = System.currentTimeMillis();
        if (now > mKeepAliveLast + mKeepAliveInterval) {
            mKeepAliveHandler.keepAliveEvent();
            mKeepAliveLast = now;
        }
    }
    
    private synchronized void
    triggerRekey ()
    {
        mNeedRekey = true;
    }
    
    
    private final static int READ_TIMEOUT = 1000;
    private final static int WRITE_TIMEOUT = 1000;
    
    /* READ the secsh RFC's before raising these values.  if anything, they
     * should probably be lower.
     */
    private final static int REKEY_PACKETS = 0x40000000;
    private final static int REKEY_BYTES = 0x40000000;      // 1GB

    private InputStream mInStream;
    private OutputStream mOutStream;
    private SecureRandom mRandom;
    private LogSink mLog;
    private boolean mClosed;
    private boolean mDumpPackets;
    private boolean mNeedRekey;
    
    private Object mWriteLock;
    private byte[] mReadBuffer;     // used for reading the first block of a packet
    
    private int mBlockSizeOut = 8;
    private int mBlockSizeIn = 8;
    private Cipher mBlockEngineOut;
    private Cipher mBlockEngineIn;
    private Mac mMacEngineOut;
    private Mac mMacEngineIn;
    private byte[] mMacBufferOut;
    private byte[] mMacBufferIn;
    private int mMacSizeOut;
    private int mMacSizeIn;
    private int mSequenceNumberOut;
    private int mSequenceNumberIn;
    
    private long mSentBytes;
    private long mSentPackets;
    private long mReceivedBytes;
    private long mReceivedPackets;
    private int mReceivedPacketsOverflow;
    
    private int mKeepAliveInterval;
    private long mKeepAliveLast;
    private KeepAliveHandler mKeepAliveHandler;
}
