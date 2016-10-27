(ns cap-and-send.core
  (:require [clojure.java.shell :refer [sh]]
            [clojure.core.async
             :as a
             :refer [>! <! >!! <!! go go-loop chan buffer close! thread
                     alts! alts!! timeout]]
            [me.raynes.fs :as fs]))

(defn- do-cap [cap-frame-cnt fps frame-dir] ""
  (println "doing capture with total frames: " cap-frame-cnt
           " fps: " fps
           " into frame dir: " frame-dir)
  (let [in ["streamer"
            "-o"
            (str frame-dir "/000000.jpeg")
            "-s"
            "1920x1080"
            "-j"
            "100"
            "-t"
            cap-frame-cnt
            "-r"
            fps]]
    (try
      (println "in: " in)
      (apply sh in)
      (catch Throwable t
        (println "error: " (.getMessage t))))))

(defn- do-clip [fps frame-dir clip-path] ""
  (let [out (:out (sh "ffmpeg"
                      "-r"
                      fps
                      "-f"
                      "image2"
                      "-s"
                      "1920x1080"
                      "-i"
                      (str frame-dir "/\\%06d")
                      "-vcodec"
                      "libx264"
                      "-crf"
                      "25"
                      "-pix_fmt"
                      "yuv420p"
                      clip-path))]
    out))

(defn- clear-dir [dir]
  (fs/delete-dir dir)
  (fs/mkdir dir))

(defn -main
  "I don't do a whole lot ... yet."
  [cap-frame-cnt clip-interval fps frame-dir clip-dir intermediate-dir]
  (do (clear-dir frame-dir)
      (clear-dir clip-dir)
      (clear-dir intermediate-dir)

      (future (do-cap cap-frame-cnt fps frame-dir))
      (dotimes [i 10]
        (do
          (Thread/sleep (read-string clip-interval))
          (println "make clip")
          ))
      ))
