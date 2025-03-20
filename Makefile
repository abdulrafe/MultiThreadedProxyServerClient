JAVAC = javac
JAVA = java

# Default target compiles both servers
all: ProxyServerWithCache.class ProxyServerWithoutCache.class

# Compile Proxy Server with Cache
ProxyServerWithCache.class: ProxyServerWithCache.java ProxyParse.java
	$(JAVAC) ProxyServerWithCache.java ProxyParse.java

# Compile Proxy Server without Cache
ProxyServerWithoutCache.class: ProxyServerWithoutCache.java ProxyParse.java
	$(JAVAC) ProxyServerWithoutCache.java ProxyParse.java

# Run targets
run-with-cache:
	$(JAVA) ProxyServerWithCache 8080

run-without-cache:
	$(JAVA) ProxyServerWithoutCache 8080

# Clean compiled .class files
clean:
	rm -f *.class
