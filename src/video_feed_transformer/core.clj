(ns video-feed-transformer.core
  (:gen-class)
  (:require [clojure.java.shell :refer [sh]]
            [clojure.core.async
             :as a
             :refer [>! <! >!! <!! go go-loop chan buffer close! thread
                     alts! alts!! timeout put!]]
            [me.raynes.fs :as fs]
            [clojure.string :as str]
            [clojure.tools.cli :as cli]
            [video-feed-transformer.aws :refer [upload-to-s3]]
            [video-feed-transformer.webcam.motion :refer [motion-chan]]
            [video-feed-transformer.webcam.capture :refer [cap-chan]]
            [video-feed-transformer.make-clips :refer [clip-chan]]))

(def uploaded-clips (atom []))

(defn- clear-dir [dir]
  (fs/delete-dir dir)
  (fs/mkdir dir))

(def s3-upload-chan (chan))

(defn s3-upload [filename bucket key] ""
  (println "INFO uploading to s3: " filename)
  (try (upload-to-s3 bucket key filename {:foo "bar"})
       (catch Throwable t
         (println "ERROR uploading to s3: " (:cause (Throwable->map t)))))
  (swap! uploaded-clips conj filename))

(go-loop []
  (let [data (<! s3-upload-chan)
        {file   :file
         bucket :bucket
         key    :key} data]
    (s3-upload file bucket key))
  (recur))

(def cli-opts [[nil "--upload-to-s3" "Whether or not to upload to s3"]
               [nil "--detect-motion-mode" "EXP Only make clips for motion-detected frames"]
               [nil "--capture-time-secs CTS" "Capture time seconds"
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
               [nil "--motion-summary-dir MD" "Motion summary dir"
                :default "motion-summary-dir"]
               [nil "--s3-bucket S3B" "S3 bucket"
                :default "fake-s3-bucket"]
               [nil "--s3-key S3K" "S3 key"
                :default "fake-s3-key"]
               [nil "--device Device" "Video Capture Device"
                :default "/dev/video0"]])

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
                s3-key
                detect-motion-mode
                upload-to-s3
                motion-summary-dir
                device]} (:options opts)]

    (println "INFO cap time secs: " capture-time-secs
             "\nINFO clip interval ms: " clip-interval-ms
             "\nINFO fps: " fps
             "\nINFO frame dir: " frame-dir
             "\nINFO clip dir: " clip-dir
             "\nINFO s3 upload dir: " s3-upload-dir
             "\nINFO motion dir: " motion-dir
             "\nINFO s3 bucket: " s3-bucket
             "\nINFO s3 key: " s3-key
             "\nINFO motion capture: " detect-motion-mode
             "\nINFO upload to s3: " upload-to-s3
             "\nINFO motion summary dir: " motion-summary-dir
             "\nINFO motion capture device: " device)

    (do (clear-dir frame-dir)
        (clear-dir clip-dir)
        (clear-dir s3-upload-dir)
        (clear-dir motion-dir)
        (clear-dir motion-summary-dir)

        (if detect-motion-mode
          ;TODO make these motion params take...
          (put! motion-chan {:motion-dir motion-dir
                             :device     device})
          (put! cap-chan {:time-secs capture-time-secs
                          :fps       fps
                          :frame-dir frame-dir}))

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
                (put! clip-chan {:fps                   fps
                                 :frame-dir             frame-dir
                                 :clip-dir              clip-dir
                                 :motion-dir            motion-dir
                                 :clipname              clipname
                                 :s3-bucket             s3-bucket
                                 :s3-upload-chan        s3-upload-chan
                                 :use-motion            detect-motion-mode
                                 :s3-dir                s3-upload-dir
                                 :upload-to-s3          upload-to-s3
                                 :motion-summary-dir    motion-summary-dir}))
              (println "INFO currently uploaded/ing clips: " @uploaded-clips)))))))
