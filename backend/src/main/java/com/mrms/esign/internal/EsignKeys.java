package com.mrms.esign.internal;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Key material for eSign.
 *
 * <ul>
 *   <li>ASP key and certificate: sign every request sent to the ESP (issued to
 *       the Directorate when it registers as an Application Service Provider).</li>
 *   <li>ESP certificates: verify the ESP's signature on every response.</li>
 *   <li>Trusted CAs: signer certificates must chain to one of these (the
 *       Controller of Certifying Authorities root and the ESP's CA).</li>
 * </ul>
 * For the development simulator every key is generated at start up and never leaves memory.
 */
final class EsignKeys {

    final PrivateKey aspKey;
    final X509Certificate aspCertificate;
    final List<X509Certificate> espCertificates;
    final List<X509Certificate> trustedCas;
    /** Present only for the simulator. */
    final Simulator simulator;

    /** The simulated ESP's own keys: one to sign responses, one CA to issue short lived signer certificates. */
    record Simulator(PrivateKey espKey, X509Certificate espCertificate, PrivateKey caKey, X509Certificate caCertificate) {
    }

    private EsignKeys(PrivateKey aspKey, X509Certificate aspCertificate, List<X509Certificate> espCertificates,
                      List<X509Certificate> trustedCas, Simulator simulator) {
        this.aspKey = aspKey;
        this.aspCertificate = aspCertificate;
        this.espCertificates = espCertificates;
        this.trustedCas = trustedCas;
        this.simulator = simulator;
    }

    static EsignKeys load(String keystorePath, String keystorePassword, String alias, String espCertificatePem,
                          String trustedCaPem) {
        try {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            try (InputStream in = Files.newInputStream(Path.of(keystorePath))) {
                ks.load(in, keystorePassword.toCharArray());
            }
            PrivateKey key = (PrivateKey) ks.getKey(alias, keystorePassword.toCharArray());
            X509Certificate cert = (X509Certificate) ks.getCertificate(alias);
            if (key == null || cert == null) {
                throw new IllegalStateException("No key and certificate with alias " + alias + " in " + keystorePath);
            }
            return new EsignKeys(key, cert, certificates(espCertificatePem), certificates(trustedCaPem), null);
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Could not load the eSign keys: " + e.getMessage(), e);
        }
    }

    /** Fresh in memory keys for the development simulator. */
    static EsignKeys simulated() {
        try {
            KeyPair asp = rsa();
            KeyPair esp = rsa();
            KeyPair ca = rsa();
            X509Certificate aspCert = selfSigned(asp, "CN=MRMS Development ASP, O=MRMS (simulated)", false);
            X509Certificate espCert = selfSigned(esp, "CN=Simulated eSign Service Provider, O=MRMS (simulated)", false);
            X509Certificate caCert = selfSigned(ca, "CN=Simulated eSign CA, O=MRMS (simulated)", true);
            return new EsignKeys(asp.getPrivate(), aspCert, List.of(espCert), List.of(caCert),
                    new Simulator(esp.getPrivate(), espCert, ca.getPrivate(), caCert));
        } catch (GeneralSecurityException | OperatorCreationException e) {
            throw new IllegalStateException("Could not create simulator keys", e);
        }
    }

    static KeyPair rsa() throws GeneralSecurityException {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048, new SecureRandom());
        return gen.generateKeyPair();
    }

    private static X509Certificate selfSigned(KeyPair pair, String dn, boolean ca)
            throws GeneralSecurityException, OperatorCreationException {
        Instant now = Instant.now();
        X500Name name = new X500Name(dn);
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(name, serial(), Date.from(now.minusSeconds(60)),
                Date.from(now.plus(Duration.ofDays(365))), name, pair.getPublic());
        try {
            builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(ca));
            if (ca) {
                builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
            }
        } catch (IOException e) {
            throw new GeneralSecurityException(e);
        }
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(pair.getPrivate());
        return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
    }

    static BigInteger serial() {
        return new BigInteger(64, new SecureRandom()).abs().add(BigInteger.ONE);
    }

    private static List<X509Certificate> certificates(String pemPath) throws IOException, GeneralSecurityException {
        if (pemPath == null || pemPath.isBlank()) {
            throw new IllegalStateException("A certificate file is required for eSign");
        }
        List<X509Certificate> result = new ArrayList<>();
        try (InputStream in = Files.newInputStream(Path.of(pemPath))) {
            CertificateFactory.getInstance("X.509").generateCertificates(in)
                    .forEach(c -> result.add((X509Certificate) c));
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("No certificates in " + pemPath);
        }
        return result;
    }
}
