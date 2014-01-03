(ns testing-om.core
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]))

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

(defn handle-form-submit [e app owner]
  (.log js/console "event" e "app" app "owner" owner)
  (let [author-field (om/get-node owner "author")
        text-field (om/get-node owner "text")
        author (trim-field author-field)
        text (trim-field text-field)
        _ (.log js/console "author" author)
        _ (.log js/console "text" text)]
    (om/transact! app :comments conj
                  {:author author
                   :text text})
    (set! (.-value author-field) "")
    (set! (.-value text-field) ""))
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
            (om/build comment-list app {:path []})
            (om/build comment-form app {:path []}))))

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
