/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.openwire;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Vector;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.TransportConnector;
import org.apache.activemq.spring.SpringSslContext;
import org.apache.activemq.util.Wait;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test that connection attempts that don't send the WireFormatInfo performative
 * get cleaned up by the inactivity monitor.
 */
@RunWith(Parameterized.class)
public class OpenWireConnectionTimeoutTest {

    private static final Logger LOG = LoggerFactory.getLogger(OpenWireConnectionTimeoutTest.class);

    @Rule public TestName name = new TestName();

    private Socket connection;
    protected String connectorScheme;
    protected int port;
    protected BrokerService brokerService;
    protected Vector<Throwable> exceptions = new Vector<Throwable>();

    @Parameters(name="{0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                {"tcp"},
                {"ssl"},
                {"nio"},
                {"nio+ssl"}
            });
    }

    public OpenWireConnectionTimeoutTest(String connectorScheme) {
        this.connectorScheme = connectorScheme;
    }

    protected String getConnectorScheme() {
        return connectorScheme;
    }

    public String getTestName() {
        return name.getMethodName();
    }

    @Before
    public void setUp() throws Exception {
        LOG.info("========== start " + getTestName() + " ==========");

        startBroker();
    }

    @After
    public void tearDown() throws Exception {
        if (connection != null) {
            try {
                connection.close();
            } catch (Throwable e) {}
            connection = null;
        }

        stopBroker();

        LOG.info("========== start " + getTestName() + " ==========");
    }

    public String getAdditionalConfig() {
        return "?transport.connectAttemptTimeout=1200";
    }

    @Test(timeout = 90000)
    public void testInactivityMonitor() throws Exception {

        Thread t1 = new Thread() {

            @Override
            public void run() {
                try {
                    connection = createConnection();
                    connection.getOutputStream().write('A');
                    connection.getOutputStream().flush();
                } catch (Exception ex) {
                    LOG.error("unexpected exception on connect/disconnect", ex);
                    exceptions.add(ex);
                }
            }
        };

        t1.start();

        assertTrue("one connection", Wait.waitFor(new Wait.Condition() {
             @Override
             public boolean isSatisified() throws Exception {
                 return 1 == brokerService.getTransportConnectorByScheme(getConnectorScheme()).connectionCount();
             }
        }, TimeUnit.SECONDS.toMillis(15), TimeUnit.MILLISECONDS.toMillis(250)));

        // and it should be closed due to inactivity
        assertTrue("no dangling connections", Wait.waitFor(new Wait.Condition() {
            @Override
            public boolean isSatisified() throws Exception {
                return 0 == brokerService.getTransportConnectorByScheme(getConnectorScheme()).connectionCount();
            }
        }, TimeUnit.SECONDS.toMillis(15), TimeUnit.MILLISECONDS.toMillis(500)));

        assertTrue("no exceptions", exceptions.isEmpty());
    }

    protected Socket createConnection() throws IOException {
        boolean useSsl = false;

        switch (connectorScheme) {
            case "tcp":
            case "nio":
                break;
            case "ssl":
            case "nio+ssl":
                useSsl = true;;
                break;
            default:
                throw new IOException("Invalid OpenWire connector scheme passed to test.");
        }

        if (useSsl) {
            return SSLSocketFactory.getDefault().createSocket("localhost", port);
        } else {
            return new Socket("localhost", port);
        }
    }

    protected void startBroker() throws Exception {
        brokerService = new BrokerService();
        brokerService.setPersistent(false);
        brokerService.setSchedulerSupport(false);
        brokerService.setAdvisorySupport(false);
        brokerService.setUseJmx(false);
        brokerService.getManagementContext().setCreateConnector(false);

        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(new KeyManager[0], new TrustManager[]{new DefaultTrustManager()}, new SecureRandom());
        SSLContext.setDefault(ctx);

        // Setup SSL context...
        final File classesDir = new File(OpenWireConnectionTimeoutTest.class.getProtectionDomain().getCodeSource().getLocation().getFile());
        File keystore = new File(classesDir, "../../src/test/resources/server.keystore");
        final SpringSslContext sslContext = new SpringSslContext();
        sslContext.setKeyStore(keystore.getCanonicalPath());
        sslContext.setKeyStorePassword("password");
        sslContext.setTrustStore(keystore.getCanonicalPath());
        sslContext.setTrustStorePassword("password");
        sslContext.afterPropertiesSet();
        brokerService.setSslContext(sslContext);

        System.setProperty("javax.net.ssl.trustStore", keystore.getCanonicalPath());
        System.setProperty("javax.net.ssl.trustStorePassword", "password");
        System.setProperty("javax.net.ssl.trustStoreType", "jks");
        System.setProperty("javax.net.ssl.keyStore", keystore.getCanonicalPath());
        System.setProperty("javax.net.ssl.keyStorePassword", "password");
        System.setProperty("javax.net.ssl.keyStoreType", "jks");

        TransportConnector connector = null;

        switch (connectorScheme) {
            case "tcp":
                connector = brokerService.addConnector("tcp://0.0.0.0:0" + getAdditionalConfig());
                break;
            case "nio":
                connector = brokerService.addConnector("nio://0.0.0.0:0" + getAdditionalConfig());
                break;
            case "ssl":
                connector = brokerService.addConnector("ssl://0.0.0.0:0" + getAdditionalConfig());
                break;
            case "nio+ssl":
                connector = brokerService.addConnector("nio+ssl://0.0.0.0:0" + getAdditionalConfig());
                break;
            default:
                throw new IOException("Invalid OpenWire connector scheme passed to test.");
        }

        brokerService.start();
        brokerService.waitUntilStarted();

        port = connector.getPublishableConnectURI().getPort();
    }

    public void stopBroker() throws Exception {
        if (brokerService != null) {
            brokerService.stop();
            brokerService.waitUntilStopped();
            brokerService = null;
        }
    }

    public class DefaultTrustManager implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
