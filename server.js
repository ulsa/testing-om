var fs = require('fs');

var express = require('express');
var app = express();

var comments = [{author: 'Pete Hunt', text: 'Hey there!'}];

app.use('/', express.static(__dirname));
app.use(express.bodyParser());

app.get('/comments.json', function(req, res) {
  res.setHeader('Content-Type', 'application/json');
  res.send(JSON.stringify(comments));
});

app.post('/comments.json', function(req, res) {
  var comment = req.body;
  if (!comment.author) {
    res.send(400, { error: 'Author must not be empty!' });
  }
  else if (!comment.text) {
    res.send(400, { error: 'Comment text must not be empty!' });
  }
  else {
    comments.push(req.body);
    res.setHeader('Content-Type', 'application/json');
    res.send(JSON.stringify(comments));
  }
});

app.listen(3000);
