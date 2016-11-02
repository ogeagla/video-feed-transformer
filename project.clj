(defproject cap-and-send "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [me.raynes/fs "1.4.6"]
                 [org.clojure/core.async "0.2.391"]
                 [org.clojure/tools.cli "0.3.5"]]
  :main ^:skip-aot cap-and-send.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
