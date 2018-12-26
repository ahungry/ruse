(defproject ruse "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [clj-http "3.9.1"]
                 [cheshire "5.8.1"]
                 [org.clojure/tools.reader "1.3.2"]
                 [com.github.serceman/jnr-fuse "0.5.2.1"]]
  :repositories {"bintray" "https://jcenter.bintray.com"}
  :main ^:skip-aot ruse.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
