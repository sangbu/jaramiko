/*
 * Copyright (C) 2005 Robey Pointer <robey@lag.net>
 *
 * This file is part of paramiko.
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * 
 * 
 * Created on May 31, 2005
 */

package net.lag.jaramiko;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;
import junit.framework.TestCase;

/**
 * @author robey
 */
public class TransportTest
    extends TestCase
{
    private static Socket[]
    makeSocketPair ()
        throws IOException
    {
        ServerSocket serv = new ServerSocket();
        serv.bind(new InetSocketAddress(InetAddress.getByName("localhost"), 0));

        Socket client = new Socket();
        client.connect(new InetSocketAddress(InetAddress.getByName("localhost"), serv.getLocalPort()));
        
        Socket client2 = serv.accept();
        return new Socket[] { client2, client };
    }
    
    public void
    setUp ()
        throws Exception
    {
        Socket[] pair = makeSocketPair();
        mSocketS = pair[0];
        mSocketC = pair[1];
        mTS = new Transport(mSocketS);
        mTC = new Transport(mSocketC);
        //mTC.setLog(new ConsoleLog());
        //mTS.setLog(new ConsoleLog());
    }
    
    public void
    tearDown ()
        throws Exception
    {
        mTC.close();
        mTS.close();
        mSocketC.close();
        mSocketS.close();
    }
    
    // verify that the security options can be modified
    public void
    testSecurityOptions ()
        throws IOException
    {
        SecurityOptions o = mTC.getSecurityOptions();
        
        List ciphers = o.getCiphers();
        assertTrue(ciphers.size() > 2);
        ciphers.clear();
        ciphers.add("aes256-cbc");
        ciphers.add("blowfish-cbc");
        o.setCiphers(ciphers);
        
        List c2 = o.getCiphers();
        assertEquals(2, c2.size());
        assertEquals("aes256-cbc", (String) c2.get(0));
        assertEquals("blowfish-cbc", (String) c2.get(1));
        
        try {
            ciphers.clear();
            ciphers.add("aes256-cbc");
            ciphers.add("made-up-cipher");
            o.setCiphers(ciphers);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException x) {
            // pass
        }
    }

    // verify that the key generation algorithm works
    public void
    testComputeKey ()
        throws Exception
    {
        mTC.setKH(K, H);
        byte[] key = mTC.computeKey((byte)'C', 16);
        assertEquals("207E66594CA87C44ECCBA3B3CD39FDDB", Util.encodeHex(key));
    }
    
    /*
     * verify that we can establish an ssh link with ourselves across the
     * loopback sockets.  this is hardly "simple" but it's simpler than the
     * later tests. :)
     */
    public void
    testSimple ()
        throws Exception
    {
        PKey hostKey = PKey.readPrivateKeyFromStream(new FileInputStream("test/test_rsa.key"), null);
        PKey publicHostKey = PKey.createFromBase64(hostKey.getBase64());
        mTS.addServerKey(hostKey);
        final FakeServer server = new FakeServer();
        assertEquals(null, mTC.getUsername());
        assertEquals(null, mTS.getUsername());
        assertFalse(mTC.isAuthenticated());
        assertFalse(mTS.isAuthenticated());
        
        final Event sync = new Event();
        new Thread(new Runnable() {
            public void run () {
                try {
                    mTS.startServer(server, 15000);
                    sync.set();
                } catch (IOException x) { }
            }
        }).start();
        mTC.startClient(publicHostKey, 15000);
        mTC.authPassword("slowdive", "pygmalion", 15000);
        sync.waitFor(5000);
        
        assertTrue(sync.isSet());
        assertTrue(mTS.isActive());
        assertEquals("slowdive", mTC.getUsername());
        assertEquals("slowdive", mTS.getUsername());
        assertTrue(mTC.isAuthenticated());
        assertTrue(mTS.isAuthenticated());
    }
    
    /*
     * verify that the client can demand odd handshake settings, and can
     * renegotiate keys in mid-stream.
     */
    public void
    testSpecial ()
        throws Exception
    {
        PKey hostKey = PKey.readPrivateKeyFromStream(new FileInputStream("test/test_rsa.key"), null);
        PKey publicHostKey = PKey.createFromBase64(hostKey.getBase64());
        mTS.addServerKey(hostKey);
        final FakeServer server = new FakeServer();
        
        final Event sync = new Event();
        new Thread(new Runnable() {
            public void run () {
                try {
                    mTS.startServer(server, 15000);
                    sync.set();
                } catch (IOException x) { }
            }
        }).start();
        
        SecurityOptions o = mTC.getSecurityOptions();
        o.setCiphers(Arrays.asList(new String[] { "aes128-cbc" }));
        o.setDigests(Arrays.asList(new String[] { "hmac-md5-96" }));
        mTC.startClient(publicHostKey, 15000);
        mTC.authPassword("slowdive", "pygmalion", 15000);
        sync.waitFor(5000);

        assertTrue(mTS.isActive());
        assertEquals("aes128-cbc", mTC.mAgreedLocalCipher);
        assertEquals("aes128-cbc", mTC.mAgreedRemoteCipher);
        assertEquals(12, mTC.mPacketizer.mMacSizeOut);
        assertEquals(12, mTC.mPacketizer.mMacSizeIn);
        
        mTC.sendIgnore(1024, 15000);
        assertTrue(mTC.renegotiateKeys(15000));
        mTC.sendIgnore(1024, 15000);
    }

    // verify that the keepalive will be sent
    public void
    testKeepalive ()
        throws Exception
    {
        PKey hostKey = PKey.readPrivateKeyFromStream(new FileInputStream("test/test_rsa.key"), null);
        PKey publicHostKey = PKey.createFromBase64(hostKey.getBase64());
        mTS.addServerKey(hostKey);
        final FakeServer server = new FakeServer();
        
        final Event sync = new Event();
        new Thread(new Runnable() {
            public void run () {
                try {
                    mTS.startServer(server, 15000);
                    sync.set();
                } catch (IOException x) { }
            }
        }).start();
        
        mTC.startClient(publicHostKey, 15000);
        mTC.authPassword("slowdive", "pygmalion", 15000);
        sync.waitFor(5000);
        
        assertTrue(sync.isSet());
        assertTrue(mTS.isActive());
        assertEquals(null, server.mGlobalRequest);
        mTC.setKeepAlive(1000);
        try {
            Thread.sleep(2000);
        } catch (InterruptedException x) { }
        assertEquals("keepalive@lag.net", server.mGlobalRequest);
    }

    /*
     * verify that we get the right exception when an unsupported auth type
     * is requested.
     */
    public void
    testBadAuthType ()
        throws Exception
    {
        PKey hostKey = PKey.readPrivateKeyFromStream(new FileInputStream("test/test_rsa.key"), null);
        PKey publicHostKey = PKey.createFromBase64(hostKey.getBase64());
        mTS.addServerKey(hostKey);
        final FakeServer server = new FakeServer();
        
        final Event sync = new Event();
        new Thread(new Runnable() {
            public void run () {
                try {
                    mTS.startServer(server, 15000);
                    sync.set();
                } catch (IOException x) { }
            }
        }).start();
        
        try {
            mTC.startClient(publicHostKey, 15000);
            mTC.authPassword("unknown", "error", 15000);
            fail("expected BadAuthenticationType exception");
        } catch (BadAuthenticationType x) {
            String[] allowed = x.getAllowedTypes();
            assertEquals(1, allowed.length);
            assertEquals("publickey", allowed[0]);
        }
    }
    
    /*
     * verify that a bad password gets the right exception, and that a retry
     * with the right password works.
     */
    public void
    testBadPassword ()
        throws Exception
    {
        PKey hostKey = PKey.readPrivateKeyFromStream(new FileInputStream("test/test_rsa.key"), null);
        PKey publicHostKey = PKey.createFromBase64(hostKey.getBase64());
        mTS.addServerKey(hostKey);
        final FakeServer server = new FakeServer();
        
        final Event sync = new Event();
        new Thread(new Runnable() {
            public void run () {
                try {
                    mTS.startServer(server, 15000);
                    sync.set();
                } catch (IOException x) { }
            }
        }).start();
        
        mTC.startClient(publicHostKey, 15000);
        try {
            mTC.authPassword("slowdive", "error", 15000);
            fail("expected SSHException");
        } catch (SSHException x) {
            // pass
        }
        
        mTC.authPassword("slowdive", "pygmalion", 15000);
        sync.waitFor(5000);
        
        assertTrue(sync.isSet());
        assertTrue(mTS.isActive());
    }
    
    // verify that multipart auth works
    public void
    testMultipartAuth ()
        throws Exception
    {
        PKey hostKey = PKey.readPrivateKeyFromStream(new FileInputStream("test/test_rsa.key"), null);
        PKey publicHostKey = PKey.createFromBase64(hostKey.getBase64());
        mTS.addServerKey(hostKey);
        final FakeServer server = new FakeServer();
        
        final Event sync = new Event();
        new Thread(new Runnable() {
            public void run () {
                try {
                    mTS.startServer(server, 15000);
                    sync.set();
                } catch (IOException x) { }
            }
        }).start();
        
        mTC.startClient(publicHostKey, 15000);
        
        String[] remain = mTC.authPassword("paranoid", "paranoid", 15000);
        assertEquals(1, remain.length);
        assertEquals("publickey", remain[0]);
        PKey key = PKey.readPrivateKeyFromStream(new FileInputStream("test/test_dss.key"), null);
        remain = mTC.authPrivateKey("paranoid", key, 15000);
        assertEquals(0, remain.length);
        
        sync.waitFor(5000);
        assertTrue(sync.isSet());
        assertTrue(mTS.isActive());
    }
    
    // verify that exec_command() does something reasonable
    public void
    testExecCommand ()
        throws Exception
    {
        PKey hostKey = PKey.readPrivateKeyFromStream(new FileInputStream("test/test_rsa.key"), null);
        PKey publicHostKey = PKey.createFromBase64(hostKey.getBase64());
        mTS.addServerKey(hostKey);
        final FakeServer server = new FakeServer();
        
        final Event sync = new Event();
        new Thread(new Runnable() {
            public void run () {
                try {
                    mTS.startServer(server, 15000);
                    sync.set();
                } catch (IOException x) { }
            }
        }).start();
        
        mTC.startClient(publicHostKey, 15000);
        mTC.authPassword("slowdive", "pygmalion", 15000);
        
        sync.waitFor(5000);
        assertTrue(sync.isSet());
        assertTrue(mTS.isActive());
        
        Channel chan = mTC.openSession(5000);
        Channel schan = mTS.accept(5000);
        assertFalse(chan.execCommand("no", 5000));
        chan.close();
        schan.close();
        
        chan = mTC.openSession(5000);
        assertTrue(chan.execCommand("yes", 5000));
        schan = mTS.accept(5000);
        
        schan.getOutputStream().write("Hello there.\n".getBytes());
        schan.getStderrOutputStream().write("This is on stderr.\n".getBytes());
        schan.close();
        
        BufferedReader r = new BufferedReader(new InputStreamReader(chan.getInputStream()));
        assertEquals("Hello there.", r.readLine());
        assertEquals(null, r.readLine());
        r = new BufferedReader(new InputStreamReader(chan.getStderrInputStream()));
        assertEquals("This is on stderr.", r.readLine());
        assertEquals(null, r.readLine());
        chan.close();
        
        // now try it with combined stdout/stderr
        chan = mTC.openSession(5000);
        assertTrue(chan.execCommand("yes", 5000));
        schan = mTS.accept(5000);
        schan.getOutputStream().write("Hello there\n".getBytes());
        schan.getStderrOutputStream().write("This is on stderr.\n".getBytes());
        schan.close();
        
        chan.setCombineStderr(true);
        r = new BufferedReader(new InputStreamReader(chan.getInputStream()));
        assertEquals("Hello there", r.readLine());
        assertEquals("This is on stderr.", r.readLine());
        assertEquals(null, r.readLine());
        chan.close();
    }
    
    // verify that invoke_shell() does something reasonable
    public void
    testInvokeShell ()
        throws Exception
    {
        PKey hostKey = PKey.readPrivateKeyFromStream(new FileInputStream("test/test_rsa.key"), null);
        PKey publicHostKey = PKey.createFromBase64(hostKey.getBase64());
        mTS.addServerKey(hostKey);
        final FakeServer server = new FakeServer();
        
        final Event sync = new Event();
        new Thread(new Runnable() {
            public void run () {
                try {
                    mTS.startServer(server, 15000);
                    sync.set();
                } catch (IOException x) { }
            }
        }).start();
        
        mTC.startClient(publicHostKey, 15000);
        mTC.authPassword("slowdive", "pygmalion", 15000);
        
        sync.waitFor(5000);
        assertTrue(sync.isSet());
        assertTrue(mTS.isActive());
        
        Channel chan = mTC.openSession(5000);
        assertTrue(chan.invokeShell(5000));
        Channel schan = mTS.accept(5000);
        chan.getOutputStream().write("communist j. cat\n".getBytes());
        chan.close();
        
        BufferedReader r = new BufferedReader(new InputStreamReader(schan.getInputStream()));
        assertEquals("communist j. cat", r.readLine());
        schan.close();
    }
    
    // verify that getExitStatus works
    public void
    testExitStatus ()
        throws Exception
    {
        PKey hostKey = PKey.readPrivateKeyFromStream(new FileInputStream("test/test_rsa.key"), null);
        PKey publicHostKey = PKey.createFromBase64(hostKey.getBase64());
        mTS.addServerKey(hostKey);
        final FakeServer server = new FakeServer();
        
        final Event sync = new Event();
        new Thread(new Runnable() {
            public void run () {
                try {
                    mTS.startServer(server, 15000);
                    sync.set();
                } catch (IOException x) { }
            }
        }).start();
        
        mTC.startClient(publicHostKey, 15000);
        mTC.authPassword("slowdive", "pygmalion", 15000);
        
        sync.waitFor(5000);
        assertTrue(sync.isSet());
        assertTrue(mTS.isActive());

        Channel chan = mTC.openSession(5000);
        assertTrue(chan.execCommand("yes", 5000));
        Channel schan = mTS.accept(5000);
        schan.getOutputStream().write("Hello there.\n".getBytes());
        // trigger an EOF
        schan.shutdownRead();
        schan.shutdownWrite();
        schan.sendExitStatus(23);
        schan.close();
        
        BufferedReader r = new BufferedReader(new InputStreamReader(chan.getInputStream()));
        assertEquals("Hello there.", r.readLine());
        assertEquals(null, r.readLine());
        assertEquals(23, chan.getExitStatus(5000));
        chan.close();
    }
    
    
    private Socket mSocketC;
    private Socket mSocketS;
    private Transport mTC;
    private Transport mTS;

    private static final BigInteger K =
        new BigInteger("12328109597968658152337725611420972077453906897310" +
                       "13308727636229713994294810725197135362927727095072" +
                       "96759612401802191955568143056534122385270077606457" +
                       "72155346973065923356933935614008528405243669748075" +
                       "95105196728487437944334601131189868168266248652911" +
                       "16513647975790797391795651716378444844877749505443" +
                       "714557929");
    private static final byte[] H =
        Util.decodeHex("0C8307CDE6856FF30BA93684EB0F04C2520E9ED3");
}