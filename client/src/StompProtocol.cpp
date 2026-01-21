#include "../include/StompProtocol.h"
#include <sstream>
#include <vector>
#include <iostream>

using Command = StompFrame::Command;
using std::vector;
using std::stringstream;
using std::cout;
using std::endl;

StompProtocol::StompProtocol(){
    //placeholder
}

vector<string> StompProtocol::processKeyboardMessage(string message) {
    // TODO: Implement logic
    vector<string> args = split(message, ' ');

    string commandName = args[0];
    vector<string> output_frames;
    if (commandName == "login") {
        if (!isLoggedIn){
            string hostPort = args[1];
            string user = args[2];
            string pass = args[3];

            size_t position = hostPort.find(':');
            string host = hostPort.substr(0, position);
            string portStr = hostPort.substr(position + 1);

            this->username = args[2];

            output_frames.push_back(sendLogin(host, portStr, username, pass));
        }
        else{
            output_frames.push_back("");
            cout << "The client is already logged in, log out before trying again";
        }
    } 
    
    else if(commandName == "join")
        output_frames.push_back(sendSubscribe(args[1]));

    else if(commandName == "exit")
        output_frames.push_back(sendUnsubscribe(args[1]));

    else if(commandName == "report")
        output_frames = processReport(args[1]);

    else if(commandName == "summary")
        output_frames.push_back("");

    else if(commandName == "logout")
        output_frames.push_back(sendLogout());

    return output_frames;
}

bool StompProtocol::processSocketMessage(string message) {
    StompFrame frame = stringToFrame(message);
    switch (frame.command) {
        case Command::RECEIPT:
            return handleReceipt(frame);
        case Command::MESSAGE:
            return handleMessage(frame);
        case Command::ERROR:
            return handleError(frame);
        default:
            return false;
    }

    
}

string StompProtocol::sendLogin(string host, string port, string username, string password) {
    // TODO: Implement logic
    StompFrame frame;
    frame.command = Command::CONNECT;
    frame.headers["accept-version"] = "1.2";
    frame.headers["host"] = "stomp.cs.bgu.ac.il";
    frame.headers["login"] = username;
    frame.headers["passcode"] = password;
    frame.body = "";

    return frameToString(frame);
}

string StompProtocol::sendLogout() {
    // TODO: Implement logic
    StompFrame frame;
    frame.command = Command::DISCONNECT;
    frame.headers["receipt"] = receiptIdCounter;
    receipts[receiptIdCounter] = "USER DISCONNECTED";
    receiptIdCounter++;
    loggedIn = false;
    return frameToString(frame);

}

string StompProtocol::sendSubscribe(string gameName) {
    // TODO: Implement logic
    StompFrame frame;
    frame.command = Command::SUBSCRIBE;
    frame.headers["destination"] = "/" + gameName;
    //locking to handle client and socket access to maps.
    { 
        lock_guard<mutex> lock(mtx);
        gameToSubId[gameName] = subscriptionIdCounter;
        frame.headers["id"] = subscriptionIdCounter;
        subscriptionIdCounter++;
        frame.headers["receipt"] = receiptIdCounter;
        receipts[receiptIdCounter] = "Joined channel " + gameName;
        receiptIdCounter++;
    }
    
    frame.body = "";

    return frameToString(frame);

}

string StompProtocol::sendUnsubscribe(string gameName) {
    // TODO: Implement logic
    StompFrame frame;
    frame.command = Command::UNSUBSCRIBE;
    frame.headers["destination"] = "/" + gameName;
    { 
        lock_guard<mutex> lock(mtx);
        frame.headers["id"] = gameToSubId[gameName];
        gameToSubId.erase(gameName);
        frame.headers["receipt"] = receiptIdCounter;
        receipts[receiptIdCounter] = "Exited channel " + gameName;
        receiptIdCounter++;
    }
    
    frame.body = "";

    return frameToString(frame);

}

string StompProtocol::sendSend(string destination, string message) {
    // TODO: Implement logic
    StompFrame frame;
    frame.command = Command::SEND;
    frame.headers["destination"] = "/" + destination;
    frame.body = message;

    return frameToString(frame);

}

bool StompProtocol::handleReceipt(StompFrame& frame) {
    string receiptMessage = frame.headers["receipt"];
    cout << receiptMessage << endl;
    if (receiptMessage.compare("USER DISCONNECTED"))
        setLoggedIn(false);
    return true;
}

bool StompProtocol::handleError(StompFrame& frame) {
    cout << "Error received from server: \"" << frame.headers["message"] << "\"\n";
    if (frame.body != "")
        cout << "error description: " << frame.body << endl;
    loggedIn = false;
    return false;
}

bool StompProtocol::handleMessage(StompFrame& frame) {
    // TODO: Implement logic
    string gameName = frame.headers["destination"].substr(1); //removing the leading '/'
    string reporter = frame.headers["user"];
    string body = frame.body;
    Event event(body);
    insetToGameUpdates(gameName,reporter, event);

}

string StompProtocol::frameToString(const StompFrame& frame) {
    stringstream ss;
    
    ss << commandToString(frame.command) << "\n"; 

    for (const auto& pair : frame.headers) {
        ss << pair.first << ":" << pair.second << "\n";
    }
    
    ss << "\n"; 
    
    ss << frame.body;

    return ss.str();
}

StompFrame StompProtocol::stringToFrame(const string& message){
    StompFrame frame;
    size_t currentChar = 0;
    size_t len = message.length();
    size_t commandEnd = message.find('\n', currentChar);
    frame.command = stringToCommand(message.substr(0, commandEnd));
    currentChar = commandEnd + 1;
    while (currentChar < len && message[currentChar] != '\n') {
        
        size_t endHeaderName = message.find(':', currentChar);
        size_t endHeaderLine = message.find('\n', currentChar);

        // if headers are malformed stop parsing to pervent crash
        if (endHeaderName == string::npos || endHeaderLine == string::npos) {
            break; 
        }

        string key = message.substr(currentChar, endHeaderName - currentChar);       
        string value = message.substr(endHeaderName + 1, endHeaderLine - (endHeaderName + 1));
        frame.headers[key] = value;

        currentChar = endHeaderLine + 1;
    }
    if (currentChar < len && message[currentChar] == '\n'){
        currentChar++;//skipping the empty line between headers and body
    }

    if (currentChar < len) {
        frame.body = message.substr(currentChar);
    }

    return frame;

    
}

StompFrame::Command StompProtocol::stringToCommand(const std::string& cmd) {

    if (cmd == "CONNECTED") return Command::CONNECTED;
    if (cmd == "MESSAGE")   return Command::MESSAGE;
    if (cmd == "RECEIPT")   return Command::RECEIPT;
    if (cmd == "ERROR")     return Command::ERROR;
    if (cmd == "SEND")      return Command::SEND;
    if (cmd == "SUBSCRIBE") return Command::SUBSCRIBE;
    if (cmd == "UNSUBSCRIBE") return Command::UNSUBSCRIBE;
    if (cmd == "DISCONNECT") return Command::DISCONNECT;
    if (cmd == "CONNECT")   return Command::CONNECT;
    return Command::UNKNOWN;
}

string StompProtocol::commandToString(StompFrame::Command cmd) {
    switch (cmd) {
        case Command::CONNECT:     return "CONNECT";
        case Command::CONNECTED:   return "CONNECTED";
        case Command::SEND:        return "SEND";
        case Command::SUBSCRIBE:   return "SUBSCRIBE";
        case Command::UNSUBSCRIBE: return "UNSUBSCRIBE";
        case Command::MESSAGE:     return "MESSAGE";
        case Command::RECEIPT:     return "RECEIPT";
        case Command::ERROR:       return "ERROR";
        case Command::DISCONNECT:  return "DISCONNECT";
        default:                   return "UNKNOWN";
    }
}

vector<string> StompProtocol::split(const string &s, char delimiter) {
    vector<string> tokens;
    string token;
    stringstream tokenStream(s);
    while (std::getline(tokenStream, token, delimiter)) {
        tokens.push_back(token);
    }
    return tokens;
}

vector<string> StompProtocol::processReport(string path){
    names_and_events events = parseEventsFile(path);
    vector<Event> newEvents = events.events;
    string gameName = events.team_a_name + "_" + events.team_b_name;
    string destinantion = "/" + gameName;
    vector<string> sends;
    for(Event e : newEvents){
        insetToGameUpdates(gameName, username, e);
        sends.push_back(sendSend(destinantion, eventBodyConstructor(e)));
    }
}

bool StompProtocol::insetToGameUpdates(string gameName, string username, Event e){
    {
        lock_guard<mutex> lock(mtx);
        e.set_beforeHalftime(beforeHalftimeFlags[gameName]);
        gameUpdates[gameName][username].push_back(e);
        std::sort(gameUpdates[gameName][username].begin(), gameUpdates[gameName][username].end());
        if(e.get_name() == "halftime")
            beforeHalftimeFlags[gameName] = false;
    }
}

string StompProtocol::eventBodyConstructor(Event event){
    string body = "";

    // adding body headers
    body += "user: " + username + "\n";
    body += "team a: " + event.get_team_a_name() + "\n";
    body += "team b: " + event.get_team_b_name() + "\n";
    body += "event name: " + event.get_name() + "\n";
    
    // adding time
    body += "time: " + std::to_string(event.get_time()) + "\n";

    // adding general game updates, going over the pairs [updateName][value]
    body += "general game updates:\n";
    for (const auto& pair : event.get_game_updates()) {
        body += "\t" + pair.first + ": " + pair.second + "\n";
    }

    // adding team A updates, going over the pairs [updateName][value]
    body += "team a updates:\n";
    for (const auto& pair : event.get_team_a_updates()) {
        body += "\t" + pair.first + ": " + pair.second + "\n";
    }

    // adding team B updates, going over the pairs [updateName][value]
    body += "team b updates:\n";
    for (const auto& pair : event.get_team_b_updates()) {
        body += "\t" + pair.first + ": " + pair.second + "\n";
    }

    // adding "discription" XD
    body += "description:\n";
    body += event.get_discription(); 
    
    return body;
}

void StompProtocol::summary(string gameName, string userToSummerize, string path){

}