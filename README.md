## To use

Clone, build and install [Om](https://github.com/swannodette/om).

```
git clone git@github.com:swannodette/om.git
cd om
git checkout <version-of-om-used-in-project.clj> # e.g. 0.1.3
lein install
```

Compile Clojure and ClojureScript and run the server.
```
lein ring server-headless
```

Visit [localhost:3000/index.html](http://localhost:3000/index.html).
