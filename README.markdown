Aloha is a webserver, implemented using [Netty](http://netty.io) and Clojure.

```
$ git clone http://github.com/ztellman/aloha.git
...
$ lein run &
Server listening on port 8080.
$ curl localhost:8080
Aloha!
```

It is a simple but complete implementation of the [Ring](https://github.com/mmcgrana/ring) spec, mostly intended as a reference implementation for "Hello World" benchmarking.  If you think you can improve the performance, please send a pull request.

### Benchmarking Aloha on OS X ###

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









