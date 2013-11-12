(ns troncle.emacs
  (:require [clojure.tools.trace :as t]
            [troncle.core :as c]
            [troncle.traces :as traces]
            [nrepl.discover :as d]
            [clojure.tools.nrepl.misc :as m]
            :reload))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Interface with emacs

(defmacro discover-ify
  "Add the nrepl-discover metadata to the var fn XXX incomplete"
  [fn args]
  (let [m (meta fn)]))

(defn safe-read [s]
  (binding [*read-eval* nil]
    (read-string s)))

(def a (atom nil))

(defn trace-region
  "Eval source, taken from source-region instrumenting all forms
  contained in trace-region with tracing"
  [{:keys [transport source source-region trace-region ns] :as msg}
   ]
  (user/starbreak)
  (swap! a (constantly msg))
  (let [source-region (safe-read source-region)
        trace-region (safe-read trace-region)
        soffset (nth source-region 1)
        [tstart tend] (map #(- (nth trace-region %) soffset) [1 2])
        tracer #(list 'clojure.tools.trace/trace
                      (pr-str ((juxt :line :column) (meta %1)) %1) %2)
        ns (-> ns symbol the-ns)]
    (swap! a (constantly [source tstart tend ns tracer]))
    (try (c/trace-marked-forms source tstart tend ns tracer)
         (@traces/trace-execution-function)
         (catch Throwable e
           (clojure.repl/pst e)))))

(let [m (meta #'trace-region)
      opmap {:nrepl/op {:name (-> m :name str) :doc (m :doc)
                        :args [["source"        "string"]
                               ["source-region" "region"]
                               ["trace-region" "region"]]}}]
  (alter-meta! #'trace-region conj opmap))