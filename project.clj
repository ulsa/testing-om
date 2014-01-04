(defproject testing-om "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"

  :jvm-opts ^:replace ["-Xmx1g" "-server"]

  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-2138"]
                 [org.clojure/core.async "0.1.267.0-0d7780-alpha"]
                 [om "0.1.3"]
                 [cljs-ajax "0.2.3"]]

  :plugins [[lein-cljsbuild "1.0.1"]]

  :source-paths ["src"]

  :cljsbuild {
    :builds [{:id "dev"
              :source-paths ["src"]
              :compiler {
                :output-to "app.js"
                :output-dir "out"
                :optimizations :none
                         :source-map true}}
             {:id "release"
              :source-paths ["src"]
              :compiler {
                         :output-to "app.js"
                         :optimizations :advanced
                         :pretty-print false
                         :output-wrapper false
                         :preamble ["react/react.min.js"]
                         :externs ["react/externs/react.js"]
                         :closure-warnings
                         {:non-standard-jsdoc :off}}}]})
