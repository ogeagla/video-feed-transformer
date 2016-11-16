(ns cap-and-send.webcam.motion
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
  ())

(defn- do-motion-conf [config-dest motion-dir device] ""
  (copy-conf-to-new config-dest)
  (alter-conf config-dest motion-dir device))

(defn- do-motion [motion-dir device] ""
  (println "INFO running motion with output dir: " motion-dir " and device: " device)
  ;TODO actually use motion-dir and device
  (let [in ["motion" "-n" "-c" "resources/motion.conf"]]
    (try
      (println "INFO motion sh input: " in)
      (apply sh in)
      (catch Throwable t
        (println "ERROR motion error: " (:cause (Throwable->map t)))))))

(go-loop []
  (let [data (<! motion-chan)
        {motion-dir :motion-dir
         device     :device} data]
    (do-motion motion-dir device))
  (recur))