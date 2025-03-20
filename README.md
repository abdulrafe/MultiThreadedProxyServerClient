# Multi-Threaded Proxy Server (Java)

This project is a **multi-threaded HTTP/HTTPS proxy server** written in Java with two versions:

- `ProxyServerWithCache.java`: Implements a proxy with **LRU-based caching**.
- `ProxyServerWithoutCache.java`: Basic proxy **without caching**.

It supports:
- Forwarding HTTP requests.
- Tunneling HTTPS requests via **CONNECT**.
- Limiting concurrent clients using a **semaphore**.
- Logging of client activity and forwarding behavior.

---

## 📦 **Project Structure**

```
/ProjectRoot
├── Makefile
├── ProxyParse.java
├── ProxyServerWithCache.java
├── ProxyServerWithoutCache.java
├── .gitignore
└── README.md
```

---

## 🛠 **How to Build and Run**

### ✅ **Step 1: Build**

```bash
make all
```

### ✅ **Step 2: Run with Cache**

```bash
make run-with-cache
```

### ✅ **Step 3: Run without Cache**

```bash
make run-without-cache
```

By default, both servers will listen on `localhost:8080`.

---

## 🌐 **Testing via curl**

### For HTTP:
```bash
curl -v -x http://localhost:8080 http://www.example.com
```

### For HTTPS:
```bash
curl -v -x http://localhost:8080 https://www.asu.edu
```

---

## 🌍 **Testing via Browser**

1. Configure your browser to use **HTTP Proxy**:
   - **Host:** `localhost`
   - **Port:** `8080`

2. Visit any HTTP or HTTPS URL like:
   - `http://www.asu.edu/`

> ⚠️ Note: HTTPS works because this proxy now supports CONNECT tunneling.

---

## 🔄 **Features**

- ✅ Multi-threaded using `ThreadPool` or native threads.
- ✅ Semaphore-based client limiting.
- ✅ Supports **HTTP GET** requests.
- ✅ Supports **HTTPS CONNECT** method.
- ✅ Caching (only in `ProxyServerWithCache`) with LRU eviction.
- ✅ Logging for cache hits/misses and forwarded requests.

---

## 🚀 **Makefile Commands**

| Command              | Description                           |
|----------------------|---------------------------------------|
| `make all`           | Compiles both proxy servers           |
| `make run-with-cache`| Runs proxy with cache                 |
| `make run-without-cache` | Runs proxy without cache        |
| `make clean`         | Deletes `.class` files                |

---

## ⚙️ **Dependencies**

- Java 17+ (tested)
- No external libraries required

---

> **Enjoy!** Contributions and suggestions are welcome 🚀

