(ns cap-and-send.core
  (:require [clojure.java.shell :refer [sh]]
            [clojure.core.async
             :as a
             :refer [>! <! >!! <!! go go-loop chan buffer close! thread
                     alts! alts!! timeout]]
            [me.raynes.fs :as fs]
            [clojure.string :as str]))

(defn- move-file [src-file dest-dir] ""
  (fs/copy+ src-file dest-dir))

(defn- delete-file [file] ""
  (fs/delete file))

(defn- do-cap [cap-time-secs fps frame-dir] ""
  (println "INFO doing capture with total time: " cap-time-secs " fps: " fps " into frame dir: " frame-dir)
  (let [in ["streamer" "-o"
            (str frame-dir "/000000.jpeg")
            "-s" "1920x1080" "-j" "100" "-t"
            (str (* (read-string fps) (read-string cap-time-secs)))
            "-r"
            fps]]
    (try
      (println "INFO cap sh input: " in)
      (apply sh in)
      (catch Throwable t
        (println "ERROR cap error: " t)))))

(defn- get-clip-number [frame-file] ""
  (-> (.getName frame-file)
      (str/split #"\.")
      (first)
      (Integer/parseInt)))

(defn- do-clip [fps frame-dir clip-dir clip-path] ""

  (let [the-frames (fs/list-dir frame-dir)
        the-frames-ordered (sort-by #(get-clip-number %) the-frames)
        with-abs (map-indexed (fn [idx itm] (hash-map :idx idx
                                                      :file itm
                                                      :new-file (str clip-dir "/" (format "%06d" idx) ".jpeg"))) the-frames-ordered)]
    (do
      (println "INFO making clip from frame count: " (count the-frames))
      (doseq [f with-abs]
        (move-file (:file f) (:new-file f)))
      (let [the-new-frames (fs/list-dir clip-dir)
            in ["ffmpeg" "-r"
                fps
                "-f" "image2" "-s" "1920x1080" "-i"
                (str clip-dir "/%6d.jpeg")
                "-vcodec" "libx264" "-crf" "25" "-pix_fmt" "yuv420p"
                clip-path]]
        (try
          (println "INFO clip input: " in)
          (apply sh in)
          (catch Throwable t
            (println "ERROR clip error: " t)))
        (doseq [f the-new-frames]
          (delete-file f))
        (doseq [f the-frames]
          (delete-file f))))))

(defn- clear-dir [dir]
  (fs/delete-dir dir)
  (fs/mkdir dir))

(def uploaded-clips (atom []))

(def s3-upload-chan (chan))

(defn s3-upload [filename bucket key] ""
  (println "INFO uploading to s3: " filename)
  ;TODO
  (swap! uploaded-clips conj filename))

(go-loop []
  (let [data (<! s3-upload-chan)
        {file :file
         bucket :bucket
         key :key} data]
    (s3-upload file bucket key))
  (recur))

(def cap-chan (chan))

(go-loop []
  (let [data (<! cap-chan)
        {time-secs :time-secs
         fps       :fps
         frame-dir :frame-dir} data]
    (do-cap time-secs fps frame-dir))
  (recur))

(def clip-chan (chan))

(go-loop []
  (let [data (<! clip-chan)
        {fps       :fps
         frame-dir :frame-dir
         clip-dir  :clip-dir
         clipname  :clipname} data]
    (do-clip fps frame-dir clip-dir clipname))
  (recur))

(defn -main
  "I don't do a whole lot ... yet."
  [cap-time-secs clip-interval-ms fps frame-dir clip-dir s3-upload-dir s3-bucket s3-key]
  (do (clear-dir frame-dir)
      (clear-dir clip-dir)
      (clear-dir s3-upload-dir)

      (>!! cap-chan {:time-secs cap-time-secs
                     :fps       fps
                     :frame-dir frame-dir})
      (let [clips-iterations (int
                               (/
                                 (read-string cap-time-secs)
                                 (/
                                   (read-string clip-interval-ms)
                                   1000)))]
        (dotimes [i clips-iterations]
          (do
            (Thread/sleep (read-string clip-interval-ms))
            (let [clipname (str s3-upload-dir "/" i ".mp4")]
              (>!! clip-chan {:fps       fps
                              :frame-dir frame-dir
                              :clip-dir  clip-dir
                              :clipname  clipname})
              (>!! s3-upload-chan {:file clipname
                                   :bucket s3-bucket
                                   :key s3-key}))
            (println "INFO currently uploaded/ing clips: " @uploaded-clips))))))

;TODO use `motion` to capture only motion frames
