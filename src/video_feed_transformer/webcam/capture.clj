(ns video-feed-transformer.webcam.capture
  (:require [clojure.java.shell :refer [sh]]
            [clojure.core.async
             :as a
             :refer [>! <! >!! <!! go go-loop chan buffer close! thread
                     alts! alts!! timeout put!]]))

(def cap-chan (chan))


(defn- do-cap [cap-time-secs fps frame-dir] ""
  (println "INFO doing capture with total time: " cap-time-secs " fps: " fps " into frame dir: " frame-dir)
  (let [in ["streamer" "-o"
            (str frame-dir "/000000.jpeg")
            "-s" "1920x1080" "-j" "100" "-t"
            (str (* fps cap-time-secs))
            "-r"
            (str fps)]]
    (try
      (println "INFO cap sh input: " in)
      (apply sh in)
      (catch Throwable t
        (println "ERROR cap error: " (:cause (Throwable->map t)))))))

(go-loop []
  (let [data (<! cap-chan)
        {time-secs :time-secs
         fps       :fps
         frame-dir :frame-dir} data]
    (do-cap time-secs fps frame-dir))
  (recur))
