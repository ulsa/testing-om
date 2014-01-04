(ns testing-om.core
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs.core.async :as async :refer [<! put! chan]]
            [ajax.core :refer [GET POST]])
  (:require-macros
   [cljs.core.async.macros :refer [go]])
  (:import [goog.net Jsonp]
           [goog Uri]))

(defn log [s]
  (.log js/console (str s)))

(def app-state (atom {:comments
                      [{:author "John Doe" :text "* Some text"}]}))

(def converter (js/Showdown.converter.))

(defn comment [{:keys [author text]}]
  (om/component
   (dom/div #js {:className "comment"}
            (dom/h2 #js {:className "commentAuthor"}
                    author)
            (let [raw-markup (.makeHtml converter text)]
              (dom/span #js {:dangerouslySetInnerHTML #js {:__html raw-markup}})))))

(defn comment-list [app]
  (let [comments (om/build-all comment (:comments app))]
    (om/component
     (dom/div #js {:className "commentList"} comments))))

(defn trim-field [f]
  (.. f -value trim))

(defn post-json [uri data]
  (let [success (chan)
        error (chan)]
    (POST uri
               {:handler (fn [response] (put! success response))
                :error-handler (fn [response] (put! error response))
                :params data
                :format :json
                :response-format :json
                :keywords? true
                :timeout 10})
    {:success success :error error}))

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
              (om/transact! app :comments (fn [_] result))
              (do 
                (om/transact! app :comments pop)    
                (js/alert (str "Failed to post comment: " (get-in result [:response :error])))))))
    (set! (.-value author-field) "")
    (set! (.-value text-field) "")))
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
   (dom/div #js {:className "commentBox"}
            (dom/h1 nil "Comments")
            (om/build comment-list app)
            (om/build comment-form app))))

(om/root app-state comment-box (.getElementById js/document "content"))

(comment
  "
var CommentForm = React.createClass({
  handleSubmit: function() {
    var author = this.refs.author.getDOMNode().value.trim();
    var text = this.refs.text.getDOMNode().value.trim();
    if (!text || !author) {
      return false;
    }
    this.props.onCommentSubmit({author: author, text: text});
    this.refs.author.getDOMNode().value = '';
    this.refs.text.getDOMNode().value = '';
    return false;
  },
  render: function() {
    return (React.DOM.form(
      {className: "commentForm ", onSubmit: this.handleSubmit},
      React.DOM.input({type: "text ", placeholder: "Your name ", ref: "author "}),
      React.DOM.input({type: "text ", placeholder: "Say something... ", ref: "text "}),
      React.DOM.input({type: "submit ", value: "Post "})
    ));
  }
});
// tutorial1.js
var CommentBox = React.createClass({
  getInitialState: function() {
    return {data: []};
  },
  loadCommentsFromServer: function() {
    $.ajax({
      url: 'comments.json',
      dataType: 'json',
      success: function(data) {
        this.setState({data: data});
      }.bind(this),
      error: function(xhr, status, err) {
        console.error("comments.json ", status, err.toString());
      }.bind(this)
    });
  },
  handleCommentSubmit: function(comment) {
    var comments = this.state.data;
    var newComments = comments.concat([comment]);
    this.setState({data: newComments});
    $.ajax({
      url: this.props.url,
      type: 'POST',
      data: comment,
      success: function(data) {
        this.setState({data: data});
      }.bind(this)
    });
  },
  componentWillMount: function() {
    this.loadCommentsFromServer();
    setInterval(this.loadCommentsFromServer, this.props.pollInterval);
  },
  render: function() {
    return React.DOM.div({className: "commentBox "},
        React.DOM.h1("Comments "),
        CommentList({data: this.state.data}),
        CommentForm({onCommentSubmit: this.handleCommentSubmit})
    );
  }
});
React.renderComponent(
  CommentBox({url: "comments.json ", pollInterval: 10000}),
  document.getElementById('content')
);
")
