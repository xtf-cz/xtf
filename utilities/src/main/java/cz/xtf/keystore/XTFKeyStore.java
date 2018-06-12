package cz.xtf.keystore;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.slf4j.LoggerFactory;

import cz.xtf.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.InvalidParameterException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStore.Entry;
import java.security.KeyStore.PasswordProtection;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.KeyStore.ProtectionParameter;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Collection;
import java.util.Enumeration;
import java.util.LinkedList;

import javax.crypto.KeyGenerator;

public class XTFKeyStore {
	public static final String SIGNER_CERTIFICATE = "xtf.ca";
	public static final String SIGNER_PASSWORD = "password";

	private static XTFKeyStore instance;
	private final KeyStore keystore;

	private XTFKeyStore(KeyStore keystore) {
		this.keystore = keystore;
	}

	public static XTFKeyStore newInstance() {
		try {
			return newInstance(null, null);
		} catch (IOException ex) {
			// should never be thrown
			throw new RuntimeException("Unable to instantiate new KeyStore", ex);
		}
	}

	public static XTFKeyStore newInstance(InputStream source, String password) throws IOException {
		return newInstance(source, password, "JKS");
	}

	public static XTFKeyStore newInstance(
			InputStream source, String password, String keystoreType) throws IOException {
		try {
			KeyStore ks = KeyStore.getInstance(keystoreType == null ? "JKS" : keystoreType);
			ks.load(source, password == null ? null : password.toCharArray());

			return new XTFKeyStore(ks);
		} catch (GeneralSecurityException ex) {
			throw new RuntimeException("Couldn't load keystore", ex);
		}
	}

	public static XTFKeyStore getInstance() {
		if (instance == null) {
			Path defaultKeystore = findDefaultKeyStore();
			try (InputStream is = Files.newInputStream(defaultKeystore)) {
				instance = newInstance(is, "");
			} catch (IOException ex) {
				throw new RuntimeException("Couldn't load default keystore: " + defaultKeystore, ex);
			}
		}

		return instance;
	}

	private static Path findDefaultKeyStore() {
		return IOUtils.findProjectRoot().resolve("keystore");
	}

	public Collection<String> getAliases() {
		try {
			Collection<String> result = new LinkedList<>();

			for (Enumeration<String> e = keystore.aliases(); e.hasMoreElements(); ) {
				result.add(e.nextElement());
			}

			return result;
		} catch (KeyStoreException e) {
			// this should never happen
			throw new IllegalStateException("Key store is not initialized", e);
		}
	}

	public String getCertificate(String alias) {
		try {
			Certificate certificate = keystore.getCertificate(alias);
			return convertObject(certificate);
		} catch (KeyStoreException e) {
			// this should never happen
			throw new IllegalStateException("Key store is not initialized", e);
		} catch (IOException e) {
			throw new IllegalStateException("Could not convert certificate " + alias, e);
		}
	}

	public String getKey(String alias) {
		return getKey(alias, "password");
	}

	public String getKey(String alias, String password) {
		try {
			Key key = keystore.getKey(alias, password.toCharArray());
			return convertObject(key);
		} catch (KeyStoreException | NoSuchAlgorithmException e) {
			// this should never happen
			throw new IllegalStateException("Key store is not initialized", e);
		} catch (IOException e) {
			throw new IllegalStateException("Could not convert key " + alias, e);
		} catch (UnrecoverableKeyException e) {
			throw new IllegalArgumentException("Wrong password for key " + alias, e);
		}
	}

	public void addCertificateFromBase64String(final String certificate) {
		try {
			keystore.setCertificateEntry("maven", CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(certificate.getBytes())));
		} catch (KeyStoreException | CertificateException e) {
			throw new IllegalStateException(e);
		}
	}

	public void addSelfSignedCertificate(String certificateAlias) {
		addSelfSignedCertificate(certificateAlias, "CN=xtf,OU=QE,O=xtf.cz,L=Brno,C=CZ", "password");
	}

	public void addSelfSignedCertificate(String certificateAlias, String dn, String password) {
		try {
			KeyPair keys = generateKeyPair();

			Calendar start = Calendar.getInstance();
			Calendar expiry = Calendar.getInstance();
			expiry.add(Calendar.YEAR, 1);
			X500Name name = new X500Name(dn);
			X509v3CertificateBuilder certificateBuilder = new X509v3CertificateBuilder(name, BigInteger.ONE,
					start.getTime(), expiry.getTime(), name, SubjectPublicKeyInfo.getInstance(keys.getPublic().getEncoded()));
			ContentSigner signer = new JcaContentSignerBuilder("SHA1WithRSA").setProvider(new BouncyCastleProvider()).build(keys.getPrivate());
			X509CertificateHolder holder = certificateBuilder.build(signer);
			Certificate cert = new JcaX509CertificateConverter().setProvider(new BouncyCastleProvider()).getCertificate(holder);

			Entry entry = new PrivateKeyEntry(keys.getPrivate(), new Certificate[]{ cert });
			keystore.setEntry(certificateAlias, entry, new PasswordProtection(password.toCharArray()));
		} catch (GeneralSecurityException | OperatorCreationException ex) {
			throw new RuntimeException("Unable to generate self-signed certificate", ex);
		}
	}

	public void addSignedCertificate(final XTFKeyStore signerKeyStore, final String signerAlias, final String signerPassword, final String dn, final String certificateAlias, final String password) {
		try {
			final X509Certificate caCert = (X509Certificate) signerKeyStore.keystore.getCertificate(signerAlias);
			final PrivateKey caKey = (PrivateKey) signerKeyStore.keystore.getKey(signerAlias, signerPassword.toCharArray());
			final Calendar start = Calendar.getInstance();
			final Calendar expiry = Calendar.getInstance();
			expiry.add(Calendar.YEAR, 1);
			final KeyPair keyPair = generateKeyPair();
			final X500Name certName = new X500Name(dn);
			final X500Name issuerName = new X500Name(caCert.getSubjectDN().getName());
			X509v3CertificateBuilder certificateBuilder = new X509v3CertificateBuilder(
					issuerName,
					BigInteger.valueOf(System.nanoTime()),
					start.getTime(),
					expiry.getTime(),
					certName,
					SubjectPublicKeyInfo.getInstance(keyPair.getPublic().getEncoded()));
			final JcaX509ExtensionUtils u = new JcaX509ExtensionUtils();
			certificateBuilder.addExtension(Extension.authorityKeyIdentifier, false,
					u.createAuthorityKeyIdentifier(caCert));
			certificateBuilder.addExtension(Extension.subjectKeyIdentifier, false,
					u.createSubjectKeyIdentifier(keyPair.getPublic()));
			ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA").setProvider(new BouncyCastleProvider()).build(caKey);
			X509CertificateHolder holder = certificateBuilder.build(signer);
			Certificate cert = new JcaX509CertificateConverter().setProvider(new BouncyCastleProvider()).getCertificate(holder);

			Entry entry = new PrivateKeyEntry(keyPair.getPrivate(), new Certificate[] {cert, caCert});
			keystore.setEntry(certificateAlias, entry, new PasswordProtection(password.toCharArray()));
		} catch (GeneralSecurityException | OperatorCreationException | CertIOException ex) {
			throw new RuntimeException("Unable to generate signed certificate", ex);
		}
	}

	public void addPrivateKey(String keyAlias, String certificateAlias) {
		addPrivateKey(keyAlias, certificateAlias, "password");
	}

	/**
	 * Asymmetric cryptography - only the private key from generated pair is used.
	 * Pre-condition: #certificateAlias refers to existing certificate
	 *
	 * @throws {@link NullPointerException} when #certificateAlias is @code{null}
	 */
	public void addPrivateKey(String keyAlias, String certificateAlias, String password) {
		keyAlias = String.format("%s (%s)", keyAlias, certificateAlias);

		try {
			Certificate[] certChain = keystore.getCertificateChain(certificateAlias);
			if (certChain == null) {
				LoggerFactory.getLogger(getClass()).warn("Could not find certificate");
				certChain = new Certificate[0];
			}
			Entry entry = new PrivateKeyEntry(generateKeyPair().getPrivate(), certChain);
			ProtectionParameter protParam = new KeyStore.PasswordProtection(password.toCharArray());
			keystore.setEntry(keyAlias, entry, protParam);
		} catch (KeyStoreException | NoSuchAlgorithmException ex) {
			throw new RuntimeException("Unable to add new private key", ex);
		}
	}

	/**
	 * Symmetric cryptography - add (private) key to keystore.
	 * Can be used in both ways, with and without a certificate.
	 */
	public void addSecretKey(String keyAlias, String certificateAlias, String password) {
		try {
			// certChain can be null, in that case keystore will contain only the key
			Certificate[] certChain = null;
			try { certChain = keystore.getCertificateChain(certificateAlias); } catch (NullPointerException e) {}

			Key key = generateKey("DES", 56);
			keystore.setKeyEntry(keyAlias, key, password.toCharArray(), certChain);
		} catch (KeyStoreException | NoSuchAlgorithmException ex) {
			throw new RuntimeException("Unable to add new secret key", ex);
		}
	}

	public Path export() {
		Path outputPath = findDefaultKeyStore();
		try (OutputStream output = Files.newOutputStream(outputPath)) {
			export(output, "");

			return outputPath;
		} catch (IOException ex) {
			throw new RuntimeException("Unable to export keystore to default location.");
		}
	}

	public void export(OutputStream output, String password) throws IOException {
		try {
			keystore.store(output, password.toCharArray());
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("Unable to export keystore", e);
		}
	}

	private String convertObject(Object obj) throws IOException {
		try (StringWriter sw = new StringWriter(); JcaPEMWriter pemWriter = new JcaPEMWriter(sw)) {
			pemWriter.writeObject(obj);
			pemWriter.close();
			return sw.toString();
		}
	}

	/**
	 * Symmetric cryptography - one (private) key is generated.
	 *
	 * @param algorithm following algorithms and corresponding key size values are available:
	 *                    DES 56
	 *                    AES 128, 192, 256
	 * @param keysize length of seed
	 * @return generated key
	 * @throws NoSuchAlgorithmException if given algorithm is not supported
	 * @throws InvalidParameterException if given keysize is not supported
	 */
	private Key generateKey(String algorithm, int keysize) throws NoSuchAlgorithmException, InvalidParameterException {
		KeyGenerator keyGen = KeyGenerator.getInstance(algorithm);
		keyGen.init(keysize);
		return keyGen.generateKey();
	}

	/**
	 * Asymmetric cryptography - private and public keys are generated
	 *
	 * @return generated key pair
	 */
	private KeyPair generateKeyPair() throws NoSuchAlgorithmException {
		KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
		keyGen.initialize(4096, new SecureRandom());

		return keyGen.generateKeyPair();
	}
}
