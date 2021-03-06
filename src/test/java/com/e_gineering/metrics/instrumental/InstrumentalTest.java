/**
 * Copyright 2015 E-Gineering, LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.e_gineering.metrics.instrumental;

import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import javax.net.SocketFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ProtocolException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.failBecauseExceptionWasNotThrown;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static com.e_gineering.metrics.instrumental.MetricType.*;

public class InstrumentalTest {
    private final String host = "example.com";
    private final int port = 1234;
    private final String apiKey = "Th3Ap1K3y";
    private final SocketFactory socketFactory = mock(SocketFactory.class);
    private final InetSocketAddress address = new InetSocketAddress(host, port);

    private static final Charset ASCII = Charset.forName("ASCII");

    private final Socket socket = mock(Socket.class);
    private final ByteArrayOutputStream output = spy(new ByteArrayOutputStream());
    private final ByteArrayOutputStream input = spy(new ByteArrayOutputStream());

    private Instrumental instrumental;

    @Before
    public void setUp() throws Exception {
        when(socket.getOutputStream()).thenReturn(output);

        // Return a new InputStream when we ask the socket for one.
        doAnswer(new Answer<InputStream>() {
            @Override
            public InputStream answer(InvocationOnMock invocation) throws Throwable {
                return new ByteArrayInputStream(input.toByteArray());
            }
        }).when(socket).getInputStream();

        // Mock behavior of socket.getOutputStream().close() calling socket.close();
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                invocation.callRealMethod();
                socket.close();
                return null;
            }
        }).when(output).close();

        doAnswer(new Answer<InetAddress>() {
            @Override
            public InetAddress answer(InvocationOnMock invocation) throws Throwable {
                return InetAddress.getLocalHost();
            }
        }).when(socket).getLocalAddress();

        when(socketFactory.createSocket()).thenReturn(socket);
    }

    private void addResponse(String s) {
        try {
            input.write((s + "\n").getBytes(ASCII));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Test
    public void connectsToInstrumentalWithInetSocketAddress() throws Exception {
        instrumental = new Instrumental(apiKey, address, socketFactory);
        addResponse("ok");
        addResponse("ok");
        instrumental.connect();

        verify(socketFactory).createSocket();
        verify(socket).setTcpNoDelay(true);
        verify(socket).setKeepAlive(true);
        verify(socket).setTrafficClass(0x04 | 0x10);
        verify(socket).setPerformancePreferences(0, 2, 1);
        verify(socket).setSoTimeout(5000);
        verify(socket).connect(address);
    }

    @Test
    public void connectsToInstrumentalWithHostAndPort() throws Exception {
        instrumental = new Instrumental(apiKey, host, port, socketFactory);
        addResponse("ok");
        addResponse("ok");
        instrumental.connect();

        verify(socketFactory).createSocket();
        verify(socket).setTcpNoDelay(true);
        verify(socket).setKeepAlive(true);
        verify(socket).setTrafficClass(0x04 | 0x10);
        verify(socket).setPerformancePreferences(0, 2, 1);
        verify(socket).setSoTimeout(5000);
        verify(socket).connect(address);
    }

    @Test
    public void measuresFailures() throws Exception {
        instrumental = new Instrumental(apiKey, address, socketFactory);
        assertThat(instrumental.getFailures())
                .isZero();
    }

    @Test
    public void disconnectsFromInstrumental() throws Exception {
        instrumental = new Instrumental(apiKey, address, socketFactory);
        addResponse("ok");
        addResponse("ok");
        instrumental.connect();
        instrumental.close();

        verify(socket).close();
    }

    @Test
    public void doesNotAllowDoubleConnections() throws Exception {
        instrumental = new Instrumental(apiKey, address, socketFactory);
        addResponse("ok");
        addResponse("ok");
        instrumental.connect();
        try {
            instrumental.connect();
            failBecauseExceptionWasNotThrown(IllegalStateException.class);
        } catch (IllegalStateException e) {
            assertThat(e.getMessage())
                    .isEqualTo("Already connected");
        }
    }

    @Test
    public void handshakeWorksIfAllOk() throws Exception {
        instrumental = new Instrumental(apiKey, address, socketFactory);
        addResponse("ok");
        addResponse("ok");
        instrumental.connect();

        assertThat(instrumental.isConnected());
        instrumental.close();

        assertThat(!instrumental.isConnected());
        assertThat(output.toString()).matches("hello version .* hostname .* pid .* runtime .* platform .*\\n.*\\n");
        assertThat(output.toString()).contains("authenticate " + apiKey);
    }


    @Test
    public void handshakeFailsIfHelloBad() throws Exception {
        instrumental = new Instrumental(apiKey, address, socketFactory);

        try {
            instrumental.connect();
            Assertions.failBecauseExceptionWasNotThrown(ProtocolException.class);
        } catch (ProtocolException pe) {
            assertThat(pe.getMessage().equals("hello failed"));
        }

        assertThat(!instrumental.isConnected());
        assertThat(output.toString()).matches("hello version .* hostname .* pid .* runtime .* platform .*\\n");
    }

    @Test
    public void handshakeFailsIfAuthenticateBad() throws Exception {
        instrumental = new Instrumental(apiKey, address, socketFactory);
        addResponse("ok");

        try {
            instrumental.connect();
            Assertions.failBecauseExceptionWasNotThrown(ProtocolException.class);
        } catch (ProtocolException pe) {
            assertThat(pe.getMessage().equals("authenticate failed"));
        }

        assertThat(!instrumental.isConnected());
        assertThat(output.toString()).matches("hello version .* hostname .* pid .* runtime .* platform .*\\n.*\\n");
        assertThat(output.toString()).contains("authenticate " + apiKey);
        instrumental.close();
    }



    @Test
    public void writesValuesToInstrumental() throws Exception {
        instrumental = new Instrumental(apiKey, address, socketFactory);
        addResponse("ok");
        addResponse("ok");
        instrumental.connect();
        output.reset();
        instrumental.send(GAUGE, "name", "value", 100);
        instrumental.close();

        assertThat(output.toString())
                .isEqualTo("gauge name value 100\n");
    }

    @Test
    public void sanitizesNames() throws Exception {
        instrumental = new Instrumental(apiKey, address, socketFactory);
        addResponse("ok");
        addResponse("ok");
        instrumental.connect();
        output.reset();
        instrumental.send(GAUGE, "name woo/foo$bar.invoked(param1, param2)", "value", 100);
        instrumental.close();

        assertThat(output.toString())
                .isEqualTo("gauge name.woo.foo.bar.invoked__param1-param2__ value 100\n");
    }


    @Test
    public void sanitizesValues() throws Exception {
        instrumental = new Instrumental(apiKey, address, socketFactory);
        addResponse("ok");
        addResponse("ok");
        instrumental.connect();
        output.reset();
        instrumental.send(GAUGE, "name", "value woo", 100);
        instrumental.close();

        assertThat(output.toString())
                .isEqualTo("gauge name value.woo 100\n");
    }

    @Test
    public void simpleNoticeTest() throws Exception {
        instrumental = new Instrumental(apiKey, address, socketFactory);
        addResponse("ok");
        addResponse("ok");
        instrumental.connect();
        output.reset();
        instrumental.notice("simpleNotice");
        instrumental.close();

        assertThat(output.toString().matches("notice [0-9]* 0 simpleNotice"));
    }

    @Test
    public void durationNoticeTest() throws Exception {
        instrumental = new Instrumental(apiKey, address, socketFactory);
        addResponse("ok");
        addResponse("ok");
        instrumental.connect();
        output.reset();
        instrumental.notice("durationNotice", 5, TimeUnit.SECONDS);
        instrumental.close();

        assertThat(output.toString().matches("notice [0-9]* 5 durationNotice"));

        instrumental = new Instrumental(apiKey, address, socketFactory);
        addResponse("ok");
        addResponse("ok");
        instrumental.connect();
        output.reset();
        instrumental.notice("durationNotice", 2500, TimeUnit.MILLISECONDS);
        instrumental.close();

        assertThat(output.toString().matches("notice [0-9]* 2 durationNotice"));

    }


    @Test
    public void notifiesIfInstrumentalIsUnavailable() throws Exception {
        final String unavailableHost = "unknown-host-10el6m7yg56ge7dm.com";
        InetSocketAddress unavailableAddress = new InetSocketAddress(unavailableHost, 1234);
        Instrumental unavailableInstrumental = new Instrumental(apiKey, unavailableAddress, socketFactory);

        try {
            unavailableInstrumental.connect();
            failBecauseExceptionWasNotThrown(UnknownHostException.class);
        } catch (Exception e) {
            assertThat(e.getMessage())
                .isEqualTo(unavailableHost);
        }
    }
}
