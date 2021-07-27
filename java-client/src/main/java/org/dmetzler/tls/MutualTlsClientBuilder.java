/*
 * (C) Copyright 2021 Damien Metzler.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.dmetzler.tls;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;

public class MutualTlsClientBuilder {

    private static final char[] PASSWORD = "nopassword".toCharArray();

    private URL clientKeyUrl;

    private URL caUrl;

    private URL clientCertUrl;

    /**
     * Configure the builder with a list of root CAs
     *
     * @param caUrl The URL of a file contain CA certificates. The file can contain multiple chained certs.
     * @return
     */
    public MutualTlsClientBuilder withCA(URL caUrl) {
        if (Objects.nonNull(caUrl)) {
            this.caUrl = caUrl;
        } else {
            throw new IllegalStateException("CA file not found");
        }
        return this;
    }

    /**
     * Configure the builder with a clientKey.
     *
     * @param clientKeyUrl The URL of a file containing a PKCS8 encoded private key.
     * @return
     */
    public MutualTlsClientBuilder withClientKey(URL clientKeyUrl) {
        if (Objects.nonNull(clientKeyUrl)) {
            this.clientKeyUrl = clientKeyUrl;
        } else {
            throw new IllegalStateException("Client key file not found");
        }
        return this;
    }

    /**
     * Configure the builder with a client certificate.
     *
     * @param clientCertUrl The URL of a file containing a chain of certificates.
     * @return
     */
    public MutualTlsClientBuilder withClientCert(URL clientCertUrl) {
        if (Objects.nonNull(clientCertUrl)) {
            this.clientCertUrl = clientCertUrl;
        } else {
            throw new IllegalStateException("Client cert file not found");
        }
        return this;
    }

    /**
     * Builds the OkHttp client with TLS configured
     *
     * @return
     */
    public OkHttpClient build() {

        try {
            var ks = buildKeyStore();

            var kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, PASSWORD);
            var tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);

            var sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());

            var trustManager = getX509TrustManager(tmf);

            return new OkHttpClient.Builder() //
                                             .sslSocketFactory(sslContext.getSocketFactory(), trustManager)
                                             .build();

        } catch (IOException | KeyManagementException | NoSuchAlgorithmException | UnrecoverableKeyException
                | KeyStoreException e) {
            return null;
        }

    }

    private X509TrustManager getX509TrustManager(TrustManagerFactory tmf) {
        TrustManager[] trustManagers = tmf.getTrustManagers();
        if (trustManagers.length != 1 || !(trustManagers[0] instanceof X509TrustManager)) {
            throw new IllegalStateException("Unexpected default trust managers:" + Arrays.toString(trustManagers));
        }
        return (X509TrustManager) trustManagers[0];
    }

    private KeyStore buildKeyStore() throws IOException {
        try {

            // Creates an empty keyStore
            var keyStore = newEmptyKeyStore(PASSWORD);

            // Load the CAs : the server one and the client cert one
            loadCA(keyStore, this.caUrl);

            if (Objects.nonNull(clientCertUrl) && Objects.nonNull(clientKeyUrl)) {
                // Load the client key
                loadKey(keyStore, clientCertUrl, clientKeyUrl);
            }

            return keyStore;

        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Some error happen during keystore build", e);
        }
    }

    /**
     * Loads the CAs contained in a URL into the keystore
     *
     * @param keyStore
     * @param resource
     * @throws CertificateException
     * @throws KeyStoreException
     * @throws IOException
     */
    private void loadCA(KeyStore keyStore, URL resource) throws CertificateException, KeyStoreException, IOException {

        var certs = readCertificatesFromResource(resource);
        for (var i = 0; i < certs.size(); i++) {
            var certificateAlias = Integer.toString(i);
            keyStore.setCertificateEntry(certificateAlias, certs.get(i));
        }

    }

    /**
     * Load a private key into the key store.
     *
     * @param keyStore
     * @param certResource
     * @param keyResource
     * @throws CertificateException
     * @throws KeyStoreException
     * @throws IOException
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeySpecException
     */
    private void loadKey(KeyStore keyStore, URL certResource, URL keyResource) throws CertificateException,
            KeyStoreException, IOException, NoSuchAlgorithmException, InvalidKeySpecException {

        var certs = readCertificatesFromResource(certResource);
        var keySpec = readKeyFromResource(keyResource);
        var kf = KeyFactory.getInstance("RSA");
        var privateKey = kf.generatePrivate(keySpec);

        keyStore.setKeyEntry("client", privateKey, PASSWORD, certs.toArray(new Certificate[] {}));

    }

    /**
     * Reads a list of X509 certificates at a given URL. There must be a X509 certificate present in the file.
     *
     * @param certResource
     * @return
     * @throws IOException
     * @throws CertificateException
     */
    private List<Certificate> readCertificatesFromResource(URL certResource) throws IOException, CertificateException {
        try (var in = certResource.openStream()) {
            var certFactory = CertificateFactory.getInstance("X.509");
            Collection<? extends Certificate> certificates = certFactory.generateCertificates(in);
            if (certificates.isEmpty()) {
                throw new IllegalArgumentException("expected non-empty set of trusted certificates");
            }
            return certificates.stream().map(Certificate.class::cast).collect(Collectors.toList());

        }

    }

    /**
     * Reads a PKCS8 encoded private key from an URL;
     *
     * @param resource
     * @return
     * @throws IOException
     */
    private KeySpec readKeyFromResource(URL resource) throws IOException {
        try (var in = resource.openStream()) {

            var text = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines()
                                                                                            .collect(Collectors.joining(
                                                                                                    "\n"));

            var privateKeyPEM = text.replaceAll("-----BEGIN (.*)-----", "")
                                    .replaceAll(System.lineSeparator(), "")
                                    .replaceAll("-----END (.*)-----", "");

            return new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKeyPEM));

        }
    }

    /**
     * Creates an empty keystore protected by a given password
     *
     * @param password
     * @return
     * @throws GeneralSecurityException
     */
    private KeyStore newEmptyKeyStore(char[] password) throws GeneralSecurityException {
        try {
            var ks = KeyStore.getInstance(KeyStore.getDefaultType());
            InputStream in = null; // By convention, 'null' creates an empty key store.
            ks.load(in, password);
            return ks;
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

}
