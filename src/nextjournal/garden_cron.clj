(ns nextjournal.garden-cron
  (:require [chime.core :as chime])
  (:import (java.time ZonedDateTime)
           (java.time.temporal ChronoUnit)))

(defn- ->pred [d]
  (cond
    (or (= :* d) (= true d))
    (constantly true)

    (or (vector? d) (seq? d) (set? d))
    (set d)))

;; algorithm from https://github.com/leahneukirchen/snooze/blob/master/snooze.c
(defn next-cron [cron start]
  (let [month?   (->pred (get cron :month true))
        day?     (->pred (get cron :day true))
        weekday? (->pred (get cron :weekday true))
        hour?    (->pred (get cron :hour (if (or (:second cron) (:minute cron))
                                           true
                                           #{0})))
        minute?  (->pred (get cron :minute (if (:second cron) true #{0})))
        second?  (->pred (get cron :second #{0}))]
    ;; XXX we could check here if any predicate is always false
    (loop [time (-> start
                    (.plusSeconds 1)
                    (.withNano 0))]
      (when (> (.until start time ChronoUnit/DAYS) 1000)
        (throw (ex-info "Cron didn't trigger for 1000 days within start time"
                        {:start start
                         :cron cron})))
      (if-not (month? (.getMonthValue time))
        (recur (-> time
                   (.plusMonths 1)
                   (.withDayOfMonth 1)))
        (if-not (and (day? (.getDayOfMonth time))
                     (weekday? (.getValue (.getDayOfWeek time))))
          (recur (-> time
                     (.plusDays 1)
                     (.withHour 0)
                     (.withMinute 0)
                     (.withSecond 0)))
          (if-not (hour? (.getHour time))
            (recur (-> time
                       (.plusHours 1)
                       (.withMinute 0)
                       (.withSecond 0)))
            (if-not (minute? (.getMinute time))
              (recur (-> time
                         (.plusMinutes 1)
                         (.withSecond 0)))
              (if-not (second? (.getSecond time))
                (recur (-> time
                           (.plusSeconds 1)))
                time))))))))

(defn cron-seq
  ([cron]
   (cron-seq cron (ZonedDateTime/now)))
  ([cron start]
   (rest (iterate #(next-cron cron %) start))))

(defn defcron [func & args]
  (some-> func meta ::chime .close)
  (when args
    (prn (take 10 (apply cron-seq args)))
    (let [ch (chime/chime-at (apply cron-seq args) func)]
      (alter-meta! func #(-> %
                             (assoc ::cron (first args))
                             (assoc ::chime ch)))))
  func)
