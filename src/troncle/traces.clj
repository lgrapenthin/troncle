(ns troncle.traces
  (:require [troncle.util :as u]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tracing state

(def trace-cond
  "Predicate used for checking when to report a trace"
  (atom (constantly true)))

(def trace-hook
  "Function which is run on every trace call"
  (atom (constantly nil)))

(def trace-log
  "List of trace reports"
  (agent []))

(defn empty-trace-log []
  (send trace-log (constantly [])))

(def trace-execution-function
  "Function which is run when the trace is requested"
  (atom (constantly nil)))

(defn st
  "Set the function which is executed when troncle-trace-region is
  called."
  [fn]
  (swap! trace-execution-function (constantly fn)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tracing macros

(defn tracer
  "Instrument a form with tracing code, tracking its source code
  position."
  [line-offset form form-transformed]
  (let [[line column] ((juxt :line :column) (meta form))
        line (+ line line-offset)]
    `(let [v# ~form-transformed
           ;; Need to build the string up and print it out atomically,
           ;; or display will be out of order.
           output# (format "L:%d C:%d %s\n=> %s" ~line ~column '~form
                           (pr-str v#))]
       (println output#) v#)))



