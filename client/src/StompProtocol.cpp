#include "../include/StompProtocol.h"
#include <sstream>
#include <vector>
#include <iostream>

using Command = StompFrame::Command;
using std::vector;
using std::stringstream;

StompProtocol::StompProtocol(string& username_, ConnectionHandler& connectionHandler) : username(username_), handler(connectionHandler){

}

string StompProtocol::processKeyboardMessage(string message) {
    // TODO: Implement logic
    vector<string> args = split(message, ' ');

    string commandName = args[0];

    if (commandName == "login") {
        string hostPort = args[1];
        string user = args[2];
        string pass = args[3];

        size_t position = hostPort.find(':');
        string host = hostPort.substr(0, position);
        string portStr = hostPort.substr(position + 1);

        this->username = args[2];

        return sendLogin(host, portStr, username, pass);
    } 
    
    else if(commandName == "join")
        return sendSubscribe(args[1]);

    else if(commandName == "exit")
        return sendUnsubscribe(args[1]);

    else if(commandName == "report")
        return;

    else if(commandName == "summary")
        return;

    else if(commandName == "logout")
        return sendLogout();
}

string StompProtocol::processSocketMessage(string message) {
    // TODO: Implement logic
    
}

string StompProtocol::sendLogin(string host, string port, string username, string password) {
    // TODO: Implement logic
    StompFrame frame;
    frame.command = Command::CONNECT;
    frame.headers["accept-version"] = "1.2";
    frame.headers["host"] = " stomp.cs.bgu.ac.il";
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
    receiptIdCounter++;
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
}

string StompProtocol::handleReceipt() {
    // TODO: Implement logic
}

std::string StompProtocol::frameToString(const StompFrame& frame) {
    std::stringstream ss;
    
    ss << commandToString(frame.command) << "\n"; 

    for (const auto& pair : frame.headers) {
        ss << pair.first << ":" << pair.second << "\n";
    }
    
    ss << "\n"; 
    
    ss << frame.body;

    return ss.str();
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

std::string StompProtocol::commandToString(StompFrame::Command cmd) {
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