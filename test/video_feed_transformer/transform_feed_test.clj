(ns video-feed-transformer.transform-feed-test
  (:require [video-feed-transformer.transform_feed :as tf]
            [clojure.test :refer :all]
            [mikera.image.core :as imgz]))

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

(deftest get-rgb-avg-test
  (testing "Computes rgb averages of an image"
    (let [img (imgz/load-image "resources/below-average-photography/0006-2015-07-1405-29-39-IMG_20150714_052937_marked.jpg")
          avg (tf/get-rgb-avg-of-img img)]
      (is (=  {:r-avg 439801339/7577600, :g-avg 39520433/757760, :b-avg 76113101/1515520} avg)))))