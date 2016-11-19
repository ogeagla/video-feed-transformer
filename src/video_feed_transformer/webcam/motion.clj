(ns video-feed-transformer.webcam.motion
  (:require [clojure.java.shell :refer [sh]]
            [clojure.core.async
             :as a
             :refer [>! <! >!! <!! go go-loop chan buffer close! thread
                     alts! alts!! timeout put!]]
            [me.raynes.fs :as fs]))

(def motion-chan (chan))

(defn- copy-conf-to-new [newfile]
  ""
  (fs/copy+ "resources/motion.conf" newfile)
  newfile)

(defn- alter-conf [file motion-dir device]
  (try
    (let [out1 (sh "sed" "-i" "-e" (str "s#motion-dir#" motion-dir "#g") file)
          out2 (sh "sed" "-i" "-e" (str "s#/dev/video0#" device "#g") file)]
      (if-not (and (= (:exit out1) 0) (= (:exit out2) 0))
        (println "ERROR sed did not alter motion config file: " out1 "\n" out2)))
    (catch Throwable t
      (println "ERROR could not alter motion config file " t))))

(defn- do-motion-conf [config-dest motion-dir device] ""
  (println "INFO making motion config file: " config-dest
           "\n with params: motion-dir: " motion-dir "\n device: " device)
  (copy-conf-to-new config-dest)
  (alter-conf config-dest motion-dir device)
  config-dest)

(defn- do-motion [motion-dir device] ""
  (println "INFO running motion with output dir: " motion-dir " and device: " device)
  (let [conf-to-use "resources/motion-0.conf"
        in ["motion" "-n" "-c" "resources/motion-0.conf"]]
    (try
      (println "INFO motion sh input: " in)
      (do-motion-conf conf-to-use motion-dir device)
      (apply sh in)
      (catch Throwable t
        (println "ERROR motion error: " (:cause (Throwable->map t)))))))

(go-loop []
  (let [data (<! motion-chan)
        {motion-dir :motion-dir
         device     :device} data]
    (do-motion motion-dir device))
  (recur))