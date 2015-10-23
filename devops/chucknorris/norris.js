var command = process.argv[2];
var channel = process.argv[3];
var tsung_url = process.argv[4];
var chuck_norris_fact = process.argv[5];
var irc = require('irc');
var config = require('./config');
var async = require('async');

//Command-Line Example:
//sudo node norris.js start ids http://mytsungserver/tsung/20140515-2112/report.html "Chuck Norris knows that last number of pi"

// process.argv.forEach(function (val, index, array) {
//   console.log(index + ': ' + val);
// });

var client = new irc.Client(config.host, config.nick, {
        channels: config.channels,
        port: config.port,
        debug: true,
    	showErrors: true,
        userName: config.userName,
        realName: config.realName,
        password: config.password,
        autoConnect: false
});

channel = "#" + channel;
console.log(command, channel, tsung_url, chuck_norris_fact);

//this does not work/post anything ???
client.connect(function() { 
    async.forEach(client.opt.channels, function(channel) {
    	console.log(channel);
        client.say(channel, 'Hello world');   
    });
});

if (command == 'start') {
	console.log("Starting...");
   	client.connect(function(channel, tsung_url, chuck_norris_fact) {
   	   	console.log('Connected!');
   	   	client.say(channel, '**** Tsung Test has Started ****');
		client.say(channel, tsung_url);
    	client.say(channel, chuck_norris_fact);
   	});
    client.disconnect();
}

if (command == 'stop') {
	console.log("Stopping...");
	client.connect(function(channel, tsung_url) {
    	console.log('Connected!');
       	client.say(channel, 'Tsung Test has Ended');
        client.say(channel, tsung_url);
    });
	client.disconnect();
}




