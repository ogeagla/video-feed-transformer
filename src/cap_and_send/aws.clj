(ns cap-and-send.aws
  (:require [amazonica.aws.s3 :as s3]))

(defn upload-to-s3 [bucket key file metadata]
  (s3/put-object :bucket-name bucket
                 :key key
                 :file file
                 :metadata {:user-metadata  metadata}))

