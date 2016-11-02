(ns cap-and-send.frames-to-clip
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

(defn- do-clip [fps frame-dir clip-dir clip-path s3-bucket s3-upload-chan motion-dir use-motion] ""
  (let [the-frames (fs/list-dir frame-dir)
        the-frames-ordered (sort-by #(get-clip-number %) the-frames)
        with-abs (map-indexed (fn [idx itm]
                                (hash-map :idx idx
                                          :file itm
                                          :new-file (str clip-dir "/" (format "%06d" idx) ".jpeg")))
                              the-frames-ordered)]
    (do
      (println "INFO making clip from frame count: " (count the-frames))
      (doseq [f with-abs]
        (move-file (:file f) (:new-file f)))
      (let [the-new-frames (fs/list-dir clip-dir)
            in ["ffmpeg" "-r"
                (str fps)
                "-f" "image2" "-s" "1920x1080" "-i"
                (str clip-dir "/%6d.jpeg")
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
        (put! s3-upload-chan {:file   clip-path
                              :bucket s3-bucket})))))

(go-loop []
  (let [data (<! clip-chan)
        {fps            :fps
         frame-dir      :frame-dir
         clip-dir       :clip-dir
         motion-dir     :motion-dir
         clipname       :clipname
         s3-bucket      :s3-bucket
         s3-upload-chan :s3-upload-chan
         use-motion     :use-motion} data]
    (do-clip fps frame-dir clip-dir clipname s3-bucket s3-upload-chan motion-dir use-motion))
  (recur))

