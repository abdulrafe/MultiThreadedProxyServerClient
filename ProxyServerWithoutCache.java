import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ProxyServerWithoutCache {
    private static final int MAX_BYTES = 4096;
    private static final int MAX_CLIENTS = 400;
    private static final Semaphore semaphore = new Semaphore(MAX_CLIENTS);
    private static int portNumber = 8080;

    public static void main(String[] args) {
        if (args.length == 1) {
            portNumber = Integer.parseInt(args[0]);
        } else {
            System.out.println("Usage: java ProxyServerWithoutCache <port>");
            System.exit(1);
        }

        System.out.println("Proxy Server running on port: " + portNumber);

        try (ServerSocket serverSocket = new ServerSocket(portNumber)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static class ClientHandler implements Runnable {
        private final Socket clientSocket;

        ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try {
                semaphore.acquire();
                handleRequest(clientSocket);
            } catch (InterruptedException | IOException e) {
                e.printStackTrace();
            } finally {
                semaphore.release();
            }
        }
    }

    private static void handleRequest(Socket clientSocket) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        OutputStream clientOutput = clientSocket.getOutputStream();

        String requestLine = reader.readLine();
        if (requestLine == null || requestLine.isEmpty()) {
            System.out.println("[INFO] Empty request received, closing connection.");
            clientSocket.close();
            return;
        }

        System.out.println("[INFO] Received: " + requestLine);

        if (requestLine.startsWith("CONNECT")) {
            handleHttpsTunnel(clientSocket, requestLine);
            return;
        }

        forwardHttpRequest(clientSocket, reader, requestLine);
        clientSocket.close();
    }

    private static void handleHttpsTunnel(Socket clientSocket, String requestLine) throws IOException {
        String[] parts = requestLine.split(" ");
        String hostPort = parts[1];
        String[] hp = hostPort.split(":");
        String host = hp[0];
        int port = (hp.length > 1) ? Integer.parseInt(hp[1]) : 443;

        System.out.println("[HTTPS] Tunnel requested to host: " + host + ":" + port);

        try (Socket remoteSocket = new Socket(host, port)) {
            OutputStream clientOutput = clientSocket.getOutputStream();
            clientOutput.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes());
            clientOutput.flush();

            Thread clientToRemote = new Thread(() -> streamRelay(clientSocket, remoteSocket));
            Thread remoteToClient = new Thread(() -> streamRelay(remoteSocket, clientSocket));

            clientToRemote.start();
            remoteToClient.start();

            clientToRemote.join();
            remoteToClient.join();

        } catch (Exception e) {
            System.err.println("[ERROR] HTTPS Tunnel failed: " + e.getMessage());
        }
    }

    private static void streamRelay(Socket inputSocket, Socket outputSocket) {
        try {
            InputStream in = inputSocket.getInputStream();
            OutputStream out = outputSocket.getOutputStream();
            byte[] buffer = new byte[MAX_BYTES];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
                out.flush();
            }
        } catch (IOException e) {
            // Connection closed.
        }
    }

    private static void forwardHttpRequest(Socket clientSocket, BufferedReader reader, String requestLine) throws IOException {
        StringTokenizer tokenizer = new StringTokenizer(requestLine);
        String method = tokenizer.nextToken();
        String fullUrl = tokenizer.nextToken();
        String version = tokenizer.nextToken();

        URL url = new URL(fullUrl);
        String host = url.getHost();
        int port = (url.getPort() != -1) ? url.getPort() : 80;

        System.out.println("[FORWARD] Connecting to host: " + host + ":" + port);

        try (Socket remoteSocket = new Socket(host, port);
             OutputStream remoteOutput = remoteSocket.getOutputStream();
             InputStream remoteInput = remoteSocket.getInputStream()) {

            String path = url.getFile();
            if (path == null || path.isEmpty()) {
                path = "/";
            }

            String formattedRequest = method + " " + path + " " + version + "\r\n";

            // Forward headers
            String headerLine;
            while (!(headerLine = reader.readLine()).isEmpty()) {
                formattedRequest += headerLine + "\r\n";
            }
            formattedRequest += "\r\n";

            remoteOutput.write(formattedRequest.getBytes());
            remoteOutput.flush();

            byte[] buffer = new byte[MAX_BYTES];
            int bytesRead;
            while ((bytesRead = remoteInput.read(buffer)) != -1) {
                clientSocket.getOutputStream().write(buffer, 0, bytesRead);
            }

            System.out.println("[FORWARD] Response relayed for URL: " + fullUrl);
        }
    }
}