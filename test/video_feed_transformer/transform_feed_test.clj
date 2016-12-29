(ns video-feed-transformer.transform-feed-test
  (:require [video-feed-transformer.transform_feed :as tf]
            [clojure.test :refer :all]))

(defn contains-many? [m & ks]
  (every? #(contains? m %) ks))

(deftest grid-gen-test
  (testing "A rectangle can be split up into a grid with variable cols and rows"
    (let [rows 2
          cols 2
          width 100
          height 200
          grid (tf/get-grid-boxes width height rows cols)]
      (is (= [{:x1 0    :x2 50    :y1 0     :y2 100     :row 0 :col 0}
              {:x1 50   :x2 100   :y1 0     :y2 100     :row 0 :col 1}
              {:x1 0    :x2 50    :y1 100   :y2 200     :row 1 :col 0}
              {:x1 50   :x2 100   :y1 100   :y2 200     :row 1 :col 1}]
             grid)))))
