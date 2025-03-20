import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

class CacheElement {
    String data;
    int len;
    String url;
    long lruTimeTrack;
    CacheElement next;
}

public class ProxyServerWithCache {
    private static final int MAX_BYTES = 4096;
    private static final int MAX_CLIENTS = 400;
    private static final int MAX_SIZE = 200 * (1 << 20);
    private static final int MAX_ELEMENT_SIZE = 10 * (1 << 20);

    private static CacheElement head;
    private static int cacheSize = 0;
    private static final Semaphore semaphore = new Semaphore(MAX_CLIENTS);
    private static final Object cacheLock = new Object();
    private static int portNumber = 8080;

    public static void main(String[] args) {
        if (args.length == 1) {
            portNumber = Integer.parseInt(args[0]);
        } else {
            System.out.println("Usage: java ProxyServerWithCache <port>");
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
            handleHttpsTunnel(clientSocket, reader, requestLine);
            return;
        }

        StringTokenizer tokenizer = new StringTokenizer(requestLine);
        String method = tokenizer.nextToken();
        String url = tokenizer.nextToken();
        String version = tokenizer.nextToken();

        if (!method.equals("GET")) {
            System.out.println("[WARN] Unsupported HTTP method: " + method);
            clientSocket.close();
            return;
        }

        CacheElement cachedElement = find(url);
        if (cachedElement != null) {
            System.out.println("[CACHE HIT] URL: " + url);
            clientOutput.write(cachedElement.data.getBytes());
        } else {
            System.out.println("[CACHE MISS] URL: " + url);
            forwardRequest(clientSocket, url, version);
        }

        clientSocket.close();
    }

    private static void handleHttpsTunnel(Socket clientSocket, BufferedReader reader, String requestLine) throws IOException {
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

    private static void forwardRequest(Socket clientSocket, String url, String version) throws IOException {
        String host = parseHost(url);
        String path = parsePath(url);

        System.out.println("[FORWARD] Connecting to host: " + host);

        try (Socket remoteSocket = new Socket(host, 80);
             OutputStream remoteOutput = remoteSocket.getOutputStream();
             InputStream remoteInput = remoteSocket.getInputStream()) {

            String formattedRequest = "GET " + path + " " + version + "\r\n" +
                    "Host: " + host + "\r\n" +
                    "Connection: close\r\n\r\n";

            remoteOutput.write(formattedRequest.getBytes());
            remoteOutput.flush();

            byte[] buffer = new byte[MAX_BYTES];
            int bytesRead;
            ByteArrayOutputStream responseBuffer = new ByteArrayOutputStream();

            while ((bytesRead = remoteInput.read(buffer)) != -1) {
                clientSocket.getOutputStream().write(buffer, 0, bytesRead);
                responseBuffer.write(buffer, 0, bytesRead);
            }

            addCacheElement(responseBuffer.toString(), url);
            System.out.println("[FORWARD] Response cached for URL: " + url);
        }
    }

    private static String parseHost(String url) {
        String temp = url.replace("http://", "").replace("https://", "");
        int slashIndex = temp.indexOf('/');
        return (slashIndex != -1) ? temp.substring(0, slashIndex) : temp;
    }

    private static String parsePath(String url) {
        int slashIndex = url.indexOf('/', url.indexOf("//") + 2);
        return (slashIndex != -1) ? url.substring(slashIndex) : "/";
    }

    private static CacheElement find(String url) {
        synchronized (cacheLock) {
            CacheElement current = head;
            while (current != null) {
                if (current.url.equals(url)) {
                    current.lruTimeTrack = System.currentTimeMillis();
                    return current;
                }
                current = current.next;
            }
            return null;
        }
    }

    private static void addCacheElement(String data, String url) {
        synchronized (cacheLock) {
            int elementSize = data.length() + url.length() + 1;
            if (elementSize > MAX_ELEMENT_SIZE) {
                return;
            }
            while (cacheSize + elementSize > MAX_SIZE) {
                removeCacheElement();
            }
            CacheElement newElement = new CacheElement();
            newElement.data = data;
            newElement.len = data.length();
            newElement.url = url;
            newElement.lruTimeTrack = System.currentTimeMillis();
            newElement.next = head;
            head = newElement;
            cacheSize += elementSize;
        }
    }

    private static void removeCacheElement() {
        synchronized (cacheLock) {
            if (head == null) return;
            CacheElement prev = null, temp = head, oldest = head;
            while (temp.next != null) {
                if (temp.next.lruTimeTrack < oldest.lruTimeTrack) {
                    oldest = temp.next;
                    prev = temp;
                }
                temp = temp.next;
            }
            if (prev != null) {
                prev.next = oldest.next;
            } else {
                head = head.next;
            }
            cacheSize -= (oldest.len + oldest.url.length() + 1);
        }
    }
}