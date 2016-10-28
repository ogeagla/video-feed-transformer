(ns cap-and-send.core
  (:require [clojure.java.shell :refer [sh]]
            [clojure.core.async
             :as a
             :refer [>! <! >!! <!! go go-loop chan buffer close! thread
                     alts! alts!! timeout]]
            [me.raynes.fs :as fs]
            [clojure.string :as str]))

(defn- move-file [src-file dest-dir] ""
  (println "copying " src-file " to " dest-dir)
  (fs/copy+ src-file dest-dir))

(defn- delete-file [file] ""
  (fs/delete file))

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

(defn- get-clip-number [frame-file] ""
  (let [name (.getName frame-file)
        stuff (Integer/parseInt (first (str/split name #"\.")))

        ]
    stuff))

(defn- do-clip [fps frame-dir clip-dir clip-path] ""



  (let [
        the-frames (fs/list-dir frame-dir)
        the-frames-ordered (sort-by #(get-clip-number %) the-frames)
        with-abs(map-indexed (fn [idx itm] (hash-map :idx idx
                                                     :file itm
                                                     :new-file (str clip-dir "/" (format "%06d" idx) ".jpeg"))) the-frames-ordered)
        ]
    (do
      (println "the-frames: " (count the-frames))
      (doseq [f with-abs]
          (move-file (:file f) (:new-file f)))
      (println "moved to clip dir")
      (let [the-new-frames (fs/list-dir clip-dir)
            in ["ffmpeg"
                ;"-pattern_type glob"
                "-r"
                fps
                "-f"
                "image2"
                "-s"
                "1920x1080"
                "-i"
                (str clip-dir "/%6d.jpeg")
                "-vcodec"
                "libx264"
                "-crf"
                "25"
                "-pix_fmt"
                "yuv420p"
                clip-path]
            _ (println "in: " in)
            out (apply sh in)]
        (println "did clip: " out)
        (doseq [f the-new-frames]
            (delete-file f))
        (doseq [f the-frames]
            (delete-file f))
        (println "the new frames: " (count the-new-frames))
        out))))

(defn- clear-dir [dir]
  (fs/delete-dir dir)
  (fs/mkdir dir))

(defn -main
  "I don't do a whole lot ... yet."
  [cap-frame-cnt clip-interval fps frame-dir clip-dir intermediate-dir s3-upload-dir]
  (do (clear-dir frame-dir)
      (clear-dir clip-dir)
      (clear-dir intermediate-dir)
      (clear-dir s3-upload-dir)

      (future (do-cap cap-frame-cnt fps frame-dir))
      (dotimes [i 100]
        (do
          (Thread/sleep (read-string clip-interval))
          (do-clip fps frame-dir clip-dir (str s3-upload-dir "/" i ".mp4"))
          ))
      ))
