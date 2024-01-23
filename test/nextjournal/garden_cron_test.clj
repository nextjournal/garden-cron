(ns nextjournal.garden-cron-test
  (:require [clojure.test :refer :all]
            [nextjournal.garden-cron :as cron])
  (:import (java.time ZonedDateTime ZoneId)))

(defn sunday? [t]
  (= (.getDayOfWeek t)
     java.time.DayOfWeek/SUNDAY))

(defn cron-seq-2000 [cron]
  (take-while #(= 2000 (.getYear %))
              (cron/cron-seq cron (.minusSeconds
                                   (ZonedDateTime/of 2000 1 1 0 0 0 0
                                                     (ZoneId/of "UTC"))
                                   1))))

(deftest cron-seq
  (testing "sanity"
    (is (= 12 (count (cron-seq-2000 {:day [13]}))))
    (is (= 24 (count (cron-seq-2000 {:day [20 21]}))))
    (is (= 7 (count (cron-seq-2000 {:day [31]}))))

    (is (= 24 (count (cron-seq-2000 {:day [1] :month [1] :hour true}))))
    (is (= 60 (count (cron-seq-2000 {:day [1] :month [1] :hour [12] :minute true}))))
    (is (= 3600 (count (cron-seq-2000 {:day [1] :month [1] :hour [12] :minute true :second true}))))

    (is (= 732 (count (cron-seq-2000 {:hour [0 12]})))))
  (let [bloomsday (ZonedDateTime/of 1904 6 16 12 34 0 0 (ZoneId/of "UTC+1"))]
    (testing "next sunday"
      (is (every? sunday? (take 10 (cron/cron-seq {:weekday [7]} bloomsday)))))
    (testing "100 friday 13th later"
      (is (= (.getYear (nth (cron/cron-seq {:day [13] :weekday [5]} bloomsday) 100)) 1963)))))

(deftest cron-merge
  (testing "merge two disjoint lists"
    (is (= (count (cron/cron-merge
                   (take 10 (cron/cron-seq {:minute (range 0 60 5)}))
                   (take 10 (cron/cron-seq {:minute (range 1 60 5)}))))
           20)))

  (testing "two three disjoint lists"
    (is (= (count (cron/cron-merge
                   (take 10 (cron/cron-seq {:minute (range 0 60 5)}))
                   (take 10 (cron/cron-seq {:minute (range 1 60 5)}))
                   (take 10 (cron/cron-seq {:minute (range 2 60 5)}))))
           30)))

  (testing "merge three overlapping lists"
    (is (= (count (cron/cron-merge
                   (take 10 (cron/cron-seq {:minute (range 0 60 5)}))
                   (take 10 (cron/cron-seq {:minute (range 0 60 10)}))
                   (take 10 (cron/cron-seq {:minute (range 0 60 20)}))))
           20))
    (let [events (take 30 (cron/cron-merge
                           (cron/cron-seq {:minute (range 0 60 5)})
                           (cron/cron-seq {:minute (range 0 60 10)})
                           (cron/cron-seq {:minute (range 0 60 20)})
                           (cron/cron-seq {:minute (range 0 60 5)})
                           ))]
      (is (= events (sort events)))
      (is (= (count (set events)) 30)))))

(deftest errors
  (testing "impossible dates"
    (is (thrown? RuntimeException
                 (doall (cron/cron-seq {:month [5] :day [35]})))))
  (testing "invalid format"
    (is (thrown? RuntimeException
                 (doall (cron/cron-seq {:month 5}))))))
