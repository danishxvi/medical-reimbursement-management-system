package com.mrms.esign.internal;

import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DigestCalculator;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.SignedInfo;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.security.PrivateKey;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The eSign exchange with an eSign Service Provider, following the shape
 * of the CCA eSign API (version 2.1): the ASP posts a signed
 * {@code <Esign>} request carrying the SHA-256 of the document through the
 * user's browser; the ESP authenticates the user (Aadhaar OTP or biometric),
 * signs the hash with a short lived certificate issued to the user, and
 * posts back a signed {@code <EsignResp>} with a PKCS#7 signature.
 *
 * <p>Element and attribute names follow the published API. Form field names
 * and optional attributes differ slightly between providers, so they are
 * configuration (see docs/15-esign.md) and must be checked against the
 * chosen ESP's integration kit before go live.
 */
final class EsignProtocol {

    static final String VERSION = "2.1";
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
            .withZone(ZoneId.of("Asia/Kolkata"));

    private EsignProtocol() {
    }

    /** A response after XML signature verification. */
    record Response(String txn, boolean success, String errorCode, String errorMessage, String userCertificate,
                    String pkcs7) {
    }

    /** The signer's verified certificate. */
    record Signer(String name, String serial, String issuer, X509Certificate certificate) {
    }

    // ------------------------------------------------------------------
    // Request (ASP side)
    // ------------------------------------------------------------------

    static String signedRequest(String txn, String aspId, String responseUrl, String digestHex, String documentInfo,
                                Instant now, PrivateKey aspKey, X509Certificate aspCertificate) {
        Document doc = newDocument();
        Element esign = doc.createElement("Esign");
        esign.setAttribute("ver", VERSION);
        esign.setAttribute("sc", "Y");
        esign.setAttribute("ts", TS.format(now));
        esign.setAttribute("txn", txn);
        esign.setAttribute("ekycId", "");
        esign.setAttribute("ekycIdType", "A");
        esign.setAttribute("aspId", aspId);
        esign.setAttribute("AuthMode", "1");
        esign.setAttribute("responseSigType", "pkcs7");
        esign.setAttribute("responseUrl", responseUrl);
        doc.appendChild(esign);
        Element docs = doc.createElement("Docs");
        Element hash = doc.createElement("InputHash");
        hash.setAttribute("id", "1");
        hash.setAttribute("hashAlgorithm", "SHA256");
        hash.setAttribute("docInfo", documentInfo);
        hash.setTextContent(digestHex);
        docs.appendChild(hash);
        esign.appendChild(docs);
        sign(doc, aspKey, aspCertificate);
        return serialize(doc);
    }

    /** Parses and checks a request (used by the simulator, as a real ESP would). */
    static Document verifiedRequest(String xml, List<X509Certificate> aspCertificates) {
        Document doc = parse(xml);
        if (!"Esign".equals(doc.getDocumentElement().getTagName()) || !verifySignature(doc, aspCertificates)) {
            throw new IllegalArgumentException("The eSign request is not signed by a registered ASP");
        }
        return doc;
    }

    // ------------------------------------------------------------------
    // Response (ASP side)
    // ------------------------------------------------------------------

    static Response verifiedResponse(String xml, List<X509Certificate> espCertificates) {
        Document doc = parse(xml);
        Element root = doc.getDocumentElement();
        if (!"EsignResp".equals(root.getTagName())) {
            throw new IllegalArgumentException("Not an eSign response");
        }
        if (!verifySignature(doc, espCertificates)) {
            throw new IllegalArgumentException("The eSign response is not signed by the configured ESP");
        }
        boolean success = "1".equals(root.getAttribute("status"));
        return new Response(root.getAttribute("txn"), success, root.getAttribute("errCode"),
                root.getAttribute("errMsg"), text(root, "UserX509Certificate"), text(root, "DocSignature"));
    }

    /**
     * Checks that the PKCS#7 signature covers exactly {@code digest}, was made
     * with the certificate it carries, and that the certificate chains to a
     * trusted CA and was valid at {@code at}.
     */
    static Signer verifyDocumentSignature(byte[] pkcs7, byte[] digest, List<X509Certificate> trustedCas, Instant at) {
        try {
            CMSSignedData signed = new CMSSignedData(Map.of(NISTObjectIdentifiers.id_sha256, digest), pkcs7);
            Collection<SignerInformation> signers = signed.getSignerInfos().getSigners();
            if (signers.size() != 1) {
                throw new IllegalArgumentException("Expected exactly one signer");
            }
            SignerInformation signer = signers.iterator().next();
            if (!NISTObjectIdentifiers.id_sha256.equals(signer.getDigestAlgorithmID().getAlgorithm())) {
                throw new IllegalArgumentException("Only SHA-256 signatures are accepted");
            }
            @SuppressWarnings("unchecked")
            Collection<X509CertificateHolder> matches = signed.getCertificates().getMatches(signer.getSID());
            if (matches.isEmpty()) {
                throw new IllegalArgumentException("The signer's certificate is missing");
            }
            JcaX509CertificateConverter converter = new JcaX509CertificateConverter();
            X509Certificate certificate = converter.getCertificate(matches.iterator().next());
            if (!signer.verify(new JcaSimpleSignerInfoVerifierBuilder().build(certificate))) {
                throw new IllegalArgumentException("The signature does not match the document");
            }
            @SuppressWarnings("unchecked")
            Collection<X509CertificateHolder> all = signed.getCertificates().getMatches(null);
            List<X509Certificate> chain = new ArrayList<>();
            for (X509CertificateHolder h : all) {
                chain.add(converter.getCertificate(h));
            }
            validateChain(certificate, chain, trustedCas, at);
            return new Signer(commonName(certificate), certificate.getSerialNumber().toString(16).toUpperCase(),
                    certificate.getIssuerX500Principal().getName(), certificate);
        } catch (CMSException | OperatorCreationException | java.security.GeneralSecurityException e) {
            throw new IllegalArgumentException("The signature could not be verified: " + e.getMessage(), e);
        }
    }

    private static void validateChain(X509Certificate leaf, List<X509Certificate> supplied,
                                      List<X509Certificate> trustedCas, Instant at)
            throws java.security.GeneralSecurityException {
        Set<TrustAnchor> anchors = trustedCas.stream().map(c -> new TrustAnchor(c, null)).collect(Collectors.toSet());
        List<X509Certificate> path = new ArrayList<>();
        path.add(leaf);
        // Intermediates carried in the signature, excluding the leaf and any trust anchor itself
        supplied.stream()
                .filter(c -> !c.equals(leaf) && !trustedCas.contains(c))
                .forEach(path::add);
        CertPath certPath = CertificateFactory.getInstance("X.509").generateCertPath(path);
        PKIXParameters params = new PKIXParameters(anchors);
        // Revocation is checked by the ESP at issue time; eSign certificates live for minutes.
        // Enable OCSP here if the Directorate's policy requires it.
        params.setRevocationEnabled(false);
        params.setDate(Date.from(at));
        CertPathValidator.getInstance("PKIX").validate(certPath, params);
    }

    // ------------------------------------------------------------------
    // Simulator (ESP side, development only)
    // ------------------------------------------------------------------

    /** PKCS#7 over a known hash, as an ESP produces it (detached, signed attributes carry the digest). */
    static byte[] signHash(byte[] digest, PrivateKey key, X509Certificate certificate, X509Certificate ca) {
        try {
            DigestCalculatorProvider fixed = algorithm -> new DigestCalculator() {
                @Override
                public AlgorithmIdentifier getAlgorithmIdentifier() {
                    return algorithm;
                }

                @Override
                public OutputStream getOutputStream() {
                    return OutputStream.nullOutputStream();
                }

                @Override
                public byte[] getDigest() {
                    return digest.clone();
                }
            };
            ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(key);
            CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
            gen.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(fixed).build(signer, certificate));
            gen.addCertificates(new JcaCertStore(List.of(certificate, ca)));
            return gen.generate(new CMSProcessableByteArray(new byte[0]), false).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("Could not sign", e);
        }
    }

    static String signedResponse(String txn, boolean success, String errorCode, String errorMessage,
                                 X509Certificate userCertificate, byte[] pkcs7, Instant now,
                                 PrivateKey espKey, X509Certificate espCertificate) {
        try {
            Document doc = newDocument();
            Element resp = doc.createElement("EsignResp");
            resp.setAttribute("ver", VERSION);
            resp.setAttribute("status", success ? "1" : "0");
            resp.setAttribute("ts", TS.format(now));
            resp.setAttribute("txn", txn);
            resp.setAttribute("resCode", java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 20));
            resp.setAttribute("errCode", success ? "NA" : errorCode);
            resp.setAttribute("errMsg", success ? "NA" : errorMessage);
            doc.appendChild(resp);
            if (success) {
                Element cert = doc.createElement("UserX509Certificate");
                cert.setTextContent(Base64.getEncoder().encodeToString(userCertificate.getEncoded()));
                resp.appendChild(cert);
                Element signatures = doc.createElement("Signatures");
                Element docSignature = doc.createElement("DocSignature");
                docSignature.setAttribute("id", "1");
                docSignature.setAttribute("sigHashAlgorithm", "SHA256");
                docSignature.setAttribute("error", "");
                docSignature.setTextContent(Base64.getEncoder().encodeToString(pkcs7));
                signatures.appendChild(docSignature);
                resp.appendChild(signatures);
            }
            sign(doc, espKey, espCertificate);
            return serialize(doc);
        } catch (java.security.cert.CertificateEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------
    // XML helpers
    // ------------------------------------------------------------------

    /**
     * Parses XML with every external resource and DTD disabled (no XXE, no
     * entity expansion attacks).
     */
    static Document parse(String xml) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            DocumentBuilder b = f.newDocumentBuilder();
            return b.parse(new InputSource(new StringReader(xml)));
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed XML", e);
        }
    }

    private static Document newDocument() {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            return f.newDocumentBuilder().newDocument();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Enveloped XML signature over the whole document, RSA-SHA256, with the signing certificate. */
    private static void sign(Document doc, PrivateKey key, X509Certificate certificate) {
        try {
            XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");
            Reference ref = fac.newReference("", fac.newDigestMethod(DigestMethod.SHA256, null),
                    List.of(fac.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null)), null, null);
            SignedInfo si = fac.newSignedInfo(
                    fac.newCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE, (C14NMethodParameterSpec) null),
                    fac.newSignatureMethod(SignatureMethod.RSA_SHA256, null), List.of(ref));
            KeyInfoFactory kif = fac.getKeyInfoFactory();
            KeyInfo ki = kif.newKeyInfo(List.of(kif.newX509Data(List.of(certificate))));
            fac.newXMLSignature(si, ki).sign(new DOMSignContext(key, doc.getDocumentElement()));
        } catch (Exception e) {
            throw new IllegalStateException("Could not sign the XML", e);
        }
    }

    /**
     * Valid only if made with one of the trusted certificates. The key in
     * the message's own KeyInfo is ignored, so a forged message carrying its
     * own certificate is rejected.
     */
    static boolean verifySignature(Document doc, List<X509Certificate> trusted) {
        NodeList nodes = doc.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature");
        if (nodes.getLength() != 1) {
            return false;
        }
        XMLSignatureFactory fac = XMLSignatureFactory.getInstance("DOM");
        for (X509Certificate cert : trusted) {
            try {
                DOMValidateContext ctx = new DOMValidateContext(cert.getPublicKey(), nodes.item(0));
                ctx.setProperty("org.jcp.xml.dsig.secureValidation", Boolean.TRUE);
                XMLSignature signature = fac.unmarshalXMLSignature(ctx);
                if (signature.validate(ctx)) {
                    return true;
                }
            } catch (Exception ignored) {
                // try the next trusted certificate
            }
        }
        return false;
    }

    private static String serialize(Document doc) {
        try {
            TransformerFactory tf = TransformerFactory.newInstance();
            tf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            Transformer t = tf.newTransformer();
            t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            StringWriter out = new StringWriter();
            t.transform(new DOMSource(doc), new StreamResult(out));
            return out.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String text(Element root, String tag) {
        NodeList list = root.getElementsByTagName(tag);
        return list.getLength() == 0 ? null : list.item(0).getTextContent().trim();
    }

    static String commonName(X509Certificate certificate) {
        String dn = certificate.getSubjectX500Principal().getName();
        for (String part : dn.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)")) {
            String p = part.trim();
            if (p.startsWith("CN=")) {
                return p.substring(3).replace("\\", "");
            }
        }
        return dn;
    }
}
