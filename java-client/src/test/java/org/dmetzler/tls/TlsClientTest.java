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

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;

import org.junit.Test;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class TlsClientTest {

    private static final String URL = "http://nginx.local/index.html";

    @Test
    public void query_endpoint_without_cert_results_in_403() throws Exception {
        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder().url(URL).build();

        try (Response response = client.newCall(request).execute()) {
            assertThat(response.code()).isEqualTo(403);
        }

    }

    @Test
    public void mutual_tls_builder() throws Exception {
        var client = new MutualTlsClientBuilder()//
                                                 .withCA(getUrl("/ca.crt"))//
                                                 .withClientKey(getUrl("/client.pk8"))//
                                                 .withClientCert(getUrl("/client.crt"))//
                                                 .build();

        Request request = new Request.Builder().url(URL).build();

        try (Response response = client.newCall(request).execute()) {
            assertThat(response.code()).isEqualTo(200);
        }
    }

    @Test
    public void mutual_tls_build_with_jks() throws Exception {
        var client = new MutualTlsClientBuilder()//
                                                 .withJKS(getUrl("/client.jks"))//
                                                 .withPassword("changeit".toCharArray())
                                                 .build();

        Request request = new Request.Builder().url(URL).build();

        try (Response response = client.newCall(request).execute()) {
            assertThat(response.code()).isEqualTo(200);
        }
    }

    private URL getUrl(String resourcePath) {
        return getClass().getResource(resourcePath);
    }

}
