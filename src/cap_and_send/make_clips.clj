(ns cap-and-send.make-clips
  (:require [clojure.java.shell :refer [sh]]
            [clojure.core.async
             :as a
             :refer [>! <! >!! <!! go go-loop chan buffer close! thread
                     alts! alts!! timeout put!]]
            [me.raynes.fs :as fs]
            [clojure.string :as str]))

(defn- move-file [src-file dest-dir] ""
  (fs/copy+ src-file dest-dir))

(defn- delete-file [file] ""
  (fs/delete file))
(defn- get-clip-number [frame-file] ""
  (-> (.getName frame-file)
      (str/split #"\.")
      (first)
      (Integer/parseInt)))

(def clip-chan (chan))

(defn- file-coll-has-completed-video [fcoll] ""
  (let [contains-completeness-symbol (map #(.contains (.getName %) "motion-pic") fcoll)]
    (some true? contains-completeness-symbol)))

(defn- get-videos-from-file-coll [fcoll] ""
  (filter #(.contains (.getName %) ".avi") fcoll))

(defn- get-pics-from-file-coll [fcoll] ""
  (filter #(.contains (.getName %) ".jpg") fcoll))

(defn- get-motion-clips [motion-dir] ""
  (let [the-files (fs/list-dir motion-dir)
        grouped-by-event (partition-by #(Integer/parseInt (first (clojure.string/split (.getName %) #"-"))) the-files)
        completed  (filter file-coll-has-completed-video grouped-by-event)
        split (map #(hash-map :videos (get-videos-from-file-coll %)
                              :pics   (get-pics-from-file-coll %))
                   completed)]
    split))

(defn- do-motion-clips [motion-dir s3-dir s3-bucket motion-summary-dir]
  (let [clips (get-motion-clips motion-dir)]
    (doseq [{:keys [videos pics]} clips]
      (println "INFO Found vids: " videos)
      (println "INFO Found pics: " pics)
      (doseq [pic pics]
        (let [new-pic-fname (str motion-summary-dir "/" (.getName pic))]
          (move-file pic new-pic-fname)
          (delete-file pic)))
      (doseq [vid videos]
        (let [new-vid-fname (str s3-dir "/" (.getName vid))]
          (move-file vid new-vid-fname)
          (delete-file vid))))
    (let [stuff (flatten (map :videos clips))
        stuff-2 (map #(hash-map :file (str s3-dir "/" (.getName %))
                                :bucket s3-bucket) stuff)        ]
      stuff-2)))

(defn- do-clip-from-frames [fps frame-dir clip-intermediate-dir clip-path s3-bucket] ""
  (let [the-frames (fs/list-dir frame-dir)
        the-frames-ordered (sort-by #(get-clip-number %) the-frames)
        with-abs (map-indexed (fn [idx itm]
                                (hash-map :idx idx
                                          :file itm
                                          :new-file (str clip-intermediate-dir "/" (format "%06d" idx) ".jpeg")))
                              the-frames-ordered)]
    (do
      (println "INFO making clip from frame count: " (count the-frames))
      (doseq [f with-abs]
        (move-file (:file f) (:new-file f)))
      (let [the-new-frames (fs/list-dir clip-intermediate-dir)
            in ["ffmpeg" "-r"
                (str fps)
                "-f" "image2" "-s" "1920x1080" "-i"
                (str clip-intermediate-dir "/%6d.jpeg")
                "-vcodec" "libx264" "-crf" "25" "-pix_fmt" "yuv420p"
                clip-path]]
        (try
          (println "INFO clip input: " in)
          (apply sh in)
          (catch Throwable t
            (println "ERROR clip error: " (:cause (Throwable->map t)))))
        (doseq [f the-new-frames]
          (delete-file f))
        (doseq [f the-frames]
          (delete-file f))
        [{:file   clip-path
          :bucket s3-bucket}]))))


(defn- do-clip [fps frame-dir clip-intermediate-dir clip-path s3-bucket s3-dir s3-upload-chan motion-dir use-motion upload-to-s3 motion-summary-dir]
  ;(println "INFO do-clip: " fps frame-dir clip-intermediate-dir clip-path s3-bucket s3-dir s3-upload-chan motion-dir use-motion)
  (let [clips-data (if use-motion
                     (do-motion-clips motion-dir s3-dir s3-bucket motion-summary-dir)
                     (do-clip-from-frames fps frame-dir clip-intermediate-dir clip-path s3-bucket))]
    (if upload-to-s3
      (doseq [datum clips-data]
        (println "INFO putting to s3: " datum)
        (put! s3-upload-chan datum))
      (println "INFO written to fs: " (vec clips-data)))))

(go-loop []
  (let [data (<! clip-chan)
        {fps            :fps
         frame-dir      :frame-dir
         clip-dir       :clip-dir
         motion-dir     :motion-dir
         clipname       :clipname
         s3-bucket      :s3-bucket
         s3-upload-chan :s3-upload-chan
         use-motion     :use-motion
         s3-dir         :s3-dir
         upload-to-s3   :upload-to-s3
         motion-summary-dir :motion-summary-dir} data]
    (do-clip fps frame-dir clip-dir clipname s3-bucket s3-dir s3-upload-chan motion-dir use-motion upload-to-s3 motion-summary-dir))
  (recur))

