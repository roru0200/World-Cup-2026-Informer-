#include <stdlib.h>
#include "../include/ConnectionHandler.h"
void SocketReader(ConnectionHandler* connectionHandler){
	// Implementation of SocketReader that reads from the socket
	// and processes incoming messages until shouldTerminate is true.

}

int main(int argc, char *argv[]) {
	ConnectionHandler* handler = nullptr;
	StompProtocol* protocol = nullptr;
	bool loggedIn = false;
	while (1) {
		const short bufsize = 1024;
		char buf[bufsize];
		std::cin.getline(buf, bufsize);
		std::string line(buf);
		std::stringstream line_stream(line);
        std::string command;
        line_stream >> command;
		if (command == "login") {
			if (loggedIn) {
				std::cout << "the client is already logged in, log out before trying again" << std::endl;
				continue;
			}
			std::string hostPort;
            line_stream >> hostPort;
            
            size_t colon = hostPort.find(':');

			std::string host = hostPort.substr(0, colon);
			short port = atoi(hostPort.substr(colon + 1).c_str());

            handler = new ConnectionHandler(host, port);
            if (!handler->connect()) {
                std::cout << "Could not connect to server" << std::endl;
				delete handler;
                handler = nullptr;
                continue;
			}
			std::string username;
			std::string password;
			line_stream >> username;
			line_stream >> password;
			protocol = new StompProtocol(username, handler);
			protocol->sendLogin(username, password);
			std::string answer;
			handler->getFrameAscii(answer, '\0')
			protocol.processMessage(answer);


			
		} else if (command == "join") {
			// handle join
		} else if (command == "report") {
			// handle report
		} else if (command == "summery") {
			// handle summery
		} else if (command == "logout") {
			// handle logout
		} else {
			std::cout << "Unknown command" << std::endl;
		}
	}
}