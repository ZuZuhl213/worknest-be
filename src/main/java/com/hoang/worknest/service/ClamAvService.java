package com.hoang.worknest.service;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.hoang.worknest.exception.ServiceUnavailableException;

@Service
public class ClamAvService {
    @Value("${security.clamav.enabled:false}")
    private boolean enabled;

    @Value("${security.clamav.host:localhost}")
    private String host;

    @Value("${security.clamav.port:3310}")
    private int port;

    @Value("${security.clamav.timeout-ms:5000}")
    private int timeoutMillis;

    public void scan(byte[] content) {
        if (!enabled) return;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMillis);
            socket.setSoTimeout(timeoutMillis);
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            output.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
            for (int offset = 0; offset < content.length; offset += 8192) {
                int length = Math.min(8192, content.length - offset);
                output.writeInt(length);
                output.write(content, offset, length);
            }
            output.writeInt(0);
            output.flush();
            ByteArrayOutputStream responseBytes = new ByteArrayOutputStream();
            int next;
            while (responseBytes.size() < 4096 && (next = socket.getInputStream().read()) != -1 && next != 0) {
                responseBytes.write(next);
            }
            String response = responseBytes.toString(StandardCharsets.US_ASCII);
            if (response == null || response.contains("ERROR")) {
                throw new ServiceUnavailableException("Malware scanner is unavailable");
            }
            if (response.contains("FOUND")) {
                throw new IllegalArgumentException("Upload rejected by malware scanner");
            }
            if (!response.contains("OK")) {
                throw new ServiceUnavailableException("Malware scanner returned an invalid response");
            }
        } catch (ServiceUnavailableException | IllegalArgumentException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new ServiceUnavailableException("Malware scanner is unavailable", ex);
        }
    }
}
