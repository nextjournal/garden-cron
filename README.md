# nextjournal.garden-cron

The purpose of `garden-cron` is to run a function repeatedly on a
schedule, specified in a syntax akin to [crontab](https://crontab.guru/).

The preferred way to use `garden cron` is to use the function `defcron`
to schedule a function periodically:

```clojure
(defn rooster [_time]
  (println "Cock-a-doodle-doo!"))

(defcron #'rooster {:hour [6] :weekday (range 1 6)})
```

This will make the rooster wake you at 6am in the morning, but only on weekdays.

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
a different time zone or delay scheduling until the software is started.


## Additional features

The function `next-cron` computes the next trigger moment, given a
cron schedule and a time.  It will always be at least 1 second after
the given time.

The function `cron-seq` computes an infinite list of when a cron
schedule triggers, suitable for `chime-at`.

The function `cron-merge` merges multiple, potentially infinite, lists
of instants in chronological order.  This can be used
if you need more flexibility than a single cron schedule provides.


## Dependencies

`garden-cron` uses [`chime`](https://github.com/jarohen/chime)
to do the actual execution.
