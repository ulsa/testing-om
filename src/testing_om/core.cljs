(ns testing-om.core
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs.core.async :as async :refer [<! put! chan alts! timeout]]
            [ajax.core :refer [GET POST]])
  (:require-macros
   [cljs.core.async.macros :refer [go]]))

(defn log [s]
  (.log js/console (str s)))

(def app-state (atom {:comments []}))

(def converter (js/Showdown.converter.))

(defn markdown-span [text]
  (let [html (.makeHtml converter text)]
    (dom/span #js {:dangerouslySetInnerHTML #js {:__html html}})))

(defn comment [{:keys [author text]}]
  (om/component
   (dom/div #js {:className "comment"}
            (dom/h2 #js {:className "commentAuthor"}
                    author)
            (markdown-span text))))

(defn comment-list [app]
  (let [comments (om/build-all comment (:comments app))]
    (om/component
     (dom/div #js {:className "commentList"} 
              (if (empty? comments) 
                (markdown-span "*No comments yet.*")
                comments)))))

(defn trim-field [f]
  (.. f -value trim))

(defn make-handler [ch] (fn [r] (put! ch r)))

(defn get-json [uri]
  (let [success (chan)
        error (chan)]
    (GET uri
               {:handler (make-handler success)
                :error-handler (make-handler error)
                :response-format :json
                :keywords? true
                :timeout 10})
    {:success success :error error}))

(defn post-json [uri data]
  (let [success (chan)
        error (chan)]
    (POST uri
               {:handler (make-handler success)
                :error-handler (make-handler error)
                :params data
                :format :json
                :response-format :json
                :keywords? true
                :timeout 10})
    {:success success :error error}))

(def comment-updates
  (let [out (chan)]
    (go (while true
          (<! (timeout 5000))
          (let [{:keys [success error] :as chs} (get-json "/comments.json")
                [result ch] (alts! (vals chs))]
            (if (= ch success)
              (put! out result)
              (log (str "Comment updates polling failed: " (get-in result [:response :error])))))))
    out))

(defn update-comments [app]
  (go (while true
        (let [updated (<! comment-updates)] 
          (om/transact! app :comments (constantly updated))))))

(defn handle-form-submit [e app owner]
  (let [author-field (om/get-node owner "author")
        text-field (om/get-node owner "text")
        author (trim-field author-field)
        text (trim-field text-field)
        comment {:author author :text text}]
    (om/transact! app :comments conj comment)
    (let [{:keys [success error] :as chs} (post-json "/comments.json" comment)]
      (go (let [[result ch] (alts! (vals chs))] 
            (if (= ch success)
              (do 
                (om/transact! app :comments (fn [_] result))
                (set! (.-value author-field) "")
                (set! (.-value text-field) ""))  
              (do 
                (om/transact! app :comments pop)    
                (set! (.-value author-field) author)
                (set! (.-value text-field) text)   
                (log (get-in result [:response :error]))))))))
  false)

(defn comment-form [app owner]
  (om/component
   (dom/form #js {:onSubmit #(handle-form-submit % app owner)}
    (dom/input #js {:type "text"
                    :placeholder "Your name:"
                    :ref "author"})
    (dom/input #js {:type "text"
                    :placeholder "Say something..."
                    :ref "text"})
    (dom/input #js {:type "submit"
                    :value "Post"}))))

(defn comment-box [app]
  (om/component
    (update-comments app)
    (dom/div #js {:className "commentBox"}
             (dom/h1 nil "Comments")
             (om/build comment-list app)
             (om/build comment-form app))))

(om/root app-state comment-box (.getElementById js/document "content"))
