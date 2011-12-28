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