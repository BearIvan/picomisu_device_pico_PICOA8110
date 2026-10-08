// Picomisu Source Update: client of source_updaterd (/dev/socket/source_updater, system uid only).
package com.picovr.updatesystem;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

final class UpdaterClient {
    private static final String SOCKET = "source_updater";

    interface Progress {
        void onProgress(int done, int total);
    }

    private UpdaterClient() {}

    private static LocalSocket connect() throws IOException {
        LocalSocket socket = new LocalSocket();
        socket.connect(new LocalSocketAddress(SOCKET, LocalSocketAddress.Namespace.RESERVED));
        socket.setSoTimeout(10 * 60 * 1000);
        return socket;
    }

    private static String simple(String command) throws IOException {
        try (LocalSocket socket = connect()) {
            OutputStream out = socket.getOutputStream();
            out.write((command + "\n").getBytes(StandardCharsets.UTF_8));
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String line = in.readLine();
            if (line == null) throw new IOException("source_updaterd closed the connection");
            if (line.startsWith("ERROR ")) throw new IOException(line.substring(6));
            return line;
        }
    }

    static JSONObject status() throws Exception {
        String line = simple("STATUS");
        return new JSONObject(line.startsWith("OK ") ? line.substring(3) : "{}");
    }

    static void reboot() throws IOException {
        simple("REBOOT");
    }

    static void clear() throws IOException {
        simple("CLEAR");
    }

    /** Sends the package fd with the STAGE command and waits for OK. */
    static void stage(File file, Progress progress) throws IOException {
        try (LocalSocket socket = connect(); FileInputStream package_ = new FileInputStream(file)) {
            socket.setFileDescriptorsForSend(new FileDescriptor[] {package_.getFD()});
            OutputStream out = socket.getOutputStream();
            out.write("STAGE\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("PROGRESS ")) {
                    String[] parts = line.split(" ");
                    progress.onProgress(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                } else if (line.equals("OK")) {
                    return;
                } else if (line.startsWith("ERROR ")) {
                    throw new IOException(line.substring(6));
                }
            }
            throw new IOException("source_updaterd closed the connection");
        }
    }
}
