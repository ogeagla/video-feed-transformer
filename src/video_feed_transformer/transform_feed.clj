(ns video-feed-transformer.transform_feed
  (:require [clojure.java.shell :refer [sh]]
            [clojure.core.async
             :as a
             :refer [>! <! >!! <!! go go-loop chan buffer close! thread
                     alts! alts!! timeout put!]]
            [me.raynes.fs :as fs]
            [clojure.string :as str]
            [mikera.image.core :as imgz]))

(defn load-image [path]
  "loads an image from path"
  (imgz/load-image path))

(defn- get-image-data [img]
  "get pixels from image"
  (imgz/get-pixels img))

(defn get-grid-boxes [width height rows cols]
  "for a given width, height, num rows and num cols,
  return a map of [ {:x1 0 :x2 10 :y1 0 :y2 10} ...]"
  (let [
        col-w      (int (/ width cols))
        row-h      (int (/ height rows))
        rows-range (range rows)
        cols-range (range cols)]
    (into [] (for [r rows-range
                   c cols-range]
               (let [r-next (+ 1 r)
                     c-next (+ 1 c)]
                 {:row r
                  :col c
                  :x1  (* col-w c)
                  :x2  (* col-w c-next)
                  :y1  (* row-h r)
                  :y2  (* row-h r-next)})))))

(defn- get-rect-from-img [img rect]
  "given an img and rect coords, return subimg"
  (let [{:keys [x1 y1 x2 y2]} rect]
    (imgz/sub-image img x1 y1 (- x2 x1) (- y2 y1))))

(defn color-int-to-rgb [color-int]
  {:r (bit-and (bit-shift-right color-int 16) 0xff)
   :g (bit-and (bit-shift-right color-int 8) 0xff)
   :b (bit-and color-int 0xff)})

(defn average [coll]
  (/ (reduce + coll) (count coll)))

(defn get-rgb-avg-of-img [img]
  (let [pixels     (imgz/get-pixels img)
        pixels-rgb (map color-int-to-rgb pixels)
        reds       (map :r pixels-rgb)
        blues      (map :b pixels-rgb)
        greens     (map :g pixels-rgb)
        r-avg      (average reds)
        b-avg      (average blues)
        g-avg      (average greens)]
    {:r-avg r-avg
     :g-avg g-avg
     :b-avg b-avg}))

(defn rgb-dist [{:keys [r1 r2 g1 g2 b1 b2]}]
  (Math/sqrt (+
               (Math/pow (- r2 r1) 2)
               (Math/pow (- g2 g1) 2)
               (Math/pow (- b2 b1) 2))))

(defn match-by-rgb-avg [target coll]
  (let [with-dist (map #(assoc %
                          :dist (rgb-dist {:r1 (:r-avg target)
                                           :g1 (:g-avg target)
                                           :b1 (:b-avg target)
                                           :r2 (:r-avg (:rgb-avg %))
                                           :g2 (:g-avg (:rgb-avg %))
                                           :b2 (:b-avg (:rgb-avg %))}))
                       coll)
        sorted    (sort-by :dist with-dist)]
    {:target  target
     :matched (first sorted)}))

(defn to-composable [item]
  (let [winner-img (:image (:matched (:match item)))
        width      (- (:x2 item) (:x1 item))
        height     (- (:y2 item) (:y1 item))
        scaled-img (imgz/resize winner-img width height)
        x          (:x1 item)
        y          (:y1 item)]
    {:img scaled-img
     :x   x
     :y   y}))

(defn overlay-many [background-img foreground-imgs-and-coordinates]
  (let [combo (imgz/copy background-img)
        g     (.getGraphics combo)]
    (doseq [{:keys [img x y]} foreground-imgs-and-coordinates]
      (.drawImage g img x y nil))
    (.dispose g)
    combo))

(defn overlay [background-img foreground-img foreground-x foreground-y]
  (let [combo (imgz/copy background-img)]
    (-> combo
        (.getGraphics)
        (.drawImage foreground-img foreground-x foreground-y nil)
        (.dispose))
    combo))

(defn blank-canvas [w h]
  (imgz/new-image w h))

(defn build-mosaic [target-img img-coll rows cols]
  (let [

        target-w-rects   (get-grid-boxes
                           (.getWidth target-img)
                           (.getHeight target-img)
                           rows
                           cols)

        mosaic-coll      (map #(hash-map
                                 :rgb-avg (get-rgb-avg-of-img %)
                                 :image %)
                              img-coll)

        target-w-matches (->> target-w-rects
                              (map (fn [target-w-rect]
                                     (let [subimg (get-rect-from-img target-img target-w-rect)
                                           rgbavg (get-rgb-avg-of-img subimg)]
                                       (assoc target-w-rect
                                         :subimage subimg
                                         :rgb-avg rgbavg
                                         :match (match-by-rgb-avg rgbavg mosaic-coll))))))

        matched-imgs     (map
                           #(assoc % :match
                                     (match-by-rgb-avg
                                       (:rgb-avg %)
                                       mosaic-coll))
                           target-w-matches)

        composable-imgs  (map to-composable matched-imgs)

        target-w         (.getWidth target-img)
        target-h         (.getHeight target-img)]
    (-> (blank-canvas target-w target-h)
        (overlay-many composable-imgs)
        (imgz/save "test-final-img.png"))))

(defn- get-clip-number [frame-file] ""
  (-> (.getName frame-file)
      (str/split #"\.")
      (first)
      (Integer/parseInt)))

(def feed-chan (chan))

(defn- do-feed-from-frames [frame-dir s3-bucket] ""
  (let [the-frames (fs/list-dir frame-dir)]))

(defn- do-feed [fps frame-dir clip-intermediate-dir clipno s3-bucket s3-dir s3-upload-chan
                motion-dir use-motion upload-to-s3 motion-summary-dir feed-dir]
  ;(println "INFO do-feed: " fps frame-dir clip-intermediate-dir clip-path
  ; s3-bucket s3-dir s3-upload-chan motion-dir use-motion)
  (let [clips-data (if use-motion
                     (println "ERROR no support for transforming motion clips as feeds")
                     (do-feed-from-frames frame-dir s3-bucket))]
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

