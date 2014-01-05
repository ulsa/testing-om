(ns testing-om.core)

(defmacro log [& args]
  `(.log js/console ~@args))
