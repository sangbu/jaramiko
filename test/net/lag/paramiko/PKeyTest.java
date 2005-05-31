/*
 * Created on May 21, 2005
 */

package net.lag.paramiko;

import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import junit.framework.TestCase;

/**
 * @author robey
 */
public class PKeyTest
    extends TestCase
{

    public void
    testGenerateKey ()
        throws Exception
    {
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        byte[] salt = { 1, 2, 3, 4 };
        byte[] key = PKey.generateKeyBytes(md5, salt, "happy birthday".getBytes(), 30);
        
        String exps = "61E1F272F4C1C4561586BD322498C0E924672780F47BB37DDA7D54019E64";
        byte[] exp = Util.decodeHex(exps);
        assertTrue(Arrays.equals(exp, key));
    }

    public void
    testLoadRSA ()
        throws Exception
    {
        PKey rsa = PKey.readPrivateKeyFromStream(new FileInputStream("test/test_rsa.key"), null);
        assertEquals("ssh-rsa", rsa.getSSHName());
        assertEquals(RSA_FINGERPRINT, Util.encodeHex(rsa.getFingerprint()));
        assertEquals(PUB_RSA, rsa.getBase64());
        assertEquals(1024, rsa.getBits());
    }

    public void
    testLoadRSAPassword ()
        throws Exception
    {
        PKey rsa = PKey.readPrivateKeyFromStream(new FileInputStream("test/test_rsa_password.key"), "television");
        assertEquals("ssh-rsa", rsa.getSSHName());
        assertEquals(RSA_FINGERPRINT, Util.encodeHex(rsa.getFingerprint()));
        assertEquals(PUB_RSA, rsa.getBase64());
        assertEquals(1024, rsa.getBits());
    }
    
    public void
    testLoadDSS ()
        throws Exception
    {
        PKey dss = PKey.readPrivateKeyFromStream(new FileInputStream("test/test_dss.key"), null);
        assertEquals("ssh-dss", dss.getSSHName());
        assertEquals(DSS_FINGERPRINT, Util.encodeHex(dss.getFingerprint()));
        assertEquals(PUB_DSS, dss.getBase64());
        assertEquals(1024, dss.getBits());
    }
    
    public void
    testLoadDSSPassword ()
        throws Exception
    {
        PKey dss = PKey.readPrivateKeyFromStream(new FileInputStream("test/test_dss_password.key"), "television");
        assertEquals("ssh-dss", dss.getSSHName());
        assertEquals(DSS_FINGERPRINT, Util.encodeHex(dss.getFingerprint()));
        assertEquals(PUB_DSS, dss.getBase64());
        assertEquals(1024, dss.getBits());
    }
    
    // verify that the public & private keys compare equal
    public void
    testCompareRSA ()
        throws Exception
    {
        PKey rsa = PKey.readPrivateKeyFromStream(new FileInputStream("test/test_rsa.key"), null);
        assertEquals(rsa, rsa);
        PKey pub = PKey.createFromData(rsa.toByteArray());
        assertTrue(rsa.canSign());
        assertTrue(! pub.canSign());
        assertEquals(rsa, pub);
    }
    
    public void
    testCompareDSS ()
        throws Exception
    {
        PKey dss = PKey.readPrivateKeyFromStream(new FileInputStream("test/test_dss.key"), null);
        assertEquals(dss, dss);
        PKey pub = PKey.createFromData(dss.toByteArray());
        assertTrue(dss.canSign());
        assertTrue(! pub.canSign());
        assertEquals(dss, pub);
    }

    public void
    testSignRSA ()
        throws Exception
    {
        PKey rsa = PKey.readPrivateKeyFromStream(new FileInputStream("test/test_rsa.key"), null);
        Message m = rsa.signSSHData(new FakeRandom(), "ice weasels".getBytes());
        m.rewind();
        assertEquals("ssh-rsa", m.getString());
        assertEquals(SIGNED_RSA, Util.encodeHex(m.getByteString()));
        
        m.rewind();
        PKey pub = PKey.createFromData(rsa.toByteArray());
        assertTrue(pub.verifySSHSignature("ice weasels".getBytes(), m));
    }
    
    public void
    testSignDSS ()
        throws Exception
    {
        PKey dss = PKey.readPrivateKeyFromStream(new FileInputStream("test/test_dss.key"), null);
        Message m = dss.signSSHData(new FakeRandom(), "ice weasels".getBytes());
        m.rewind();
        assertEquals("ssh-dss", m.getString());
        assertEquals(SIGNED_DSS, Util.encodeHex(m.getByteString()));

        m.rewind();
        PKey pub = PKey.createFromData(dss.toByteArray());
        assertTrue(pub.verifySSHSignature("ice weasels".getBytes(), m));
    }
    
    // don't test key generation, it's broken in the java crypto library
    public void
    XXXtestGenerateRSA ()
        throws Exception
    {
        RSAKey rsa = RSAKey.generate(1024, new FakeRandom());
        Message m = rsa.signSSHData(new FakeRandom(), "jerri blank".getBytes());
        m.rewind();
        assertTrue(rsa.verifySSHSignature("jerri blank".getBytes(), m));
    }

    
    private static final String RSA_FINGERPRINT = "60733844CB5186657FDEDAA22B5A57D5";
    private static final String DSS_FINGERPRINT = "4478F0B9A23CC5182009FF755BC1D26C";
    
    private static final String PUB_RSA =
        "AAAAB3NzaC1yc2EAAAABIwAAAIEA049W6geFpmsljTwfvI1UmKWWJPNFI74+vNKT" +
        "k4dmzkQY2yAMs6FhlvhlI8ysU4oj71ZsRYMecHbBbxdN79+JRFVYTKaLqjwGENeT" +
        "d+yv4q+V2PvZv3fLnzApI3l7EJCqhWwJUHJ1jAkZzqDx0tyOL4uoZpww3nmE0kb3" +
        "y21tH4c=";
    private static final String PUB_DSS = 
        "AAAAB3NzaC1kc3MAAACBAOeBpgNnfRzr/twmAQRu2XwWAp3CFtrVnug6s6fgwj/o" +
        "LjYbVtjAy6pl/h0EKCWx2rf1IetyNsTxWrniA9I6HeDj65X1FyDkg6g8tvCnaNB8" +
        "Xp/UUhuzHuGsMIipRxBxw9LF608EqZcj1E3ytktoW5B5OcjrkEoz3xG7C+rpIjYv" +
        "AAAAFQDwz4UnmsGiSNu5iqjn3uTzwUpshwAAAIEAkxfFeY8P2wZpDjX0MimZl5wk" +
        "oFQDL25cPzGBuB4OnB8NoUk/yjAHIIpEShw8V+LzouMK5CTJQo5+Ngw3qIch/WgR" +
        "mMHy4kBq1SsXMjQCte1So6HBMvBPIW5SiMTmjCfZZiw4AYHK+B/JaOwaG9yRg2Ej" +
        "g4Ok10+XFDxlqZo8Y+wAAACARmR7CCPjodxASvRbIyzaVpZoJ/Z6x7dAumV+ysrV" +
        "1BVYd0lYukmnjO1kKBWApqpH1ve9XDQYN8zgxM4b16L21kpoWQnZtXrY3GZ4/it9" +
        "kUgyB7+NwacIBlXa8cMDL7Q/69o0d54U0X/NeX5QxuYR6OMJlrkQB7oiW/P/1mwj" +
        "QgE=";

    private static final String SIGNED_RSA =
        "20D78A3121CBF79212F2A48937F578AFE616B625B9973DA2CD5FCA2021734CAD" +
        "34738F207728E2941508D891407A8583BF183795DC541A9B88296C73CA38B404" +
        "F156B9F2429D521B2929B44FFDC92DAF47D2407630F363450CD91D43860F1C70" +
        "E2931234F3ACC50A2F14506659F188EEC14AE9D19C4E46F00E476F3874F144A8";
    private static final String SIGNED_DSS =
        "302D02145B6835B10278B432E014BBA487CB4D23415FFA87021500C1FBCC29C2" +
        "3DB51734732E179D6C3542E1527E8D";
}