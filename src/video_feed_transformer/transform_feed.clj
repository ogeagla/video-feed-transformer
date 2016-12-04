(ns video-feed-transformer.transform_feed
  (:require [clojure.java.shell :refer [sh]]
            [clojure.core.async
             :as a
             :refer [>! <! >!! <!! go go-loop chan buffer close! thread
                     alts! alts!! timeout put!]]
            [me.raynes.fs :as fs]
            [clojure.string :as str]
            [mikera.image.core :as imgz]))

(defn- load-image [path]
  "loads an image from path"
  (imgz/load-image path))

(defn- get-image-data [img]
  "get pixels from image"
  (imgz/get-pixels img))

(defn get-grid-boxes [width height rows cols]
  "for a given width, height, num rows and num cols,
  return a map of [ {:x1 0 :x2 10 :y1 0 :y2 10} ...]"
  (let [
        col-w (int (/ width cols))
        row-h (int (/ height rows))
        rows-range (range rows)
        cols-range (range cols)]
    (into [] (for [r rows-range
                   c cols-range]
               (let [r-next (+ 1 r)
                     c-next (+ 1 c)]
                 {
                  ;:row r
                  ;:col c
                  :x1 (* col-w c)
                  :x2 (* col-w c-next)
                  :y1 (* row-h r)
                  :y2 (* row-h r-next)}
                 )))))

(defn- get-rect-from-img [img rect]
  "given an img and rect coords, return subimg pixels")

(defn- get-matching-img-to-corpus [img corpus]
  "given an image and a collection of imgs, return best match from corpus")
(defn- assemble-imgs-onto-canvas [canvas imgs-and-rects]
  "given a set of images, their rect coordinates, and a canvas, assemble the imgs
  using the grid coords onto the canvas")

(defn- get-argb-avg-of-img [img]
  "")

(defn- find-closest-match-by-argb-l2 [img-argb corpus-argb]
  "")

(defn- move-file [src-file dest-dir] ""
  (fs/copy+ src-file dest-dir))

(defn- delete-file [file] ""
  (fs/delete file))
(defn- get-clip-number [frame-file] ""
  (-> (.getName frame-file)
      (str/split #"\.")
      (first)
      (Integer/parseInt)))

(def feed-chan (chan))

(defn- do-feed-from-frames [fps frame-dir clip-intermediate-dir clipno s3-bucket feed-dir] ""
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
                (str feed-dir "/" clipno ".mp4")]]
        (try
          (println "INFO clip input: " in)
          (apply sh in)
          (catch Throwable t
            (println "ERROR clip error: " (:cause (Throwable->map t)))))
        (doseq [f the-new-frames]
          (delete-file f))
        (doseq [f the-frames]
          (delete-file f))
        [{:file   clipno
          :bucket s3-bucket}]))))


(defn- do-feed [fps frame-dir clip-intermediate-dir clipno s3-bucket s3-dir s3-upload-chan
                motion-dir use-motion upload-to-s3 motion-summary-dir feed-dir]
  ;(println "INFO do-feed: " fps frame-dir clip-intermediate-dir clip-path
  ; s3-bucket s3-dir s3-upload-chan motion-dir use-motion)
  (let [clips-data (if use-motion
                     (println "ERROR no support for transforming motion clips as feeds")
                     (do-feed-from-frames fps frame-dir clip-intermediate-dir clipno s3-bucket feed-dir))]
    (if upload-to-s3
      (doseq [datum clips-data]
        (println "INFO putting to s3: " datum)
        (put! s3-upload-chan datum))
      (println "INFO written to fs: " (vec clips-data)))))

(go-loop []
  (let [data (<! feed-chan)
        {fps                :fps
         frame-dir          :frame-dir
         clip-dir           :clip-dir
         motion-dir         :motion-dir
         clipno             :clipno
         s3-bucket          :s3-bucket
         s3-upload-chan     :s3-upload-chan
         use-motion         :use-motion
         s3-dir             :s3-dir
         upload-to-s3       :upload-to-s3
         motion-summary-dir :motion-summary-dir
         feed-dir           :feed-dir} data]
    (do-feed fps frame-dir clip-dir clipno s3-bucket s3-dir s3-upload-chan
             motion-dir use-motion upload-to-s3 motion-summary-dir feed-dir))
  (recur))

