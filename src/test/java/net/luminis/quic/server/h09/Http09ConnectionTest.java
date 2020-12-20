package net.luminis.quic.server.h09;

import net.luminis.quic.QuicConnection;
import net.luminis.quic.stream.QuicStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Http09ConnectionTest {

    private Http09Connection httpConnection;

    @BeforeEach
    void initObjectUnderTest() {
        httpConnection = new Http09Connection(mock(QuicConnection.class), new File("."));
    }

    @Test
    void extractFileNameFromHttp09Request() throws IOException {
        InputStream input= new ByteArrayInputStream("GET index.html".getBytes());
        String fileName = httpConnection.extractPathFromRequest(input);
        assertThat(fileName).isEqualTo("index.html");
    }

    @Test
    void whenExtractingFileNameFromHttp09RequestInitialSlashIsDiscarded() throws IOException {
        InputStream input= new ByteArrayInputStream("GET /index.html".getBytes());
        String fileName = httpConnection.extractPathFromRequest(input);
        assertThat(fileName).isEqualTo("index.html");
    }

    @Test
    void whenRequestingExistingFileContentIsReturned() throws Exception {
        Path wwwDir = Files.createTempDirectory("kwikh09");
        Files.write(Paths.get(wwwDir.toString(), "test.txt"), "This is a test (obviously)\n".getBytes());

        Http09Connection http09Connection = new Http09Connection(mock(QuicConnection.class), wwwDir.toFile());

        QuicStream quicStream = mock(QuicStream.class);
        when(quicStream.getInputStream()).thenReturn(new ByteArrayInputStream("GET test.txt".getBytes()));
        ByteArrayOutputStream arrayOutputStream = new ByteArrayOutputStream(1024);
        when(quicStream.getOutputStream()).thenReturn(arrayOutputStream);
        http09Connection.handleRequest(quicStream);

        assertThat(arrayOutputStream.toString()).startsWith("This is a test");
    }

    @Test
    void whenRequestingNonExistingFile404Returned() throws Exception {
        Path wwwDir = Files.createTempDirectory("kwikh09");

        Http09Connection http09Connection = new Http09Connection(mock(QuicConnection.class), wwwDir.toFile());

        QuicStream quicStream = mock(QuicStream.class);
        when(quicStream.getInputStream()).thenReturn(new ByteArrayInputStream("GET doesnotexist.txt".getBytes()));
        ByteArrayOutputStream arrayOutputStream = new ByteArrayOutputStream(1024);
        when(quicStream.getOutputStream()).thenReturn(arrayOutputStream);
        http09Connection.handleRequest(quicStream);

        assertThat(arrayOutputStream.toString()).startsWith("404");
    }

    @Test
    void pathTraversalShouldBePrevented() throws Exception {
        Path rootDir = Files.createTempDirectory("kwikh09");
        Files.write(Paths.get(rootDir.toString(), "secrets"), "This is a secret\n".getBytes());

        File wwwDir = new File(rootDir.toFile(), "www");
        wwwDir.mkdirs();
        Files.write(Paths.get(wwwDir.toString(), "test.txt"), "This is a test (obviously)\n".getBytes());

        Http09Connection http09Connection = new Http09Connection(mock(QuicConnection.class), wwwDir);

        QuicStream quicStream = mock(QuicStream.class);
        when(quicStream.getInputStream()).thenReturn(new ByteArrayInputStream("GET ../secrets".getBytes()));
        ByteArrayOutputStream arrayOutputStream = new ByteArrayOutputStream(1024);
        when(quicStream.getOutputStream()).thenReturn(arrayOutputStream);
        http09Connection.handleRequest(quicStream);

        assertThat(arrayOutputStream.toString()).startsWith("404");
    }

}