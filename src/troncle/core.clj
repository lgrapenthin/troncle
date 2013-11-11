(ns troncle.core
  (:require [troncle.tools.reader :as r]
            [troncle.traces :as t]
            [troncle.wrap-macro :as wm]
            [troncle.util :as u]
            [clojure.tools.reader.reader-types :as rt]
            [clojure.walk2 :as w]))

(require '[troncle.wrap-macro :as wm] :reload)

(defn parse-tree [s]
  "Return the location-decorated parse tree of s"
  (let [reader (rt/indexing-push-back-reader s)
        ;; EOF sentinel for r/read.  Make sure it's not in the string
        ;; to be parsed.
        eof (->> #(gensym "parser-eof__") repeatedly
                 (remove #(.contains s (str %))) first)]
    (take-while #(not (= eof %)) (repeatedly #(r/read reader nil eof)))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Identification of forms to trace

(defn line-starts [s]
  "Returns the offset in s of the start of each new line"
  (let [m (re-matcher #"\r?\n" s)
        nl #(if (.find m) (.end m))]
    (into [0] (take-while identity (repeatedly nl)))))

(defn offset-from-line-column
  "Get the offset into the string from the line/column position, using
  the positions computed in line-starts"
  [line column linestarts]
  (+ (linestarts (- line 1)) (- column 1)))

(defn in-region? [linestarts start end f]
  "Given linestarts a list of offsets for each new line in the source
  string, a region in the string marked by start and end, and f a
  subform taken from the read of the source string, test whether f's
  metadata lay in the specified region."
  (let [m (or (meta f) (constantly 1)) ; If no metadata assume offset 0
        sl (m :line) sc (m :column) el (m :end-line) ec (m :end-column)]
    (if (not ((set [sl sc el ec]) nil)) ; Meta data is present
      (let [so (offset-from-line-column sl sc linestarts)
            eo (offset-from-line-column el ec linestarts)]
        (<= start so eo end)))))

(defn mark-contained-forms [linestarts start end fs]
  "Given forms fs, a list of the offsets for each new line in
  linestarts, and offsets start and end, decorate each form in fs
  which lie between start end end with ^{:wrap true}"
  (w/postwalk #(if (in-region? linestarts start end %)
                 (vary-meta % conj {::wrap true})
                 %)
              fs))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tracing/wrapping logic

;; wm/wrap-form needs to reference fns as var symbols.
(defonce dummy-ns (create-ns (gensym))) ;; Keep the vars here
(defn assign-var [v]
  "Return a fully-qualified symbol of a var assigned to value v"
  (let [s (gensym)]
    (intern dummy-ns s v)
    (symbol (-> dummy-ns ns-name str) (str s))))

(def never (constantly false))

(defn maybe-wrap [trace-wrap]
  (fn [f ft]
    (if (-> f meta ::wrap) (trace-wrap ft) ft)))

(defn trace-marked-forms [trace-wrap f ns]
  "Evaluate f in the given ns, with any subforms marked with ^{::wrap
  true} wrapped by the trace-wrap fn."
  (let [tw (assign-var (maybe-wrap trace-wrap))]
    (binding [*ns* ns]
      (eval `(wm/wrap-form never identity ~tw ~f)))))

