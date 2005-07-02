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
 * Created on May 23, 2005
 */

package net.lag.paramiko;

import java.io.IOException;
import java.math.BigInteger;

/**
 * @author robey
 */
public class FakeTransport
    implements TransportInterface
{
    public void
    sendMessage (Message m)
    {
        mMessage = m;
    }
    
    public void
    sendUserMessage (Message m, int timeout_ms)
    {
        mUserMessage = m;
    }
    
    public void
    setKH (BigInteger k, byte[] h)
    {
        mK = k;
        mH = h;
    }
    
    public void
    saveException (IOException x)
    {
        // pass
    }
    	 
    public void
    activateOutbound ()
    {
        mActivated = true;
    }
    
    public String
    getRemoteVersion ()
    {
        return "SSH-2.0-lame";
    }
    
    public String
    getLocalVersion ()
    {
        return "SSH-2.0-paramiko_1.0";
    }
    
    public byte[]
    getRemoteKexInit ()
    {
        return "remote-kex-init".getBytes();
    }
    
    public byte[]
    getLocalKexInit ()
    {
        return "local-kex-init".getBytes();
    }
    
    public void
    verifyKey (byte[] key, byte[] sig)
    {
        mVerifyKey = key;
        mVerifySig = sig;
    }
    
    public void
    expectPacket (byte expect)
    {
        mExpect = expect;
    }
    
    public boolean
    inServerMode ()
    {
        return mServerMode;
    }
    
    public PKey
    getServerKey ()
    {
        return new FakeKey();
    }
    
    public byte[]
    getSessionID ()
    {
        return null;
    }

    public void
    registerMessageHandler (byte ptype, MessageHandler handler)
    {
        // pass
    }
    
    public void
    unlinkChannel (int chanID)
    {
        // pass
    }
    
    public void
    close ()
    {
        // pass
    }
    
    
    public Message mMessage;
    public Message mUserMessage;
    public byte mExpect;
    public BigInteger mK;
    public byte[] mH;
    public byte[] mVerifyKey;
    public byte[] mVerifySig;
    public boolean mActivated = false;
    public boolean mServerMode = false;
}
