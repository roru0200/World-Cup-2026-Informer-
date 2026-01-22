#pragma once

#include "../include/ConnectionHandler.h"
#include "../include/event.h"
#include <stdlib.h>
#include "Game.h"

using std::string;
using std::map;
using std::mutex;
using std::vector;
using std::lock_guard;

struct StompFrame {

	enum class Command {
            CONNECT,
            CONNECTED,
            SEND,
            SUBSCRIBE,
            UNSUBSCRIBE,
            MESSAGE,
            RECEIPT,
            ERROR,
            DISCONNECT,
            UNKNOWN
        };

    Command command;
    map<string, string> headers;
    string body;

    StompFrame(Command cmd, map<string, string> hdrs, string b)
        : command(cmd), headers(hdrs), body(b) {}
        
    StompFrame() : command(Command::UNKNOWN), headers(), body("") {}
};

// TODO: implement the STOMP protocol
class StompProtocol
{
private:
    string username;

    //logic variables
    mutex mtx;
    bool loggedIn;

    //counters
    int subscriptionIdCounter;
    int receiptIdCounter;

    //data structures
    map<string, int> gameToSubId; //map<GameName, SubscriptionID>
    map<string, map<string, Game>> gameUpdates; //map<GameName, map<UserName, GameObject>
    map<int, string> receipts; //map<ReceiptID, returnMessage>
    map<string, bool> beforeHalftimeFlags ;//map<GameName, beforeHalftimeFlag>
    

    //helper functions
    vector<string> split(const string &s, char delimiter);
    string frameToString(const StompFrame& frame);
    StompFrame stringToFrame(const string& message);
    
    //command converters
    static StompFrame::Command stringToCommand(const string& cmd);
    static string commandToString(StompFrame::Command cmd);

    //frame creators
    string sendLogin(string host, string port, string username, string password);
	string sendLogout();
	string sendSubscribe(string gameName);
	string sendUnsubscribe(string gameName);
	string sendSend(map<string, string> headers, string message);
    vector<string> processReport(string path);

    //frame handlers
	bool handleReceipt(StompFrame& frame);
    bool handleMessage(StompFrame& frame);
    bool handleError(StompFrame& frame);
    bool handleSummery();

    bool insetToGameUpdates(string, string, Event);
    string eventBodyConstructor(Event);
    void summary(string, string, string);
    

public:
	StompProtocol();

    //proccessing functions
	vector<string> processKeyboardMessage(string message);
	bool processSocketMessage(string message);

    //login functions
    void setLoggedIn(bool logged);
    bool isLoggedIn();

};

