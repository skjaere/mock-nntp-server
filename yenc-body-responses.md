# endpoint for adding yenc encoded body responses

We need to add an endpoint to app that accepts an articleId as a string, and some binary data encoded as base64.
This binary data should be yenc encoded using the library located at /home/william/IdeaProjects/yenc_kotlin_wrapper 
which has a function that returns yenc encoded binary data with headers.  The client in the testcontainer package
should be updated to support this new endpoint, and take care of the base64 encoding. Please add test coverage, and 
update the readme when done.

