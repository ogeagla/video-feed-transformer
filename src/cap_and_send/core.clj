(ns cap-and-send.core
  (:gen-class)
  (:require [clojure.java.shell :refer [sh]]
            [clojure.core.async
             :as a
             :refer [>! <! >!! <!! go go-loop chan buffer close! thread
                     alts! alts!! timeout put!]]
            [me.raynes.fs :as fs]
            [clojure.string :as str]
            [clojure.tools.cli :as cli]))

(def uploaded-clips (atom []))

(def s3-upload-chan (chan))

(def cap-chan (chan))

(def clip-chan (chan))

(def motion-chan (chan))

(defn- clear-dir [dir]
  (fs/delete-dir dir)
  (fs/mkdir dir))

(defn s3-upload [filename bucket key] ""
  (println "INFO uploading to s3: " filename)
  ;TODO
  (swap! uploaded-clips conj filename))

(defn- move-file [src-file dest-dir] ""
  (fs/copy+ src-file dest-dir))

(defn- delete-file [file] ""
  (fs/delete file))

(defn- do-motion [motion-dir device] ""
  (println "INFO running motion with output dir: " motion-dir " and device: " device)
  ;TODO actually use motion-dir and device
  (let [in ["motion" "-c" "resources/motion.conf"]]
    (try
      (println "INFO motion sh input: " in)
      (apply sh in)
      (catch Throwable t
        (println "ERROR motion error: " t)))))

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
        (println "ERROR cap error: " t)))))

(defn- get-clip-number [frame-file] ""
  (-> (.getName frame-file)
      (str/split #"\.")
      (first)
      (Integer/parseInt)))

(defn- do-clip [fps frame-dir clip-dir clip-path] ""
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
            (println "ERROR clip error: " t)))
        (doseq [f the-new-frames]
          (delete-file f))
        (doseq [f the-frames]
          (delete-file f))))))

(go-loop []
  (let [data (<! s3-upload-chan)
        {file :file
         bucket :bucket
         key :key} data]
    (s3-upload file bucket key))
  (recur))

(go-loop []
  (let [data (<! cap-chan)
        {time-secs :time-secs
         fps       :fps
         frame-dir :frame-dir} data]
    (do-cap time-secs fps frame-dir))
  (recur))

(go-loop []
  (let [data (<! motion-chan)
        {motion-dir :motion-dir
         device :device} data]
    (do-motion motion-dir device))
  (recur))

(go-loop []
  (let [data (<! clip-chan)
        {fps       :fps
         frame-dir :frame-dir
         clip-dir  :clip-dir
         clipname  :clipname} data]
    (do-clip fps frame-dir clip-dir clipname))
  (recur))

(def cli-opts [[nil "--capture-time-secs CTS" "Capture time seconds"
                :default 60
                :parse-fn #(Integer/parseInt %)]
               [nil "--clip-interval-ms CIMS" "Clip interval ms"
                :default 15000
                :parse-fn #(Integer/parseInt %)]
               [nil "--fps FPS" "Frames per second"
                :default 10
                :parse-fn #(Integer/parseInt %)]
               [nil "--frame-dir FD" "Frames dir"
                :default "frames-dir"]
               [nil "--clip-dir CD" "Clips dir"
                :default "clips-dir"]
               [nil "--s3-upload-dir S3D" "S3 upload dir"
                :default "s3-dir"]
               [nil "--motion-dir MD" "Motion dir"
                :default "motion-dir"]
               [nil "--s3-bucket S3B" "S3 bucket"
                :default "fake-s3-bucket"]
               [nil "--s3-key S3K" "S3 key"
                :default "fake-s3-key"]])

(defn -main
  ""
  [& args]
  (let [opts (cli/parse-opts args cli-opts)
        {:keys [capture-time-secs
                clip-interval-ms
                fps
                frame-dir
                clip-dir
                s3-upload-dir
                motion-dir
                s3-bucket
                s3-key]} (:options opts)]

    (println "INFO cap time secs: " capture-time-secs
             "\nINFO clip interval ms: " clip-interval-ms
             "\nINFO fps: " fps
             "\nINFO frame dir: " frame-dir
             "\nINFO clip dir: " clip-dir
             "\nINFO s3 upload dir: " s3-upload-dir
             "\nINFO motion dir: " motion-dir
             "\nINFO s3 bucket: " s3-bucket
             "\nINFO s3 key: " s3-key)

    (do (clear-dir frame-dir)
        (clear-dir clip-dir)
        (clear-dir s3-upload-dir)
        (clear-dir motion-dir)

        (put! cap-chan {:time-secs capture-time-secs
                        :fps       fps
                        :frame-dir frame-dir})
        (put! motion-chan {:motion-dir "motion"
                           :device     "/dev/video0"})
        (let [clips-iterations (int
                                 (/
                                   capture-time-secs
                                   (/
                                     clip-interval-ms
                                     1000)))]
          (dotimes [i clips-iterations]
            (do
              (Thread/sleep clip-interval-ms)
              (let [clipname (str s3-upload-dir "/" i ".mp4")]
                (put! clip-chan {:fps       fps
                                 :frame-dir frame-dir
                                 :clip-dir  clip-dir
                                 :clipname  clipname})
                (put! s3-upload-chan {:file   clipname
                                      :bucket s3-bucket
                                      :key    s3-key}))
              (println "INFO currently uploaded/ing clips: " @uploaded-clips)))))))
