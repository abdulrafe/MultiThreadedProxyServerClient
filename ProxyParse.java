import java.util.*;
import java.util.regex.*;

public class ProxyParse {

    public static final boolean DEBUG = true;

    public static class ParsedRequest {
        public String method;
        public String protocol;
        public String host;
        public String port;
        public String path;
        public String version;
        public String buf;
        public List<ParsedHeader> headers;

        public ParsedRequest() {
            this.headers = new ArrayList<>();
        }

        public static ParsedRequest create() {
            return new ParsedRequest();
        }

        public static ParsedRequest parse(String requestBuffer) {
            ParsedRequest request = new ParsedRequest();
            Scanner scanner = new Scanner(requestBuffer);

            if (!scanner.hasNext()) return null;

            // Parse Request Line
            request.method = scanner.next();
            String fullAddress = scanner.next();
            request.version = scanner.next();

            if (!request.method.equals("GET")) {
                debug("Only GET method is supported!");
                return null;
            }

            // Extract protocol, host, and path
            Pattern pattern = Pattern.compile("(http[s]?)://([^/:]+)(:\\d+)?(/.*)?");
            Matcher matcher = pattern.matcher(fullAddress);

            if (!matcher.find()) {
                debug("Invalid request format");
                return null;
            }

            request.protocol = matcher.group(1);
            request.host = matcher.group(2);
            request.port = matcher.group(3) != null ? matcher.group(3).substring(1) : "80";
            request.path = matcher.group(4) != null ? matcher.group(4) : "/";

            // Parse headers
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (line.trim().isEmpty()) break;

                String[] parts = line.split(":", 2);
                if (parts.length == 2) {
                    request.headers.add(new ParsedHeader(parts[0].trim(), parts[1].trim()));
                }
            }

            return request;
        }

        public void destroy() {
            this.headers.clear();
        }

        public String unparse() {
            StringBuilder sb = new StringBuilder();
            sb.append(method).append(" ").append(protocol).append("://").append(host);
            if (!port.equals("80")) {
                sb.append(":").append(port);
            }
            sb.append(path).append(" ").append(version).append("\r\n");

            for (ParsedHeader header : headers) {
                sb.append(header.key).append(": ").append(header.value).append("\r\n");
            }

            sb.append("\r\n");
            return sb.toString();
        }

        public String unparseHeaders() {
            StringBuilder sb = new StringBuilder();
            for (ParsedHeader header : headers) {
                sb.append(header.key).append(": ").append(header.value).append("\r\n");
            }
            sb.append("\r\n");
            return sb.toString();
        }

        public int totalLen() {
            return unparse().length();
        }

        public int headersLen() {
            return unparseHeaders().length();
        }
    }

    public static class ParsedHeader {
        public String key;
        public String value;

        public ParsedHeader(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }

    public static void debug(String format, Object... args) {
        if (DEBUG) {
            System.err.printf(format + "%n", args);
        }
    }

    public static void main(String[] args) {
        String testRequest =
                "GET http://www.google.com:80/index.html HTTP/1.1\r\n" +
                        "Host: www.google.com\r\n" +
                        "Connection: keep-alive\r\n\r\n";

        ParsedRequest request = ParsedRequest.parse(testRequest);
        if (request != null) {
            System.out.println("Parsed Request:");
            System.out.println(request.unparse());
        } else {
            System.out.println("Parsing failed.");
        }
    }
}