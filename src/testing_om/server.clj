(ns testing-om.server
  (:require [compojure.core :refer :all]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [ring.middleware.json :as middleware]
            [ring.util.response :as response]
            [cheshire.core :as json]))

(defn json-response [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "application/json"}
   :body (json/generate-string data)})

(def comments (atom '()))

(defn all-comments [] (json-response @comments))

(defn contains-non-blank? [m keys]
  (not-any? #(clojure.string/blank? (% m)) keys))

(defn add-comment [comment]
  (if (contains-non-blank? comment [:author :text])
    (do 
      (swap! comments conj (select-keys comment [:author :text]))
      (json-response @comments))
    (json-response {:error "Author and text must not be blank!"} 400)))

(defroutes app-routes
  (GET "/" [] (response/redirect "/index.html"))
  (GET "/comments" [] (all-comments))
  (POST "/comments" {body :body} (add-comment body))
  (route/resources "/")
  (route/not-found "Not found"))

(def handler
  (-> (handler/api app-routes)
      (middleware/wrap-json-body {:keywords? true})))

