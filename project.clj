(defproject video-feed-transformer "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [me.raynes/fs "1.4.6"]
                 [org.clojure/core.async "0.2.391"]
                 [org.clojure/tools.cli "0.3.5"]
                 [amazonica "0.3.74"]]
  :plugins [[lein-kibit "0.1.2"]
            [jonase/eastwood "0.2.3"]]
  :main ^:skip-aot video-feed-transformer.core
  :profiles {:uberjar {:aot :all}})
