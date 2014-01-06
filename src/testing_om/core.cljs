(ns testing-om.core
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs.core.async :as async :refer [<! put! chan alts! timeout mult tap]]
            [ajax.core :refer [GET POST]])
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]]
   [testing-om.core :refer [log]]))

(def app-state (atom {:comments []}))

(def converter (js/Showdown.converter.))

(defn markdown-span [text]
  (let [html (.makeHtml converter (or text ""))]
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
    (go-loop []
             (let [{:keys [success error] :as chs} (get-json "/comments")
                   [result ch] (alts! (vals chs))]
               (if (= ch success)
                 (put! out result)
                 (log (str "Comment updates polling failed: " (get-in result [:response :error])))))
             (<! (timeout 5000))
             (recur))
    out))

(defn update-comments [app]
  (go-loop []
           (let [updated (<! comment-updates)]
             (om/transact! app :comments (constantly updated)))
           (recur)))

(def comment-posts (chan))
(def comment-posts-mult (mult comment-posts))

(def invalid-comment-posts (chan))
(def invalid-comment-posts-mult (mult invalid-comment-posts))

(defn handle-form-submit [e app owner]
  (let [author-field (om/get-node owner "author")
        text-field (om/get-node owner "text")
        author (trim-field author-field)
        text (trim-field text-field)
        comment {:author author :text text}]
    (set! (.-value author-field) "")
    (set! (.-value text-field) "")
    (put! comment-posts {:comment comment :form owner})
  false))

(defn post-comments-to-server []
  (let [unvalidated (chan)]
    (tap comment-posts-mult unvalidated)
    (go-loop []
             (let [{:keys [comment form]} (<! unvalidated)
                   {:keys [success error] :as chs} (post-json "/comments" comment)
                   [result ch] (alts! (vals chs))]
               ;; Post to channel depending on success/failure.
               (if (= ch success)
                 (put! comment-updates result)
                 (put! invalid-comment-posts {:comment comment
                                              :form form
                                              :error (get-in result [:response :error])})))
             (recur))))

(defn add-comments-optimistically [app]
  (let [unvalidated (chan)]
    (tap comment-posts-mult unvalidated)
    (go-loop []
             (let [comment (<! unvalidated)]
               (om/transact! app :comments conj comment))
             (recur))))

(defn except-element [el]
  (fn [coll] (into [] (filter #(not (= el %)) coll))))

(defn remove-invalid-comments [app owner]
  (let [invalid (chan) ]
    (tap invalid-comment-posts-mult invalid)
    (go-loop []
             (let [{:keys [comment form]} (<! invalid)
                   author-field (om/get-node form "author")
                   text-field (om/get-node form "text")]
               (om/transact! app :comments (except-element comment))
               (set! (.-value author-field) (:author comment))
               (set! (.-value text-field) (:text comment)))
             (recur))))

(defn alert-invalid-comments []
  (let [invalid (chan) ]
    (tap invalid-comment-posts-mult invalid)
    (go-loop []
             (let [{error :error} (<! invalid)]
               (js/alert error))
             (recur))))

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

(defn comment-box [app owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (update-comments app)
      (post-comments-to-server)
      (add-comments-optimistically app)
      (remove-invalid-comments app owner)
      (alert-invalid-comments))
    om/IRender
    (render [_]
      (dom/div #js {:className "commentBox"}
               (dom/h1 nil "Comments")
               (om/build comment-list app)
               (om/build comment-form app)))))

(om/root app-state comment-box (.getElementById js/document "content"))

