#pragma once

#include "../include/ConnectionHandler.h"

// TODO: implement the STOMP protocol
class StompProtocol
{
private:
std::string& username;
std::mutex mtx;
int subscriptionIdCounter;
std::map<std::string, int> gameToSubId;
std::map<std::string, std::map<std::string, std::vector<event>>> gameUpdates_;


public:
	StompProtocol(std::string& username, ConnectionHandler& connectionHandler, bool& shouldTerminate);
	void processMessage(std::string message);
	void sendLogin();
	void sendLogout();
	void sendSubscribe(std::string gameName);
	void sendUnsubscribe(std::string gameName);
	void sendSend(std::string destination, std::string message);

};
