Aloha is a webserver, implemented using [Netty](http://netty.io) and Clojure, which conforms to the [Ring](https://github.com/mmcgrana/ring) spec.

```clj
(use 'aloha.core)

(start-http-server
  (fn [request]
    {:status 200
	 :headers {:content-type "text/plain"}
	 :body "Aloha!\n"})
  {:port 8080})
```

Aloha is a reference implementation of a Clojure/Netty webserver, or basically [Aleph](https://github.com/ztellman/aleph) without any extraneous fluff.  It exists as a reminder that Aleph could be faster, but also as a very fast, fully functional webserver in its own right.  You can use Aloha in your own project by adding this to your `project.clj`:

```
[aloha "1.0.0"]
```

Since much of Aloha's life will be spent returning a single string over and over again, it's easy to start up a server for benchmarking.

```
$ lein run &
Server listening on port 8080.
$ curl localhost:8080
Aloha!
```

If you have any ideas on how to improve Aloha's performance, please send a pull request.

### Benchmarking ###



```
$  sudo sysctl -w net.inet.tcp.msl=1000
net.inet.tcp.msl: 15000 -> 1000
$  httperf --num-conns=16 --rate=16 --num-calls=100000 --port=8080
...
```

If you don't have `httperf` installed, try `brew install httperf` or `port install httperf`.  If neither of those work, consider installing [Homebrew](http://mxcl.github.com/homebrew/).

If you want to be contrary and use `ab` instead, **be aware that the ApacheBench binary is broken on OS X Lion**.  You can fix it following [these instructions](http://forrst.com/posts/Fixing_ApacheBench_bug_on_Mac_OS_X_Lion-wku).

### Benchmarking Aloha on Linux ###

```
$  echo 1 | sudo tee /proc/sys/net/ipv4/tcp_tw_reuse
1
$  httperf --num-conns=16 --rate=16 --num-calls=100000 --port=8080
...
```

If you don't have `httperf` installed, use your package manager of choice to install it.









