(ns nextjournal.garden-cron
  (:require [chime.core :as chime]
            [malli.core :as m]
            [malli.error :as me])
  (:import (java.time ZonedDateTime)
           (java.time.temporal ChronoUnit)))

(def cron-element-schema
  (m/schema [:or
             [:= :*]
             [:= true]
             [:vector int?]
             [:set int?]
             [:sequential int?]]))

(def cron-schema
  (m/schema [:map {:closed true}
             [:month {:optional true} cron-element-schema]
             [:day {:optional true} cron-element-schema]
             [:weekday {:optional true} cron-element-schema]
             [:hour {:optional true} cron-element-schema]
             [:minute {:optional true} cron-element-schema]
             [:second {:optional true} cron-element-schema]]))

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
   (when-not (m/validate cron-schema cron)
     (throw (ex-info "invalid cron specification"
                     (me/humanize (m/explain cron-schema cron)))))
   (rest (iterate #(next-cron cron %) start))))

(defn cron-merge
  ([] [])
  ([c1] c1)
  ([c1 c2]
   (if (and (seq c1) (seq c2))
     (cond
       (.isBefore (first c1) (first c2))
       (lazy-seq (cons (first c1) (cron-merge (rest c1) c2)))

       (.isAfter (first c1) (first c2))
       (lazy-seq (cons (first c2) (cron-merge c1 (rest c2))))

       ;; coalesce equal times
       :else
       (lazy-seq (cons (first c1) (cron-merge (rest c1) (rest c2)))))

     ;; one of these is empty here
     (concat c1 c2)))
  ([c1 c2 & cs]
   (apply cron-merge (cron-merge c1 c2) cs)))

(defn defcron
  "Schedules a function repeatedly.

  Takes the var containing a function and a cron expression:

  ```
  (defn rooster [_time]
  (println \"Cock-a-doodle-doo!\"))

  (defcron #'rooster {:hour [6] :weekday (range 1 6)})
  ```

  More generally, a cron expression is a map with these keys:

  * `:month`, integers from 1 to 12
  * `:day`, integers from 1 to 31
  * `:weekday`, integers from 1 (= Monday) to 7 (= Sunday), 0 is **not** permitted.
  * `:hour`, integers from 0 to 23
  * `:minute`, integers from 0 to 60
  * `:second`, integers from 0 to 60

  The values of the map can be one of these things:

  * A vector, list or set of numbers, to specify the values to activate on.
  Ranges and steps can be computed using standard Clojure `range`.
  * The value `true` or `:*` to always activate.

  A cron expression triggers when *all* its keys trigger, subject to the
  following defaults:

  * A cron expression triggers on every month, unless specified.
  * A cron expression triggers on every day, unless specified.
  * A cron expression triggers on every weekday, unless specified.
  * A cron expression triggers on hour 0, unless specified.
  * A cron expression triggers on minute 0, unless specified.
  * A cron expression triggers on second 0, unless specified.
  * Additionally, if only minutes resp. only seconds are specified, it
  triggers on any hour resp. any hour and minute, as well.
  In doubt be more explicit.

  A cron expression can be disabled by calling `defcron` without a
  schedule (second argument).  This is primarily useful during development.

  An optional third argument to `defcron` specifies the starting time;
  it defaults to `ZonedDateTime/now`.  This can be used to match against
  a different time zone or delay scheduling until the software is started."
  [func & args]
  (some-> func meta ::chime .close)
  (when args
    (let [ch (chime/chime-at (apply cron-seq args) func)]
      (alter-meta! func #(-> %
                             (assoc ::cron (first args))
                             (assoc ::chime ch)))))
  func)
