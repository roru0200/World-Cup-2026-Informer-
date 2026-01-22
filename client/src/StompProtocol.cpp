#include "../include/StompProtocol.h"
#include <sstream>
#include <vector>
#include <iostream>
#include <fstream> //for writing to file

using Command = StompFrame::Command;
using std::vector;
using std::stringstream;
using std::cout;
using std::endl;

StompProtocol::StompProtocol() :
    username(""),
    mtx(),
    loggedIn(false),
    subscriptionIdCounter(0),
    receiptIdCounter(0),
    gameToSubId(),
    gameUpdates(),
    receipts(),
    beforeHalftimeFlags(){

    }

vector<string> StompProtocol::processKeyboardMessage(string message) {
    // TODO: Implement logic
    vector<string> args = split(message, ' ');

    string commandName = args[0];
    vector<string> output_frames;
    if (commandName == "login") {
        if (!isLoggedIn()){
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

    else if(commandName == "summary"){
        summary(args[1], args[2], args[3]);
        output_frames.push_back("");
    }

    else if(commandName == "logout")
        output_frames.push_back(sendLogout());

    return output_frames;
}

bool StompProtocol::processSocketMessage(string message) {
    StompFrame frame = stringToFrame(message);
    cout << message << endl;
    switch (frame.command) {
        case Command::RECEIPT:
            return handleReceipt(frame);
        case Command::MESSAGE:
            return handleMessage(frame);
        case Command::ERROR:
            return handleError(frame);
        case Command::CONNECTED:
            loggedIn = true;
            cout << "User '"<<username <<"' successfully logged in!" << endl;
            return true;
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
    std::lock_guard<std::mutex> lock(mtx);
    frame.headers["receipt"] = std::to_string(receiptIdCounter);
    receipts[receiptIdCounter] = "USER DISCONNECTED";
    receiptIdCounter++;
    cout << "Logging out..." << endl;
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
        frame.headers["id"] = std::to_string(subscriptionIdCounter);
        subscriptionIdCounter++;
        frame.headers["receipt"] = std::to_string(receiptIdCounter);
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
        frame.headers["id"] = std::to_string(gameToSubId[gameName]);
        gameToSubId.erase(gameName);
        frame.headers["receipt"] = std::to_string(receiptIdCounter);
        receipts[receiptIdCounter] = "Exited channel " + gameName;
        receiptIdCounter++;
    }
    
    frame.body = "";

    return frameToString(frame);

}

string StompProtocol::sendSend(map<string, string> headers, string message) {
    // TODO: Implement logic
    StompFrame frame;
    frame.command = Command::SEND;
    frame.headers = headers;
    frame.body = message;

    return frameToString(frame);

}

bool StompProtocol::handleReceipt(StompFrame& frame) {
    std::lock_guard<std::mutex> lock(mtx);
    string receipIdString = frame.headers["receipt-id"];
    int receipt_id = std::stoi(receipIdString);
    cout << receipts[receipt_id] << endl;
    if (receipts[receipt_id] == "USER DISCONNECTED")
        setLoggedIn(false);
    return true;
}

bool StompProtocol::handleError(StompFrame& frame) {
    cout << "Error received from server: \"" << frame.headers["message"] << "\"\n";
    if (frame.body.find_first_not_of('\n') != string::npos)
        cout << "error description: " << frame.body << endl;
    if (loggedIn){
        loggedIn = false;
        cout<<"press enter to continue..." <<endl;
    }
    return false;
}

bool StompProtocol::handleMessage(StompFrame& frame) {
    // extracting body and game name
    string gameName = frame.headers["destination"].substr(1); //removing the leading '/'
    string subId = frame.headers["subscription"];
    cout << "Got message " << gameName << "\n subID: " << subId;
        string body = frame.body;
        // creating Event object to add to game updates
        Event event(body);
        // extracting reporter name from body
        stringstream ss(body);
        string firstLine;
        std::getline(ss, firstLine);
        size_t colonPos = firstLine.find(':');
        string reporter = firstLine.substr(colonPos +1);
        size_t reporter_start = reporter.find_first_not_of(" \t\r\n");
        size_t reporter_end = reporter.find_last_not_of(" \t\r\n");
        reporter = reporter.substr(reporter_start, reporter_end - reporter_start + 1);
        cout << "\nreporter: " << reporter;
        if(reporter != username)
            insetToGameUpdates(gameName,reporter, event);
        return true;
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
    string thisCommand = message.substr(0, commandEnd);
    size_t first = thisCommand.find_first_not_of(" \t\r\n");
    size_t last = thisCommand.find_last_not_of(" \t\r\n");
    thisCommand = thisCommand.substr(first, (last - first + 1));

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
    map<string, string> headers;
    headers["destination"] = "/" + gameName;
    vector<string> sends;
    bool firstSend = true;
    for(Event e : newEvents){
        if(firstSend){
            headers["file-name"] = path;
            sends.push_back(sendSend(headers, eventBodyConstructor(e)));
            headers.erase("file-name");
            firstSend = false;
        }
        else{
            sends.push_back(sendSend(headers, eventBodyConstructor(e)));
        }
        insetToGameUpdates(gameName, username, e);
    }
    return sends;   
}

bool StompProtocol::insetToGameUpdates(string gameName, string reporter, Event e){
    {
        lock_guard<mutex> lock(mtx);
        if (gameUpdates.find(gameName) == gameUpdates.end() || 
            gameUpdates[gameName].find(reporter) == gameUpdates[gameName].end()) {
                if (gameUpdates.find(gameName) == gameUpdates.end())
                    beforeHalftimeFlags[gameName] = true;
                
                size_t delimiter_pos = gameName.find('_');
                string team_a_name = gameName.substr(0, delimiter_pos);
                string team_b_name = gameName.substr(delimiter_pos + 1);
                Game newGame(team_a_name, team_b_name);
                gameUpdates[gameName][reporter] = newGame;
        }
        e.set_beforeHalftime(beforeHalftimeFlags[gameName]);
        gameUpdates[gameName][reporter].addEvent(e);
        if(e.get_name() == "halftime")
            beforeHalftimeFlags[gameName] = false;
    }
    return true;
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
    lock_guard<mutex> lock(mtx); //locking for threading

    // find returns index of the gameName, if it doesnt exist, it equlas to the end index
    if (gameUpdates.find(gameName) == gameUpdates.end() || 
        gameUpdates[gameName].find(userToSummerize) == gameUpdates[gameName].end()) {
        cout << "Error: No updates found for user " << userToSummerize << " in game " << gameName << endl;
        return;
    }

    // getting the user specific game
    const Game& game = gameUpdates[gameName][userToSummerize];

    // if file doesnt exist, creates it or overwritting
    std::ofstream file(path);
    if (!file.is_open()) {
        cout << "Error: Could not create or open file: " << path << endl;
        return;
    }

    // --- writing to file ---

    // Team A vs Team B Main Header
    size_t delimiter_pos = gameName.find('_');
    string team_a_name = gameName.substr(0, delimiter_pos);
    string team_b_name = gameName.substr(delimiter_pos + 1);

    file << team_a_name << " vs " << team_b_name << "\n";
    
    file << "Game stats:\n";

    // General stats
    // going through pairs of the stats map, a map of stirng is already sorted lexicography
    file << "General stats:\n";
    for (const auto& pair : game.getGeneralStats()) {
        file << pair.first << ": " + pair.second << "\n";
    }

    // Team A stats
    file << team_a_name << " stats:\n";
    for (const auto& pair : game.getTeamAStats()) {
        file << pair.first << ": " + pair.second << "\n";
    }

    // Team B stats
    file << team_b_name << " stats:\n";
    for (const auto& pair : game.getTeamBStats()) {
        file << pair.first << ": " + pair.second << "\n";
    }

    //Game event reports
    file << "Game event reports:\n";

    //Getting events sorted by time
    vector<Event> sortedEvents = game.getEvents(); 

    // Adding events to file
    for (const Event& ev : sortedEvents) {
        file << std::to_string(ev.get_time()) << " - " << ev.get_name() << ":\n\n";
        file << ev.get_discription() << "\n\n\n";
    }

    file.close();
    cout << "Summary file created at " << path << endl;
}

void StompProtocol::setLoggedIn(bool logged){
    loggedIn = logged;
}

bool StompProtocol::isLoggedIn(){
    return loggedIn;
}